package com.driot.bookplayer.settings.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.objects.VoiceItem;
import com.driot.bookplayer.tts.TtsHelper;
import com.driot.bookplayer.utils.log.LoggingFragment;

import java.util.concurrent.Executors;

public class TtsSettingsFragment extends LoggingFragment {

    private AutoCloseable ttsHandle;
    private String lastSavedTtsVoice;
    private EditText etTtsHighlightDelay, etTtsChunkSize;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.activity_tts_settings, container, false);

        // Hide the local title row when embedded inline (ARG_SHOW_LOCAL_TITLE=false)
        boolean showLocalTitle = true;
        Bundle args = getArguments();
        if (args != null) showLocalTitle = args.getBoolean("ARG_SHOW_LOCAL_TITLE", true);
        View titleContainer = root.findViewById(R.id.ll_title);
        if (titleContainer != null) {
            titleContainer.setVisibility(showLocalTitle ? View.VISIBLE : View.GONE);
        }

        // --- TTS Voice spinner ---
        Spinner ttsVoiceSpinner = root.findViewById(R.id.spinner_voice_item);
        lastSavedTtsVoice = Option.getTtsVoice();
        myLogD("setUp Voice Spinner, saved voice = " + lastSavedTtsVoice);

        // If TtsHelper expects an Activity context, use requireActivity(); otherwise requireContext() is fine.
        ttsHandle = TtsHelper.setupTtsVoiceSpinner(
                requireContext(),
                ttsVoiceSpinner,
                lastSavedTtsVoice,
                voiceItem -> {
                    if (voiceItem == null) {
                        myLogD("TTS voice chosen is null");
                    } else {
                        myLogD("TTS voice chosen: " + VoiceItem.describeVoice(voiceItem.voice).replace(", ", "\n"));
                    }

                    String sel = (voiceItem == null || voiceItem.name == null || voiceItem.name.isEmpty())
                            ? "system"
                            : voiceItem.name;

                    if (!sel.equalsIgnoreCase(lastSavedTtsVoice)) {
                        Option.setTtsVoice(sel);
                        lastSavedTtsVoice = sel;
                        myLog("TTS default base voice set to: " + sel + " (" +
                                (voiceItem == null ? "system" : (voiceItem.displayName + " / - name = " + voiceItem.name + ")")));
                    }
                }
        );

        // --- Fields ---
        etTtsHighlightDelay = root.findViewById(R.id.et_tts_highlight_delay);
        etTtsHighlightDelay.setText(String.valueOf(Option.getTtsHighlightDelayMs()));

        etTtsChunkSize = root.findViewById(R.id.et_tts_chunk_size);
        etTtsChunkSize.setText(String.valueOf(Option.getTtsChunkSize()));

        return root;
    }

    @Override
    public void onPause() {
        super.onPause();
        saveEditTextValues();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        try { if (ttsHandle != null) ttsHandle.close(); } catch (Exception ignored) {}
        ttsHandle = null;
    }

    private void saveEditTextValues() {
        //TODO check if needed executor (check with StrictMode)
        Executors.newSingleThreadExecutor().execute(() -> {
            if (getContext() == null) return;

            if (etTtsHighlightDelay != null) {
                int v1 = Option.clampInt(
                        getContext(),
                        etTtsHighlightDelay,
                        /* min */ 0,
                        /* max */ 400,
                        /* def */ Option.DEFAULT_TTS_HIGHLIGHT_DELAY_MS,
                        getString(R.string.option_tts_highlight_delay_outOfBounds)
                );
                Option.setTtsHighlightDelayMs(v1);
            }

            if (etTtsChunkSize != null) {
                int v2 = Option.clampInt(
                        getContext(),
                        etTtsChunkSize,
                        /* min */ 1200,
                        /* max */ 9999,
                        /* def */ Option.DEFAULT_TTS_CHUNK_SIZE,
                        getString(R.string.tts_chunk_size)
                );
                Option.setTtsChunkSize(v2);
            }
        });
    }
}
