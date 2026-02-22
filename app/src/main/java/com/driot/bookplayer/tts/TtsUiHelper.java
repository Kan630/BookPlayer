package com.driot.bookplayer.tts;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.adapter.VoiceSpinnerAdapter;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.helpers.CallerHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

/**
 * UI-related helpers for TTS, extracted from TtsHelper.
 */
public final class TtsUiHelper {

    public interface OnVoiceSelected {
        void onSelected(@Nullable VoiceItem voice);
    }

    public static void setupTtsVoiceSpinnerForSettings(
            @NonNull Context ui_context,
            @NonNull Spinner spinner,
            @NonNull AppTtsManager mgr,
            @Nullable String savedCode,
            @NonNull OnVoiceSelected callback) {
        myLog("setupTtsVoiceSpinnerForSettings - savedCode=[" + savedCode + "]");
        TextToSpeech tts = mgr.raw();
        if (tts == null) {
            myLogE("setupTtsVoiceSpinnerForSettings: TTS not ready");
            setEmptySpinner(ui_context, spinner, callback);
            return;
        }

        final List<VoiceItem> voices = buildVoiceItems(tts);
        if (voices.isEmpty()) {
            myLogE("setupTtsVoiceSpinnerForSettings - no voices");
            setEmptySpinner(ui_context, spinner, callback);
            return;
        }

        final ArrayList<VoiceItem> all = new ArrayList<>();
        VoiceItem system = VoiceItem.makeSystemDefault(tts);
        if (system != null)
            all.add(system);
        all.addAll(voices);

        int currentSelected = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).name.equals(savedCode)) {
                currentSelected = i;
                break;
            }
        }

        final VoiceSpinnerAdapter adapter = new VoiceSpinnerAdapter(ui_context, all);
        spinner.setAdapter(adapter);
        if (currentSelected >= 0) {
            spinner.setSelection(currentSelected);
            adapter.setSelectedPosition(currentSelected);
        }
        spinner.setEnabled(true);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                adapter.setSelectedPosition(position);
                callback.onSelected((VoiceItem) parent.getItemAtPosition(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    public static @NonNull AutoCloseable setupTtsVoiceSpinner(
            @NonNull Context ui_context,
            @NonNull Spinner spinner,
            @NonNull AppTtsManager mgr,
            @Nullable String savedCode,
            @NonNull OnVoiceSelected callback) {
        myLog("setupTtsVoiceSpinner - savedCode=[" + savedCode + "]");
        final Handler main = new Handler(Looper.getMainLooper());

        final ArrayAdapter<String> loadingAdapter = new ArrayAdapter<>(
                ui_context, android.R.layout.simple_spinner_item,
                Collections.singletonList("Loading voices…"));
        loadingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(loadingAdapter);
        spinner.setEnabled(false);

        mgr.setPreferredVoiceName(savedCode);

        final java.util.concurrent.atomic.AtomicBoolean populatedOnce = new java.util.concurrent.atomic.AtomicBoolean(
                false);
        final boolean[] suppressSelection = new boolean[] { true };

        final AppTtsManager.Listener mgrListener = new AppTtsManager.Listener() {
            @Override
            public void onTtsReady(TextToSpeech tts) {
                if (!populatedOnce.compareAndSet(false, true))
                    return;
                main.post(() -> {
                    final List<VoiceItem> voices = buildVoiceItems(tts);
                    if (voices.isEmpty()) {
                        setEmptySpinner(ui_context, spinner, callback);
                        return;
                    }

                    final ArrayList<VoiceItem> all = new ArrayList<>();
                    VoiceItem system = VoiceItem.makeSystemDefault(tts);
                    if (system != null)
                        all.add(system);
                    all.addAll(voices);

                    final VoiceSpinnerAdapter adapter = new VoiceSpinnerAdapter(ui_context, all);
                    spinner.setAdapter(adapter);
                    spinner.setEnabled(true);

                    int pre = 0;
                    if (savedCode != null && !Option.DEFAULT_VOICE.equalsIgnoreCase(savedCode)) {
                        for (int i = 1; i < all.size(); i++) {
                            if (savedCode.equals(all.get(i).name)) {
                                pre = i;
                                break;
                            }
                        }
                    }

                    spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                            adapter.setSelectedPosition(pos);
                            if (!suppressSelection[0]) {
                                callback.onSelected(all.get(pos));
                            }
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {
                        }
                    });

                    spinner.setSelection(pre, false);
                    adapter.setSelectedPosition(pre);
                    callback.onSelected(all.get(pre));
                    spinner.post(() -> suppressSelection[0] = false);
                });
            }
        };

        mgr.addListener(mgrListener);
        return () -> {
            main.post(() -> {
                try {
                    spinner.setOnItemSelectedListener(null);
                } catch (Throwable ignored) {
                }
            });
            mgr.removeListener(mgrListener);
        };
    }

    private static void setEmptySpinner(Context ctx, Spinner spinner, OnVoiceSelected callback) {
        ArrayAdapter<String> empty = new ArrayAdapter<>(
                ctx, android.R.layout.simple_spinner_item,
                Collections.singletonList("No voices"));
        empty.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(empty);
        spinner.setEnabled(false);
        callback.onSelected(null);
    }

    public static List<VoiceItem> buildVoiceItems(TextToSpeech tts) {
        List<VoiceItem> out = new ArrayList<>();
        try {
            for (Voice v : tts.getVoices()) {
                out.add(new VoiceItem(v));
            }
        } catch (Throwable ignored) {
        }

        out.sort(Comparator
                .comparing((VoiceItem i) -> i.twoLetterCodeLanguage, String::compareToIgnoreCase)
                .thenComparing((VoiceItem i) -> !i.embedded)
                .thenComparing((VoiceItem i) -> -i.quality)
                .thenComparingInt(i -> i.latency)
                .thenComparing(i -> i.name, String.CASE_INSENSITIVE_ORDER));

        return out;
    }
}
