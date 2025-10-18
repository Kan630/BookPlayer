package com.driot.bookplayer.activities;

import android.os.Bundle;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.objects.VoiceItem;
import com.driot.bookplayer.tts.TtsHelper;
import com.driot.bookplayer.utils.log.LoggingActivity;

public class TtsSettingsActivity extends LoggingActivity {

    AutoCloseable ttsHandle;
    String lastSavedTtsVoice;
    EditText et_tts_highlight_delay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tts_settings);
        InsetHelper.apply(this);

/// TTS  VOICE
        Spinner ttsVoiceSpinner = findViewById(R.id.spinner_voice_item);
        lastSavedTtsVoice = Option.getTtsVoice();
        myLogD("setUp Voice Spinner, saved voice = " + lastSavedTtsVoice);
        ttsHandle = TtsHelper.setupTtsVoiceSpinner(
                this,
                ttsVoiceSpinner,
                lastSavedTtsVoice,
                voiceItem -> {
                    if (voiceItem == null) {
                        myLogD("TTS voice chosen is null");
                    } else {
                        myLogD("TTS voice chosen: " + VoiceItem.describeVoice(voiceItem.voice).replace(", ","\n"));
                    }
                    String sel = (voiceItem == null || voiceItem.name == null || voiceItem.name.isEmpty())
                            ? "system" : voiceItem.name;

                    if (!sel.equalsIgnoreCase(lastSavedTtsVoice)) {
                        Option.setTtsVoice(sel);
                        lastSavedTtsVoice = sel;
                        myLog("TTS default base voice set to: " + sel + " (" + (voiceItem == null ? "system" : voiceItem.displayName + " / - name = " + voiceItem.name + ")"));
                    }
                }
        );

        et_tts_highlight_delay = findViewById(R.id.et_tts_highlight_delay);
        et_tts_highlight_delay.setText(String.valueOf(Option.getTtsHighlightDelayMs()));

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN); // Avoid keyboard on opening
    }

    private void saveEditTextValues() {
        if (et_tts_highlight_delay != null ) {
            int value1 = clampInt(et_tts_highlight_delay, 0, 400, Option.DEFAULT_TTS_HIGHLIGHT_DELAY_MS,
                    () -> myLongToast(getString(R.string.delay_for_auto_deletion) + " " + getString(R.string.too_low)),
                    () -> myLongToast(getString(R.string.delay_for_auto_deletion) + " " + getString(R.string.too_high)));
            Option.setTtsHighlightDelayMs(value1);
        }
    }


    @Override
    protected void onDestroy() {
        try { if (ttsHandle != null) ttsHandle.close(); } catch (Exception ignored) {}
        saveEditTextValues();
        super.onDestroy();
    }

    public static int clampInt(EditText et, int min, int max, int def, Runnable onTooLow, Runnable onTooHigh) {
        if (et == null) return def;
        String str = et.getText().toString().trim();
        int val;
        try {
            val = Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return def;
        }

        if (val < min) {
            if (onTooLow != null) onTooLow.run();
            return min;
        } else if (val > max) {
            if (onTooHigh != null) onTooHigh.run();
            return max;
        }
        return val;
    }

}
