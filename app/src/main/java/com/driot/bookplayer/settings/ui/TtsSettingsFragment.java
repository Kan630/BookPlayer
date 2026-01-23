package com.driot.bookplayer.settings.ui;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.tts.TtsHelper;
import com.driot.bookplayer.utils.log.LoggingFragment;

import java.util.concurrent.Executors;

public class TtsSettingsFragment extends LoggingFragment {

    private String lastSavedTtsVoice;
    private EditText etTtsHighlightDelay, etTtsChunkSize;
    private boolean hasBeenInitialized = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_settings_tts, container, false);

        // Show/hide local title when embedded
        boolean showLocalTitle = true;
        Bundle args = getArguments();
        if (args != null)
            showLocalTitle = args.getBoolean("ARG_SHOW_LOCAL_TITLE", true);
        View titleContainer = root.findViewById(R.id.ll_title);
        if (titleContainer != null) {
            titleContainer.setVisibility(showLocalTitle ? View.VISIBLE : View.GONE);
        }

        // Voice spinner
        Spinner ttsVoiceSpinner = root.findViewById(R.id.spinner_voice_item);
        lastSavedTtsVoice = Option.getTtsVoice();
        myLogD("setUp Voice Spinner, saved voice = " + lastSavedTtsVoice);

        TtsHelper.setupTtsVoiceSpinnerForSettings(
                /* if it needs Activity: */ requireActivity(),
                /* otherwise use requireContext() */ ttsVoiceSpinner,
                lastSavedTtsVoice,
                voiceItem -> {
                    myLogD("TtsHelper.setupTtsVoiceSpinner callback with voiceItem = "
                            + (voiceItem == null ? "null" : voiceItem.name));
                    if (hasBeenInitialized) {
                        String sel = (voiceItem == null || voiceItem.name == null || voiceItem.name.isEmpty())
                                ? Option.DEFAULT_VOICE
                                : voiceItem.name;
                        if (!sel.equalsIgnoreCase(lastSavedTtsVoice)) {
                            Option.setTtsVoice(sel);
                            lastSavedTtsVoice = sel;
                            myLogI("TTS default base voice set to: " + sel);
                        }
                    } else {
                        hasBeenInitialized = true;
                        myLogD("ignoring callback on init");
                    }
                });

        // Fields
        etTtsHighlightDelay = root.findViewById(R.id.et_tts_highlight_delay);
        etTtsHighlightDelay.setText(String.valueOf(Option.getTtsHighlightDelayMs()));

        etTtsChunkSize = root.findViewById(R.id.et_tts_chunk_size);
        etTtsChunkSize.setText(String.valueOf(Option.getTtsChunkSize()));

        // Update max value display with device-specific maximum
        TextView tvMax = root.findViewById(R.id.tv_tts_chunk_size_max);
        if (tvMax != null) {
            int maxInputLength = TextToSpeech.getMaxSpeechInputLength();
            tvMax.setText(getString(com.driot.bookplayer.R.string.max_value_label) + maxInputLength);
        }

        return root;
    }

    @Override
    public void onPause() {
        super.onPause();
        saveEditTextValues();
    }

    private void saveEditTextValues() {
        if (!isAdded())
            return; // fragment still attached

        final Context ctx = requireContext();

        // --- Read & validate on UI thread ---

        final Integer highlightDelay;
        if (etTtsHighlightDelay != null) {
            highlightDelay = Option.clampInt(
                    ctx,
                    etTtsHighlightDelay,
                    0, 400,
                    Option.DEFAULT_TTS_HIGHLIGHT_DELAY_MS,
                    ctx.getString(R.string.option_tts_highlight_delay_outOfBounds));
        } else {
            highlightDelay = null;
        }

        final Integer chunkSize;
        if (etTtsChunkSize != null) {
            int maxInputLength = TextToSpeech.getMaxSpeechInputLength();
            chunkSize = Option.clampInt(
                    ctx,
                    etTtsChunkSize,
                    1200, maxInputLength,
                    Option.DEFAULT_TTS_CHUNK_SIZE,
                    ctx.getString(R.string.tts_chunk_size));
        } else {
            chunkSize = null;
        }

        // --- Persist off the main thread ---
        Executors.newSingleThreadExecutor().execute(() -> {
            if (highlightDelay != null) {
                Option.setTtsHighlightDelayMs(highlightDelay);
            }
            if (chunkSize != null) {
                Option.setTtsChunkSize(chunkSize);
            }
        });
    }

}
