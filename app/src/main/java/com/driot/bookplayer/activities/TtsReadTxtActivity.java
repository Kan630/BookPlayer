package com.driot.bookplayer.activities;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.Voice;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Button;
    import android.widget.SeekBar;
import android.widget.TextView;

import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.graphics.Color;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.driot.bookplayer.R;
import com.driot.bookplayer.helpers.EbookTtsHelper;
import com.driot.bookplayer.tts.TxtReader;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;



public class TtsReadTxtActivity extends LoggingActivity implements EbookTtsHelper.Listener {

    private static final String PREF_KEY_LAST_URI = "tts_last_txt_uri";

    private TextView tvUri, tvStatus, tvPreview;
    private SeekBar seekSpeed;
    private Button btnPick, btnStart, btnStop;

    private Uri pickedUri;
    private String loadedText = "";
    private EbookTtsHelper tts;

    private Spinner spinnerLanguage, spinnerVoice;
    private ArrayAdapter<String> languageAdapter, voiceAdapter;
    private List<Locale> availableLanguages = new ArrayList<>();
    private List<Voice> availableVoices = new ArrayList<>();

    private boolean ttsRetryScheduled = false;

    private int resumeOffset = 0;

    private int[] wordStarts;        // start index for each spoken word
    private int totalWords = 0;
    private int currentWordIndex = 0;

    private SpannableStringBuilder previewSpannable;
    private BackgroundColorSpan wordBgSpan = new BackgroundColorSpan(0x55FFFF00); // semi-yellow
    private ForegroundColorSpan wordFgSpan = new ForegroundColorSpan(Color.BLACK);

    private boolean isSpeaking = false;
    private boolean isPaused = false;

    private boolean spinnersInitialized = false;


