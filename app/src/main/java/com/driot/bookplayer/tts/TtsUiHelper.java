package com.driot.bookplayer.tts;

import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import com.driot.bookplayer.adapter.VoiceSpinnerAdapter;
import com.driot.bookplayer.global.Option;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

/**
 * UI-related helpers for TTS voice spinners.
 */
public final class TtsUiHelper {

    public interface OnVoiceSelected {
        void onSelected(@Nullable VoiceItem voice);
    }

    /**
     * Sets up the TTS voice spinner for the Settings screen (SharedPref path).
     *
     * Uses {@link AppTtsManager#getVoicesLiveData()} with a LifecycleOwner so
     * the observer is automatically removed when the Fragment view is destroyed,
     * and it populates immediately if TTS is already initialised.
     *
     * @param owner      Fragment's {@code getViewLifecycleOwner()} — scopes the
     *                   observer.
     * @param ui_context Themed context (e.g. {@code requireActivity()}).
     * @param spinner    The spinner widget to populate.
     * @param mgr        Injected {@link AppTtsManager} singleton.
     * @param savedCode  Currently saved voice name (or
     *                   {@link Option#DEFAULT_VOICE}).
     * @param callback   Invoked once on pre-selection, then on every user pick.
     */
    public static void setupTtsVoiceSpinnerForSettings(
            @NonNull LifecycleOwner owner,
            @NonNull android.content.Context ui_context,
            @NonNull Spinner spinner,
            @NonNull AppTtsManager mgr,
            @Nullable String savedCode,
            @NonNull OnVoiceSelected callback) {

        myLog("setupTtsVoiceSpinnerForSettings - savedCode=[" + savedCode + "]");

        // Show "Loading voices…" while we wait for LiveData to fire
        ArrayAdapter<String> loadingAdapter = new ArrayAdapter<>(
                ui_context, android.R.layout.simple_spinner_item,
                Collections.singletonList("Loading voices…"));
        loadingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(loadingAdapter);
        spinner.setEnabled(false);

        // Observe LiveData — fires immediately if already populated, cleans up
        // automatically with the lifecycle.
        mgr.getVoicesLiveData().observe(owner, voices -> {
            if (voices == null || voices.isEmpty()) {
                myLogE("setupTtsVoiceSpinnerForSettings: empty voice list from LiveData");
                ArrayAdapter<String> empty = new ArrayAdapter<>(
                        ui_context, android.R.layout.simple_spinner_item,
                        Collections.singletonList("No voices"));
                empty.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinner.setAdapter(empty);
                spinner.setEnabled(false);
                callback.onSelected(null);
                return;
            }
            myLog("setupTtsVoiceSpinnerForSettings: " + voices.size() + " voices");
            populateSpinnerFromVoices(ui_context, spinner, mgr, voices, savedCode, callback);
        });
    }

    // ---- private helpers ----

    private static void populateSpinnerFromVoices(
            @NonNull android.content.Context ui,
            @NonNull Spinner spinner,
            @NonNull AppTtsManager mgr,
            @NonNull List<VoiceItem> voices,
            @Nullable String savedCode,
            @NonNull OnVoiceSelected callback) {

        final boolean[] hasBeenInitialized = { false };

        final ArrayList<VoiceItem> all = new ArrayList<>();
        android.speech.tts.TextToSpeech ttsRaw = mgr.raw();
        VoiceItem system = ttsRaw != null ? VoiceItem.makeSystemDefault(ttsRaw) : null;
        if (system != null)
            all.add(system);
        all.addAll(voices);

        int currentSelected = 0;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).name.equals(savedCode)) {
                currentSelected = i;
                break;
            }
        }

        final VoiceSpinnerAdapter adapter = new VoiceSpinnerAdapter(ui, all);
        spinner.setAdapter(adapter);
        if (currentSelected >= 0) {
            spinner.setSelection(currentSelected);
            adapter.setSelectedPosition(currentSelected);
        }
        spinner.setEnabled(true);

        final int preSelected = currentSelected;
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                adapter.setSelectedPosition(position);
                if (!hasBeenInitialized[0]) {
                    hasBeenInitialized[0] = true;
                    myLogD("setupTtsVoiceSpinnerForSettings: ignoring init callback");
                    return;
                }
                callback.onSelected((VoiceItem) parent.getItemAtPosition(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }
}
