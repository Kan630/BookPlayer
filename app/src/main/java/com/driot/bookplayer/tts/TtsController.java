package com.driot.bookplayer.tts;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.player.PlayList;
import com.driot.bookplayer.utils.log.LoggerStaticHelper;

import java.util.Locale;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

/**
 * Controller for TTS specific logic: voice management and text preprocessing.
 * Decouples MediaService and TtsEngine from AppTtsManager/Voice details.
 */
@Singleton
public final class TtsController {

    private final AppTtsManager mgr;

    @Inject
    public TtsController(AppTtsManager mgr) {
        this.mgr = mgr;
    }

    /**
     * Resolves the voice to use for the current folder/book.
     */
    @Nullable
    public String resolveVoiceName() {
        String picked = null;
        PlayList pl = PlayList.getInstance();
        if (pl != null && pl.getFolder() != null) {
            picked = pl.getFolder().ttsVoice;
        }

        if (picked == null || picked.isEmpty() || Option.DEFAULT_VOICE.equalsIgnoreCase(picked)) {
            picked = Option.getTtsVoice();
        }

        if (picked != null && (picked.isEmpty() || Option.DEFAULT_VOICE.equalsIgnoreCase(picked))) {
            picked = null;
        }
        return picked;
    }

    /**
     * Applies a voice by name to the shared TTS manager.
     */
    public boolean applyVoiceByName(String voiceName) {
        if (voiceName == null || voiceName.isEmpty() || Option.DEFAULT_VOICE.equalsIgnoreCase(voiceName)) {
            return resetToSystemDefault();
        }

        try {
            Set<Voice> voices = mgr.getVoices();
            if (voices == null || voices.isEmpty())
                return false;

            Voice target = null;
            for (Voice v : voices) {
                if (voiceName.equals(v.getName())) {
                    target = v;
                    break;
                }
            }
            if (target == null)
                return false;

            int r = mgr.setVoice(target);
            if (r != TextToSpeech.SUCCESS) {
                myLogE("TtsController: Error setting TTS Voice: " + voiceName);
                return false;
            }
            myLog("TtsController: Applied voice: " + voiceName);
            return true;
        } catch (Throwable e) {
            myLogEE(e, "TtsController: applyVoiceByName failed");
            return false;
        }
    }

    private boolean resetToSystemDefault() {
        try {
            TextToSpeech raw = mgr.raw();
            if (raw == null)
                return false;
            Locale locale = Locale.getDefault();
            int res = raw.setLanguage(locale);
            TtsErrorUtils.logSetLanguageResult("TtsController", res, locale);
            return (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED);
        } catch (Throwable e) {
            myLogEE(e, "TtsController: resetToSystemDefault failed");
            return false;
        }
    }

    /**
     * Preprocesses raw text for TTS: normalizing newlines and smart paragraphizing.
     */
    public String preprocessText(String raw) {
        if (raw == null)
            return "";
        // Normalize newlines
        String processed = raw.replace("\r\n", "\n").replace('\r', '\n');
        // Paragraphize if flat
        if (TtsHelper.countNewlines(processed) < 2) {
            processed = TtsHelper.smartParagraphize(processed);
        }
        return processed;
    }

    /**
     * Saves the selected voice for the current folder if applicable.
     */
    public void saveVoiceForCurrentFolder(Context context, String voiceName) {
        PlayList pl = PlayList.getInstance();
        if (pl != null && pl.isZikFile()) {
            Folder f = pl.getFolder();
            if (f != null) {
                f.ttsVoice = voiceName;
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    AppDatabase.getInstance(context).folderDao().update(f);
                });
            }
        }
    }

    /**
     * Returns the name of the currently selected voice in the TTS engine.
     */
    @Nullable
    public String getCurrentVoiceName() {
        TextToSpeech raw = mgr.raw();
        if (raw == null)
            return null;
        try {
            Voice v = raw.getVoice();
            return (v != null) ? v.getName() : null;
        } catch (Throwable e) {
            return null;
        }
    }
}