    // Activity Result API for SAF
    private final ActivityResultLauncher<Intent> openDocLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        try {
                            getContentResolver().takePersistableUriPermission(
                                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                            );
                        } catch (Exception ignored) {}
                        onPickedUri(uri);
                    }
                }
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tts_read_txt);

        tvUri = findViewById(R.id.tvUri);
        tvStatus = findViewById(R.id.tvStatus);
        tvPreview = findViewById(R.id.tvPreview);
        seekSpeed = findViewById(R.id.seekSpeed);
        btnPick = findViewById(R.id.btnPickTxt);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);

        spinnerLanguage = findViewById(R.id.spinnerLanguage);
        spinnerVoice = findViewById(R.id.spinnerVoice);

        languageAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLanguage.setAdapter(languageAdapter);

        voiceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        voiceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerVoice.setAdapter(voiceAdapter);

        tts = new EbookTtsHelper(getApplicationContext(), this);

        btnPick.setOnClickListener(v -> pickTxt());
        btnStart.setOnClickListener(v -> clickRead());
        btnStop.setOnClickListener(v -> togglePauseResume());
        btnStop.setText("Pause");


        seekSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                // Map 50..150 → 0.5x..1.5x (with 100=1.0x)
                float rate = Math.max(0.1f, value / 100f);
                if (tts != null) {
                    // Safe if not ready; will apply once ready
                    try { tts.setSpeechRate(rate); } catch (Throwable ignored) {}
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Restore last picked file if you want quick testing
        String last = getSharedPreferences("tts_demo", MODE_PRIVATE).getString(PREF_KEY_LAST_URI, null);
        if (last != null) {
            try { onPickedUri(Uri.parse(last)); } catch (Exception ignored) {}
        }
    }

    private void pickTxt() {
        myLogI("---- USER CLICKS PICK -----");
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        openDocLauncher.launch(i);
    }


    private void onPickedUri(Uri uri) {
        pickedUri = uri;
        tvUri.setText("Picked: " + uri);
        myLogI("Picked: " + uri);
        getSharedPreferences("tts_demo", MODE_PRIVATE)
                .edit().putString(PREF_KEY_LAST_URI, uri.toString()).apply();
        tellStuff("Loading...");
        TxtReader.loadAsync(this, uri, new TxtReader.Callback() {
            @Override public void onLoaded(String text) {
                runOnUiThread(() -> {
                    buildWordIndex(text);
                    loadedText = text;
                    previewSpannable = new SpannableStringBuilder(text);
                    tvPreview.setText(previewSpannable);
                    tvPreview.setMovementMethod(new ScrollingMovementMethod()); // enable programmatic scroll
                    tellStuff("Loaded " + text.length() + " chars");
                });
            }

            @Override public void onError(Exception e) {
                runOnUiThread(() -> {
                    loadedText = "";
                    tvPreview.setText("");
                    tellStuff("Load error: " + e.getMessage());
                });
            }
        });
    }

    private void clickRead() {
        myLogI("---- USER CLICKS READ ALOUD -----");
        if (!tts.isReady()) { tellStuff("TTS not ready yet…"); return; }
        if (isEmpty(loadedText)) { tellStuff("No text loaded"); return; }
        tts.stop();
        tts.speakFromOffset(loadedText, resumeOffset);
        isPaused = false;
        isSpeaking = true;
        btnStop.setText("Pause");
        updatePlayingStatus();
    }


    @Override protected void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
    }

    // EbookTtsHelper.Listener
    @Override public void onStart(String id) {
        runOnUiThread(() -> {
            isSpeaking = true;
            isPaused = false;
            tellStuff("Speaking…");
        });
    }
    @Override public void onDone(String id)  {
        runOnUiThread(() -> {
            isSpeaking = false;
            // don't change isPaused here (done at end of queue)
            tellStuff("Done");
        });
    }
    @Override public void onError(String id, int code) {
        runOnUiThread(() -> {
            isSpeaking = false; // we’ll re-queue on retry
            tellStuff("TTS error: " + code);
            if (!ttsRetryScheduled && (code == -7 /*NOT_INSTALLED_YET*/ ||
                    code == -6 /*NETWORK*/          ||
                    code == -5 /*NETWORK_TIMEOUT*/  ||
                    code == -9 /*OUTPUT*/)) {
                ttsRetryScheduled = true;
                tvStatus.postDelayed(() -> {
                    ttsRetryScheduled = false;
                    if (loadedText != null && !loadedText.trim().isEmpty() && tts.isReady()) {
                        tellStuff("Retrying speak…");
                        tts.stop();
                        warmUpTts(tts.getTts());
                        tvStatus.postDelayed(() -> tts.speakFromOffset(loadedText, resumeOffset), 250);
                    }
                }, 600); // small backoff to let engine settle/download
            }
        });
    }

    @Override public void onTtsReady(TextToSpeech t) {
        runOnUiThread(this::populateLanguagesAndVoices);
    }

    // Fallback (chunk-level) – resume at current sentence/chunk start
    @Override public void onUtteranceRange(int start, int end) {
        myLog("onUtteranceRange -  start " + start + " / end " + end);
        resumeOffset = start; // precise sentence start thanks to small chunks
        highlightRange(start, Math.min(end, loadedText.length()));
        updatePlayingStatus();
    }

    // Precise (word-level) – resume at current word
    @Override public void onWordRange(int start, int end) {
        //myLogD("onWordRange -  start " + start + " / end " + end);
        resumeOffset = start; // exact word start
        highlightRange(start, Math.min(end, loadedText.length()));
        updatePlayingStatus();
    }


    // -------------------------------------

    private void warmUpTts(TextToSpeech t) {
        try {
            t.playSilentUtterance(200, TextToSpeech.QUEUE_FLUSH, "warmup"); // API 21+
        } catch (Throwable ignored) {}
    }

    private void highlightRange(int start, int end) {
        if (previewSpannable == null) return;
        int len = previewSpannable.length();
        int s = Math.max(0, Math.min(start, len));
        int e = Math.max(s, Math.min(end, len));

        previewSpannable.removeSpan(wordBgSpan);
        previewSpannable.removeSpan(wordFgSpan);
        previewSpannable.setSpan(wordBgSpan, s, e, 0);
        previewSpannable.setSpan(wordFgSpan, s, e, 0);
        tvPreview.setText(previewSpannable);

        scrollPreviewTo(s);

        // update word counter
        currentWordIndex = findWordIndexAtOrBefore(s);
    }

    private void updatePlayingStatus() {
        int cur = Math.min(currentWordIndex + 1, Math.max(1, totalWords)); // 1-based
        if (totalWords <= 0) {
            tellStuff("Starts Playing...");
        } else {
            String txt = "Playing... (" + cur + " / " + totalWords + ")";
            tvStatus.setText(txt);
        }
    }

    private void scrollPreviewTo(int charIndex) {
        tvPreview.post(() -> {
            try {
                if (tvPreview.getLayout() == null) return;
                int safe = Math.max(0, Math.min(charIndex, tvPreview.getText().length()));
                int line = tvPreview.getLayout().getLineForOffset(safe);
                int y = tvPreview.getLayout().getLineTop(line);
                int targetY = Math.max(0, y - tvPreview.getHeight() / 3);
                tvPreview.scrollTo(0, targetY);
            } catch (Exception ignored) {}
        });
    }


    private void populateLanguagesAndVoices() {
        TextToSpeech t = tts.getTts();
        if (t == null) return;

        // ---- Languages ----
        // Prefer engine-reported languages; fallback: collect locales from voices
        List<Locale> langs = new ArrayList<>();
        try {
            Set<Locale> fromEngine = t.getAvailableLanguages(); // may be null on some engines/OS
            if (fromEngine != null && !fromEngine.isEmpty()) {
                langs.addAll(fromEngine);
            }
        } catch (Throwable ignored) {}

        if (langs.isEmpty()) {
            try {
                Set<Voice> voices = t.getVoices();
                if (voices != null) {
                    for (Voice v : voices) {
                        if (v.getLocale() != null && !langs.contains(v.getLocale())) {
                            langs.add(v.getLocale());
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        availableLanguages.clear();
        // Optional: sort by display name
        langs.sort((a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()));
        availableLanguages.addAll(langs);

        List<String> languageLabels = new ArrayList<>();
        for (Locale loc : availableLanguages) languageLabels.add(loc.getDisplayName());
        languageAdapter.clear();
        languageAdapter.addAll(languageLabels);
        languageAdapter.notifyDataSetChanged();

        // Preselect English if present
        int langIndex = 0;
        for (int i = 0; i < availableLanguages.size(); i++) {
            Locale L = availableLanguages.get(i);
            if ("en".equalsIgnoreCase(L.getLanguage())) { langIndex = i; break; }
        }
        if (!availableLanguages.isEmpty()) spinnerLanguage.setSelection(langIndex);

        spinnerLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!spinnersInitialized) return;
                Locale chosen = availableLanguages.get(position);
                setLanguageSafe(tts.getTts(), chosen, TtsReadTxtActivity.this);
                warmUpTts(tts.getTts());
                tvStatus.postDelayed(() -> {
                    if (!isEmpty(loadedText) && tts.isReady()) {
                        tts.stop();
                        tts.speakFromOffset(loadedText, resumeOffset);
                        isPaused = false;
                        isSpeaking = true;
                        btnStop.setText("Pause");
                        myLog("Resumed at char " + resumeOffset + " (word " + (currentWordIndex+1) + "/" + totalWords + ")");
                    }
                }, 250);

            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // ---- Voices ----
        availableVoices.clear();
        List<String> voiceLabels = new ArrayList<>();
        try {
            Set<Voice> voices = t.getVoices();
            if (voices != null) {
                // Put English voices first (nice UX), then others
                List<Voice> englishFirst = new ArrayList<>();
                List<Voice> others = new ArrayList<>();
                for (Voice v : voices) {
                    if (v.getLocale() != null && "en".equalsIgnoreCase(v.getLocale().getLanguage())) {
                        englishFirst.add(v);
                    } else {
                        others.add(v);
                    }
                }
                englishFirst.sort((a,b) -> a.getName().compareToIgnoreCase(b.getName()));
                others.sort((a,b) -> a.getName().compareToIgnoreCase(b.getName()));
                availableVoices.addAll(englishFirst);
                availableVoices.addAll(others);

                for (Voice v : availableVoices) {
                    String label = v.getName();
                    if (v.getLocale() != null) label += " (" + v.getLocale().getDisplayName() + ")";
                    // Flag network voices
                    if (v.getFeatures() != null && v.getFeatures().contains("networkTts")) {
                        label += " [network]";
                    }
                    voiceLabels.add(label);
                }
            }
        } catch (Throwable ignored) {}

        voiceAdapter.clear();
        voiceAdapter.addAll(voiceLabels);
        voiceAdapter.notifyDataSetChanged();

        // Preselect first English voice if any
        int voiceIndex = 0;
        for (int i = 0; i < availableVoices.size(); i++) {
            Voice v = availableVoices.get(i);
            if (v.getLocale() != null && "en".equalsIgnoreCase(v.getLocale().getLanguage())) {
                voiceIndex = i; break;
            }
        }
        if (!availableVoices.isEmpty()) spinnerVoice.setSelection(voiceIndex);

        spinnerVoice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!spinnersInitialized) return;
                Voice v = availableVoices.get(position);
                try {
                    TextToSpeech t = tts.getTts();
                    t.setVoice(v);
                    if (v.getLocale() != null) setLanguageSafe(t, v.getLocale(), TtsReadTxtActivity.this);
                    tellStuff("Voice: " + v.getName() + (v.getFeatures()!=null && v.getFeatures().contains("networkTts") ? " [network]" : ""));
                    warmUpTts(t);
                } catch (Exception e) {
                    tellStuff("Failed to set voice: " + e.getMessage());
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        spinnersInitialized = true;

    }

    private int setLanguageSafe(TextToSpeech t, Locale loc, Activity activity) {
        int avail = t.isLanguageAvailable(loc);
        if (avail == TextToSpeech.LANG_MISSING_DATA) {
            // Ask user to install TTS data for this language
            try {
                activity.startActivity(
                        new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
            } catch (Exception ignored) {}
            tellStuff("TTS data missing for " + loc.getDisplayName());
            return avail;
        }
        if (avail == TextToSpeech.LANG_NOT_SUPPORTED) {
            tellStuff("Language not supported: " + loc.getDisplayName());
            return avail;
        }
        // OK or partially OK (country/variant) → set it
        int res = t.setLanguage(loc);
        tellStuff("Language set: " + loc.getDisplayName() + " (res=" + res + ")");
        return res;
    }

    private void buildWordIndex(String s) {
        if (s == null) { wordStarts = null; totalWords = 0; currentWordIndex = 0; return; }
        BreakIterator it = BreakIterator.getWordInstance(); // locale-independent tokenization
        it.setText(s);
        List<Integer> starts = new ArrayList<>();
        int start = it.first();
        for (int end = it.next(); end != BreakIterator.DONE; start = end, end = it.next()) {
            // Consider only “words”: letters/digits inside the segment
            if (hasWordChar(s, start, end)) {
                starts.add(start);
            }
        }
        wordStarts = new int[starts.size()];
        for (int i = 0; i < starts.size(); i++) wordStarts[i] = starts.get(i);
        totalWords = wordStarts.length;
        currentWordIndex = 0; // will be advanced by callbacks
    }

    private int findWordIndexAtOrBefore(int charPos) {
        if (wordStarts == null || wordStarts.length == 0) return 0;
        int lo = 0, hi = wordStarts.length - 1, ans = 0;
        int p = Math.max(0, Math.min(charPos, loadedText.length()));
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (wordStarts[mid] <= p) { ans = mid; lo = mid + 1; }
            else { hi = mid - 1; }
        }
        return ans;
    }

    private boolean hasWordChar(String s, int start, int end) {
        for (int i = start; i < end && i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) return true;
        }
        return false;
    }

    private boolean isEmpty(String s){ return s==null || s.trim().isEmpty(); }

    private void togglePauseResume() {
        myLogI("---- USER CLICKS PAUSE/RESUME -----");
        if (!tts.isReady() || isEmpty(loadedText)) return;
        if (isSpeaking && !isPaused) {
            // Pause
            tts.stop();                 // keeps resumeOffset from callbacks
            isPaused = true;
            isSpeaking = false;
            btnStop.setText("Resume");
            myLog("Paused at char " + resumeOffset + " (word " + (currentWordIndex+1) + "/" + totalWords + ")");
            tellStuff("Paused");
        } else {
            // Resume from last word
            tts.speakFromOffset(loadedText, resumeOffset);
            myLog("Resumed at char " + resumeOffset + " (word " + (currentWordIndex+1) + "/" + totalWords + ")");
            isPaused = false;
            isSpeaking = true;
            btnStop.setText("Pause");
            updatePlayingStatus();
        }
    }


    private void tellStuff(String text) {
        tvStatus.setText(text);
        myLogD(text);
    }

    private void tellError(String text) {
        tvStatus.setText("ERROR = " + text);
        myLogE(text);
    }


// call from onUtteranceRange(start,end): scrollPreviewTo(start);


}
