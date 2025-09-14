package com.driot.bookplayer.activities;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.View;
import android.widget.*;

import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.graphics.Color;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.driot.bookplayer.R;
import com.driot.bookplayer.helpers.EbookTtsHelper;
import com.driot.bookplayer.tts.EpubLowLevel;
import com.driot.bookplayer.tts.TxtReader;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.text.BreakIterator;
import java.util.*;

public class TtsReadTxtActivity extends LoggingActivity implements EbookTtsHelper.Listener {

    private static final String PREF_KEY_LAST_URI = "tts_last_txt_uri";

    private long timeStart;

    private TextView tvUri, tvStatus, tvPreview;
    private SeekBar seekSpeed;
    private Button btnPick, btnStart, btnPauseResume;
    private ImageView ivCover;
    private Spinner spinnerLanguage, spinnerVoice;

    private ArrayAdapter<String> languageAdapter, voiceAdapter;
    private final List<Locale> availableLanguages = new ArrayList<>();
    private final List<Voice> availableVoices = new ArrayList<>();

    private Uri pickedUri;
    private String loadedText = "";
    private EbookTtsHelper tts;

    // playback + highlight state
    private int resumeOffset = 0;
    private int[] wordStarts;
    private int totalWords = 0;
    private int currentWordIndex = 0;
    private android.text.Spannable previewSpannable;
    private final BackgroundColorSpan wordBgSpan = new BackgroundColorSpan(0x55FFFF00);
    private final ForegroundColorSpan wordFgSpan = new ForegroundColorSpan(Color.BLACK);
    private boolean isSpeaking = false, isPaused = false;

    // guards
    private boolean spinnersInitialized = false;
    private boolean ttsRetryScheduled = false;

    // --- UI update throttling (ADD THESE) ---
    private final android.os.Handler mainH = new android.os.Handler(android.os.Looper.getMainLooper());
    private int pendingS = -1, pendingE = -1;
    private boolean highlightScheduled = false;
    private int lastScrollLine = -1;


    // SAF picker
    private final ActivityResultLauncher<Intent> openDocLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        try {
                            getContentResolver().takePersistableUriPermission(uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (Exception ignored) {}
                        myLogI("USER picked: " + uri);
                        resetForNewDoc();
                        handlePickedUri(uri);
                    }
                }
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        timeStart = System.currentTimeMillis();
        setContentView(R.layout.activity_tts_read_txt);

        tvUri = findViewById(R.id.tvUri);
        tvStatus = findViewById(R.id.tvStatus);
        tvPreview = findViewById(R.id.tvPreview);
        seekSpeed = findViewById(R.id.seekSpeed);
        btnPick = findViewById(R.id.btnPickTxt);
        btnStart = findViewById(R.id.btnStart);
        btnPauseResume = findViewById(R.id.btnStop);
        ivCover = findViewById(R.id.ivCover);

        spinnerLanguage = findViewById(R.id.spinnerLanguage);
        spinnerVoice = findViewById(R.id.spinnerVoice);

        languageAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLanguage.setAdapter(languageAdapter);

        voiceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        voiceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerVoice.setAdapter(voiceAdapter);

        tts = new EbookTtsHelper(getApplicationContext(), this);

        btnPick.setOnClickListener(v -> { myLogI("USER: pick"); pickDoc(); });
        btnStart.setOnClickListener(v -> { myLogI("USER: start"); startReading(); });
        btnPauseResume.setText("Pause");
        btnPauseResume.setOnClickListener(v -> { myLogI("USER: pause/resume"); togglePauseResume(); });

        seekSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                float rate = Math.max(0.1f, value / 100f);
                try { if (tts != null) tts.setSpeechRate(rate); } catch (Throwable t) {
                    myLogE("could not set Speech Rate");
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    // ---------- Picking & routing ----------

    private void pickDoc() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                        "text/plain",
                        "application/epub+zip"
                })
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        openDocLauncher.launch(i);
    }

    private void handlePickedUri(Uri uri) {
        pickedUri = uri;
        tvUri.setText(String.valueOf(uri));
        getSharedPreferences("tts_demo", MODE_PRIVATE).edit()
                .putString(PREF_KEY_LAST_URI, uri.toString()).apply();

        String mime = getContentResolver().getType(uri);
        String ext = getExt(uri);

        if ("application/epub+zip".equalsIgnoreCase(mime) || "epub".equalsIgnoreCase(ext)) {
            handleEpub(uri);
        } else {
            // default to TXT-like
            handleTxt(uri);
        }
    }

    // ---------- TXT path ----------

    private void handleTxt(Uri uri) {
        tell("Loading text...");
        long startTime = System.currentTimeMillis();
        TxtReader.loadAsync(this, uri, new TxtReader.Callback() {
            @Override public void onLoaded(String text) {
                runOnUiThread(() -> {
                    updatePreviewFromText(text);
                    tell("Loaded " + text.length() + " chars in " + Tonio.formatMS(System.currentTimeMillis() - startTime));
                });
            }
            @Override public void onError(Exception e) {
                runOnUiThread(() -> {
                    updatePreviewFromText("");
                    tell("Load error: " + e.getMessage());
                });
            }
        });
    }

    // ---------- EPUB path ----------

    private void handleEpub(Uri uri) {
        tell("Parsing EPUB...");
        new Thread(() -> {
            try {
                EpubLowLevel.ExtractResult r = EpubLowLevel.extractAll(this, uri);
                Bitmap cover = r.coverBitmap;
                List<File> chapters = r.chapterFiles;

                String firstText = "";
                if (chapters != null && !chapters.isEmpty()) {
                    File f = chapters.get(2);
                    firstText = readUtf8File(f);
                }

                final String textForUi = firstText;
                runOnUiThread(() -> {
                    if (cover != null) {
                        ivCover.setVisibility(View.VISIBLE);
                        ivCover.setImageBitmap(cover);
                    } else {
                        ivCover.setVisibility(View.GONE);
                    }
                    updatePreviewFromText(textForUi);
                    tell("EPUB ready: " + r.bookTitle + " (" + (chapters != null ? chapters.size() : 0) + " chapters)");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    ivCover.setVisibility(View.GONE);
                    updatePreviewFromText("");
                    tell("EPUB parse error");
                });
            }
        }, "EPUB").start();
    }

    private static String readUtf8File(File f) {
        try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream(f), java.nio.charset.StandardCharsets.UTF_8),
                64 * 1024)) {
            StringBuilder sb = new StringBuilder((int) Math.min(Math.max(f.length(), 512_000L), 2_000_000L));
            char[] buf = new char[8192];
            int n;
            while ((n = br.read(buf)) != -1) sb.append(buf, 0, n);
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }


    // ---------- TTS controls ----------

    private void startReading() {
        if (!tts.isReady()) { tell("TTS not ready"); return; }
        if (isEmpty(loadedText)) { tell("No text loaded"); return; }
        tts.stop();
        tts.speakFromOffset(loadedText, resumeOffset);
        isPaused = false; isSpeaking = true;
        btnPauseResume.setText("Pause");
        updatePlayingStatus();
    }

    private void togglePauseResume() {
        if (!tts.isReady() || isEmpty(loadedText)) return;
        if (isSpeaking && !isPaused) {
            tts.stop();
            isPaused = true; isSpeaking = false;
            btnPauseResume.setText("Resume");
            tell("Paused at " + resumeOffset);
        } else {
            tts.speakFromOffset(loadedText, resumeOffset);
            isPaused = false; isSpeaking = true;
            btnPauseResume.setText("Pause");
            updatePlayingStatus();
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (tts != null) { tts.shutdown(); tts = null; }
    }

    // ---------- EbookTtsHelper.Listener ----------

    @Override public void onTtsReady(TextToSpeech t) {
        tell("TTS Ready... (in " + Tonio.formatMS(System.currentTimeMillis() - timeStart) + ")");
        runOnUiThread(this::populateLanguagesAndVoices);
    }

    @Override public void onStart(String id) {
        runOnUiThread(() -> { isSpeaking = true; isPaused = false; tell("Speaking…"); });
    }

    @Override public void onDone(String id)  {
        runOnUiThread(() -> { isSpeaking = false; tell("Done"); });
    }

    @Override public void onError(String id, int code) {
        myLogE("TTS error " + code + " for " + id);
        runOnUiThread(() -> {
            isSpeaking = false;
            tell("TTS error: " + code);
            if (!ttsRetryScheduled && (code == -7 || code == -6 || code == -5 || code == -9)) {
                ttsRetryScheduled = true;
                tvStatus.postDelayed(() -> {
                    ttsRetryScheduled = false;
                    if (!isEmpty(loadedText) && tts.isReady()) {
                        tell("Retrying…");
                        tts.stop();
                        warmUpTts(tts.getTts());
                        tvStatus.postDelayed(() -> tts.speakFromOffset(loadedText, resumeOffset), 250);
                    }
                }, 600);
            }
        });
    }

    @Override public void onUtteranceRange(int start, int end) {
        resumeOffset = start;
        highlightRange(start, Math.min(end, loadedText.length()));
        updatePlayingStatus();
    }

    @Override public void onWordRange(int start, int end) {
        resumeOffset = start;
        highlightRange(start, Math.min(end, loadedText.length()));
        updatePlayingStatus();
    }

    // ---------- UI helpers ----------

    private void resetForNewDoc() {
        if (tts != null) tts.stop();
        lastScrollLine = -1;
        loadedText = "";
        resumeOffset = 0;
        wordStarts = null;
        totalWords = 0;
        currentWordIndex = 0;
        previewSpannable = null;
        tvPreview.setText("");
        tvStatus.setText("");
        ivCover.setVisibility(View.GONE);
    }

    private void updatePreviewFromText(String text) {
        loadedText = text != null ? text : "";
        tvPreview.setText(""); // quick clear
        resumeOffset = 0;
        currentWordIndex = 0;
        lastScrollLine = -1;

        new Thread(() -> {
            buildWordIndex(loadedText);  // background
            SpannableStringBuilder span = new SpannableStringBuilder(loadedText);
            runOnUiThread(() -> {
                // 👇 IMPORTANT: keep a mutable buffer in the TextView
                tvPreview.setText(span, TextView.BufferType.SPANNABLE);
                previewSpannable = (android.text.Spannable) tvPreview.getText();

                tvPreview.setMovementMethod(new ScrollingMovementMethod());
                updatePlayingStatus();
            });
        }, "WordIndex").start();
    }



    private void highlightRange(int start, int end) {
        scheduleHighlight(start, end);
    }
    // ~12 updates/sec; adjust 60–120ms if you want smoother/faster
    private void scheduleHighlight(int s, int e) {
        pendingS = s;
        pendingE = e;
        if (highlightScheduled) return;
        highlightScheduled = true;
        mainH.postDelayed(applyHighlightRunnable, 80);
    }

    private final Runnable applyHighlightRunnable = () -> {
        highlightScheduled = false;
        if (tvPreview.getText() == null) return;

        android.text.Spannable live = (android.text.Spannable) tvPreview.getText();
        int len = live.length();
        if (len == 0 || pendingS < 0) return;

        int s = Math.max(0, Math.min(pendingS, len));
        int e = Math.max(s + 1, Math.min(pendingE, len)); // ensure >= 1 char

        // Remove old spans (by instance or by class to be extra-safe)
        live.removeSpan(wordBgSpan);
        live.removeSpan(wordFgSpan);
        // (also safe) live.removeSpan(BackgroundColorSpan.class); live.removeSpan(ForegroundColorSpan.class);

        live.setSpan(wordBgSpan, s, e, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        live.setSpan(wordFgSpan, s, e, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        // Force redraw so highlight shows
        tvPreview.invalidate();
        tvPreview.postInvalidateOnAnimation();

        scrollPreviewToThrottled(s);
        currentWordIndex = findWordIndexAtOrBefore(s);
        updatePlayingStatus();
    };



    private void updatePlayingStatus() {
        if (isSpeaking) {
            int cur = Math.min(currentWordIndex + 1, Math.max(1, totalWords));
            tvStatus.setText("Playing... (" + cur + " / " + totalWords + ")");
        } else if (isPaused) {
            tvStatus.setText("Paused");
        } else {
            // Not speaking yet: show Ready (or empty if you prefer)
            tvStatus.setText(totalWords > 0 ? "Ready" : "");
        }
    }

    private void scrollPreviewToThrottled(int charIndex) {
        tvPreview.post(() -> {
            try {
                if (tvPreview.getLayout() == null) return;
                int safe = Math.max(0, Math.min(charIndex, tvPreview.getText().length()));
                int line = tvPreview.getLayout().getLineForOffset(safe);
                if (line == lastScrollLine) return; // skip if same visual line
                lastScrollLine = line;

                int y = tvPreview.getLayout().getLineTop(line);
                int targetY = Math.max(0, y - tvPreview.getHeight() / 3);
                tvPreview.scrollTo(0, targetY);
            } catch (Exception ignored) {}
        });
    }


    private void warmUpTts(TextToSpeech t) {
        try { t.playSilentUtterance(200, TextToSpeech.QUEUE_FLUSH, "warmup"); } catch (Throwable ignored) {}
    }

    private void populateLanguagesAndVoices() {
        TextToSpeech t = tts.getTts();
        if (t == null) return;

        // languages
        List<Locale> langs = new ArrayList<>();
        try {
            Set<Locale> fromEngine = t.getAvailableLanguages();
            if (fromEngine != null) langs.addAll(fromEngine);
        } catch (Throwable ignored) {}
        if (langs.isEmpty()) {
            try {
                Set<Voice> voices = t.getVoices();
                if (voices != null) for (Voice v : voices) {
                    if (v.getLocale() != null && !langs.contains(v.getLocale())) langs.add(v.getLocale());
                }
            } catch (Throwable ignored) {}
        }
        langs.sort((a,b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()));
        availableLanguages.clear(); availableLanguages.addAll(langs);

        List<String> languageLabels = new ArrayList<>();
        for (Locale loc : availableLanguages) languageLabels.add(loc.getDisplayName());
        languageAdapter.clear(); languageAdapter.addAll(languageLabels); languageAdapter.notifyDataSetChanged();

        int langIndex = 0;
        for (int i = 0; i < availableLanguages.size(); i++) {
            if ("en".equalsIgnoreCase(availableLanguages.get(i).getLanguage())) { langIndex = i; break; }
        }

        // voices
        availableVoices.clear();
        List<String> voiceLabels = new ArrayList<>();
        try {
            Set<Voice> voices = t.getVoices();
            if (voices != null) {
                List<Voice> list = new ArrayList<>(voices);
                list.sort((a,b) -> a.getName().compareToIgnoreCase(b.getName()));
                availableVoices.addAll(list);
                for (Voice v : availableVoices) {
                    String label = v.getName();
                    if (v.getLocale()!=null) label += " (" + v.getLocale().getDisplayName() + ")";
                    if (v.getFeatures()!=null && v.getFeatures().contains("networkTts")) label += " [network]";
                    voiceLabels.add(label);
                }
            }
        } catch (Throwable ignored) {}
        voiceAdapter.clear(); voiceAdapter.addAll(voiceLabels); voiceAdapter.notifyDataSetChanged();

        // set listeners after data is in place
        spinnersInitialized = false;
        if (!availableLanguages.isEmpty()) spinnerLanguage.setSelection(langIndex, false);
        spinnerLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!spinnersInitialized) return;
                Locale chosen = availableLanguages.get(position);
                setLanguageSafe(tts.getTts(), chosen);
                warmUpTts(tts.getTts());
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        if (!availableVoices.isEmpty()) spinnerVoice.setSelection(0, false);
        spinnerVoice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!spinnersInitialized) return;
                Voice v = availableVoices.get(position);
                try {
                    TextToSpeech t = tts.getTts();
                    t.setVoice(v);
                    if (v.getLocale()!=null) setLanguageSafe(t, v.getLocale());
                    warmUpTts(t);
                } catch (Exception e) { tell("Failed to set voice"); }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnersInitialized = true;
    }

    private int setLanguageSafe(TextToSpeech t, Locale loc) {
        int avail = t.isLanguageAvailable(loc);
        if (avail == TextToSpeech.LANG_MISSING_DATA) {
            try { startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)); } catch (Exception ignored) {}
            tell("TTS data missing for " + loc.getDisplayName());
            return avail;
        }
        if (avail == TextToSpeech.LANG_NOT_SUPPORTED) {
            tell("Language not supported: " + loc.getDisplayName());
            return avail;
        }
        int res = t.setLanguage(loc);
        tell("Language set: " + loc.getDisplayName() + " (res=" + res + ")");
        return res;
    }

    // ---------- text utils ----------

    private void buildWordIndex(String s) {
        if (s == null) { wordStarts = null; totalWords = 0; currentWordIndex = 0; return; }
        BreakIterator it = BreakIterator.getWordInstance();
        it.setText(s);
        List<Integer> starts = new ArrayList<>();
        int start = it.first();
        for (int end = it.next(); end != BreakIterator.DONE; start = end, end = it.next()) {
            if (hasWordChar(s, start, end)) starts.add(start);
        }
        wordStarts = new int[starts.size()];
        for (int i = 0; i < starts.size(); i++) wordStarts[i] = starts.get(i);
        totalWords = wordStarts.length;
        currentWordIndex = 0;
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

    private boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }

    private void tell(String text) {
        tvStatus.setText(text);
        myLog(text);
    }

    // -------- misc --------
    private String getExt(Uri uri) {
        String path = String.valueOf(uri);
        int q = path.indexOf('?'); if (q >= 0) path = path.substring(0,q);
        int hash = path.indexOf('#'); if (hash >= 0) path = path.substring(0,hash);
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot+1) : "";
    }
}
