package com.driot.bookplayer.tts;

import android.content.Context;
import android.content.Intent;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

public final class TtsErrorUtils {
    private static final String TAG = "TtsErrorUtils";
    private TtsErrorUtils() {}

    // ---------- Public helpers ----------

    /** Interpret result from setLanguage(Locale). */
    public static String describeSetLanguageResult(int result, Locale locale) {
        switch (result) {
            case TextToSpeech.LANG_AVAILABLE:
                return ok("Language available: " + localeToString(locale));
            case TextToSpeech.LANG_COUNTRY_AVAILABLE:
                return ok("Language+country available: " + localeToString(locale));
            case TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE:
                return ok("Language+country+variant available: " + localeToString(locale));
            case TextToSpeech.LANG_MISSING_DATA: // == -1
                return warn("Language data missing for " + localeToString(locale) +
                        ". Prompt user to install TTS data (Intent.ACTION_INSTALL_TTS_DATA).");
            case TextToSpeech.LANG_NOT_SUPPORTED: // == -2
                return error("Language NOT supported: " + localeToString(locale) +
                        ". Try a different Locale or engine.");
            default:
                return error("Unknown setLanguage() result: " + result +
                        " for " + localeToString(locale));
        }
    }

    /** Interpret generic TTS API return (e.g., speak(), stop(), synthesizeToFile()). */
    public static String describeOperationResult(String opName, int result) {
        if (result == TextToSpeech.SUCCESS) return ok(opName + " succeeded");
        if (result == TextToSpeech.ERROR)   return error(opName + " failed (generic ERROR). See onError() callback for details.");
        // Some engines may return hidden negative values directly (rare). Handle them too:
        return mapEngineError(opName, result);
    }

    /** Interpret onError(utteranceId, errorCode) detailed engine error. */
    public static String describeOnErrorCode(int errorCode) {
        switch (errorCode) {
            // Hidden/engine codes used by Google TTS & others
            case -4:  return error("Invalid request (bad params/null text/engine busy).");
            case -5:  return error("Network error while fetching voice/data.");
            case -6:  return error("Network timeout while fetching voice/data.");
            case -7:  return error("Audio output error (route/device/focus).");
            case -8:  return error("Service error (engine crashed or unresponsive).");
            case -9:  return error("Synthesis error (failed to generate audio).");
            case -10: return error("Feature unsupported by this engine.");
            // Public ones (older path)
            case TextToSpeech.ERROR: return error("Generic error.");
            default:  return error("Unknown TTS error code (" + errorCode + ").");
        }
    }

    /** One-liner to log setLanguage() outcome. */
    public static void logSetLanguageResult(String tag, int result, Locale locale) {
        Log.i(tag, describeSetLanguageResult(result, locale));
    }

    /** One-liner to log speak/synthesize outcome. */
    public static void logOperationResult(String tag, String opName, int result) {
        Log.i(tag, describeOperationResult(opName, result));
    }

    // ---------- Suggested mitigations ----------

    /** Open the system flow to install/download TTS data (voices). */
    public static void promptInstallTtsData(Context ctx) {
        Intent intent = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
        // Let the system choose the right engine/config UI
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "No activity can handle ACTION_INSTALL_TTS_DATA", e);
        }
    }

    /** Good default: check availability before setLanguage (optional). */
    public static String checkLanguageAvailability(TextToSpeech tts, Locale locale) {
        int avail = tts.isLanguageAvailable(locale);
        switch (avail) {
            case TextToSpeech.LANG_AVAILABLE:             return "Available";
            case TextToSpeech.LANG_COUNTRY_AVAILABLE:     return "Country available";
            case TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE: return "Country+variant available";
            case TextToSpeech.LANG_MISSING_DATA:          return "Missing data";
            case TextToSpeech.LANG_NOT_SUPPORTED:         return "Not supported";
            default:                                      return "Unknown (" + avail + ")";
        }
    }

    // ---------- Private helpers ----------

    private static String mapEngineError(String opName, int code) {
        // Interpret hidden codes if they leak via result
        switch (code) {
            case -4:  return error(opName + " failed: Invalid request.");
            case -5:  return error(opName + " failed: Network error.");
            case -6:  return error(opName + " failed: Network timeout.");
            case -7:  return error(opName + " failed: Audio output error.");
            case -8:  return error(opName + " failed: Service error.");
            case -9:  return error(opName + " failed: Synthesis error.");
            case -10: return error(opName + " failed: Feature unsupported.");
            default:  return error(opName + " failed with unknown code (" + code + ").");
        }
    }

    private static String ok(String s)   { return "✓ " + s; }
    private static String warn(String s) { return "⚠ " + s; }
    private static String error(String s){ return "✗ " + s; }

    private static String localeToString(Locale l) {
        if (l == null) return "null";
        String bcp47 = l.toLanguageTag();
        return bcp47 + " (" + l.toString() + ")";
    }
}

