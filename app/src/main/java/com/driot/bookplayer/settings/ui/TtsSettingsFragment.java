package com.driot.bookplayer.settings.ui;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.checkbox.MaterialCheckBox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.tts.TtsUiHelper;
import com.driot.bookplayer.tts.AppTtsManager;
import com.driot.bookplayer.utils.log.LoggingFragment;

import java.util.concurrent.Executors;

import javax.inject.Inject;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class TtsSettingsFragment extends LoggingFragment {

    @Inject
    protected AppTtsManager ttsManager;

    private String lastSavedTtsVoice;
    private MaterialCheckBox chkTtsSnapToSentence, chkTtsShowLoadingOverlay;
    private EditText etTtsHighlightDelay, etTtsChunkSize, etTtsOverlayTimeout;
    private Spinner spinnerEpubSplitMode;
    private MaterialCheckBox chkEbookRemoveReferences;
    private MaterialCheckBox chkDocxSplitIntoChapters;
    private MaterialCheckBox chkTtsFullscreenControls;
    private LinearLayout llEbookRemoveReferences;
    private LinearLayout llDocxSplitIntoChapters;
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

        TtsUiHelper.setupTtsVoiceSpinnerForSettings(
                getViewLifecycleOwner(),
                requireActivity(),
                ttsVoiceSpinner,
                ttsManager,
                lastSavedTtsVoice,
                voiceItem -> {
                    myLogD("TtsUiHelper.setupTtsVoiceSpinner callback with voiceItem = "
                            + (voiceItem == null ? "null" : voiceItem.name));
                    String sel = (voiceItem == null || voiceItem.name == null || voiceItem.name.isEmpty())
                            ? Option.DEFAULT_VOICE
                            : voiceItem.name;
                    if (!sel.equalsIgnoreCase(lastSavedTtsVoice)) {
                        Option.setTtsVoice(sel);
                        lastSavedTtsVoice = sel;
                        myLogI("TTS default base voice set to: " + sel);
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

        // EPUB Split Mode spinner
        spinnerEpubSplitMode = root.findViewById(R.id.spinner_epub_split_mode);

        setupEpubSplitModeSpinner();

        // Remove reference markers (footnotes) in ebooks
        chkEbookRemoveReferences = root.findViewById(R.id.chk_ebook_remove_references);
        llEbookRemoveReferences = root.findViewById(R.id.ll_ebook_remove_references);
        chkEbookRemoveReferences.setChecked(Option.getEbookRemoveReferencesFootnotes());
        llEbookRemoveReferences.setOnClickListener(v -> chkEbookRemoveReferences.toggle());
        chkEbookRemoveReferences
                .setOnCheckedChangeListener(
                        (buttonView, isChecked) -> Option.setEbookRemoveReferencesFootnotes(isChecked));

        // Docx split into chapters
        chkDocxSplitIntoChapters = root.findViewById(R.id.chk_docx_split_into_chapters);
        llDocxSplitIntoChapters = root.findViewById(R.id.ll_docx_split_into_chapters);
        if (chkDocxSplitIntoChapters != null && llDocxSplitIntoChapters != null)

        {
            chkDocxSplitIntoChapters.setChecked(Option.getDocxSplitIntoChapters());
            llDocxSplitIntoChapters.setOnClickListener(v -> chkDocxSplitIntoChapters.toggle());
            chkDocxSplitIntoChapters
                    .setOnCheckedChangeListener((buttonView, isChecked) -> Option.setDocxSplitIntoChapters(isChecked));
        }

        chkTtsSnapToSentence = root.findViewById(R.id.chk_tts_snap_to_sentence);
        LinearLayout llTtsSnapToSentence = root.findViewById(R.id.ll_tts_snap_to_sentence);
        chkTtsSnapToSentence.setChecked(Option.getTtsSnapToSentence());
        llTtsSnapToSentence.setOnClickListener(v -> chkTtsSnapToSentence.toggle());
        chkTtsSnapToSentence
                .setOnCheckedChangeListener((buttonView, isChecked) -> Option.setTtsSnapToSentence(isChecked));

        chkTtsShowLoadingOverlay = root.findViewById(R.id.chk_tts_show_loading_overlay);
        LinearLayout llTtsShowLoadingOverlay = root.findViewById(R.id.ll_tts_show_loading_overlay);
        chkTtsShowLoadingOverlay.setChecked(Option.getTtsShowLoadingOverlay());
        llTtsShowLoadingOverlay.setOnClickListener(v -> chkTtsShowLoadingOverlay.toggle());
        chkTtsShowLoadingOverlay
                .setOnCheckedChangeListener((buttonView, isChecked) -> Option.setTtsShowLoadingOverlay(isChecked));

        chkTtsFullscreenControls = root.findViewById(R.id.chk_tts_fullscreen_controls);
        LinearLayout llTtsFullscreenControls = root.findViewById(R.id.ll_tts_fullscreen_controls);
        if (chkTtsFullscreenControls != null && llTtsFullscreenControls != null) {
            chkTtsFullscreenControls.setChecked(Option.getTtsFullscreenControls());
            llTtsFullscreenControls.setOnClickListener(v -> chkTtsFullscreenControls.toggle());
            chkTtsFullscreenControls
                    .setOnCheckedChangeListener((buttonView, isChecked) -> Option.setTtsFullscreenControls(isChecked));
        }

        etTtsOverlayTimeout = root.findViewById(R.id.et_tts_overlay_timeout);
        etTtsOverlayTimeout.setText(String.valueOf(Option.getTtsOverlayTimeoutSec()));

        return root;
    }

    private void setupEpubSplitModeSpinner() {
        String[] options = new String[] {
                getString(R.string.option_epub_split_mode_auto),
                getString(R.string.option_epub_split_mode_toc),
                getString(R.string.option_epub_split_mode_spine),
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(), R.layout.spinner_item, options);
        adapter.setDropDownViewResource(R.layout.spinner_item);
        spinnerEpubSplitMode.setAdapter(adapter);

        // Set current selection
        String currentMode = Option.getEpubSplitMode();
        int selection = 0; // default to "auto"
        if ("toc".equals(currentMode)) {
            selection = 1;
        } else if ("spine".equals(currentMode)) {
            selection = 2;
        }
        spinnerEpubSplitMode.setSelection(selection, false);

        spinnerEpubSplitMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String mode;
                switch (position) {
                    case 1:
                        mode = "toc";
                        break;
                    case 2:
                        mode = "spine";
                        break;
                    default:
                        mode = "auto";
                        break;
                }
                Option.setEpubSplitMode(mode);
                myLogD("EPUB split mode set to: " + mode);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
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

        final Integer overlayTimeout;
        if (etTtsOverlayTimeout != null) {
            overlayTimeout = Option.clampInt(
                    ctx,
                    etTtsOverlayTimeout,
                    5, 20,
                    Option.DEFAULT_TTS_OVERLAY_TIMEOUT_SEC,
                    ctx.getString(R.string.option_tts_overlay_timeout_title));
        } else {
            overlayTimeout = null;
        }

        // --- Persist off the main thread ---
        Executors.newSingleThreadExecutor().execute(() -> {
            if (highlightDelay != null) {
                Option.setTtsHighlightDelayMs(highlightDelay);
            }
            if (chunkSize != null) {
                Option.setTtsChunkSize(chunkSize);
            }
            if (overlayTimeout != null) {
                Option.setTtsOverlayTimeoutSec(overlayTimeout);
            }
        });
    }

}
