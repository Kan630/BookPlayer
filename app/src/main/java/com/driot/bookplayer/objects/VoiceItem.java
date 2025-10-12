package com.driot.bookplayer.objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import com.driot.bookplayer.helpers.FlagHelper;
import com.driot.bookplayer.utils.log.KanLogger;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;

public class VoiceItem {
    public final Voice voice; // android parcelable

    // Stable identifiers / properties
    public final String name;           // engine voice name (stable key)
    public final Locale locale;         // may include region
    public final int quality;           // Voice#getQuality()
    public final int latency;           // Voice#getLatency()
    public final boolean requiresNetwork;
    public final boolean embedded;      // features contains "embeddedTts"
    public final Set<String> features;

    // UI / app metadata
    public final String twoLetterCodeLanguage;   // e.g. "en"
    public final String displayName;             // pretty label
    public final String voiceDetails;
    public final int flagResIdLanguage;          // your flag for language
    public final int flagResIdCountry;             // optional per-voice icon/flag

    public VoiceItem(Voice v) {
        this.voice = v; // base built-in object, now let's add some field...
        this.name = v.getName();
        this.locale = v.getLocale();
        this.quality = v.getQuality();
        this.latency = v.getLatency();
        this.requiresNetwork = v.isNetworkConnectionRequired();
        Set<String> f = v.getFeatures();
        this.features = (f == null ? Collections.emptySet() : f);
        this.embedded = features.contains("embeddedTts");

        String lang2 = this.locale != null && !this.locale.getLanguage().isEmpty()
                ? this.locale.getLanguage() : "und";
        String country = (this.locale != null && !this.locale.getCountry().isEmpty()) ? this.locale.getCountry() : "";

        this.twoLetterCodeLanguage = lang2;
        this.displayName = displayName(v);
        this.voiceDetails = voiceDetails(v);
        this.flagResIdLanguage = FlagHelper.getFlagResIdForLanguage(lang2);
        this.flagResIdCountry = FlagHelper.getFlagResIdForCountry(country);
    }


    // --- ADD this secondary constructor for synthetic items (e.g., "system") ---
    public VoiceItem(@NonNull String name,
                     @Nullable Voice voice,
                     @Nullable Locale locale,
                     int quality,
                     int latency,
                     boolean requiresNetwork,
                     boolean embedded,
                     @NonNull Set<String> features,
                     @NonNull String twoLetterCodeLanguage,
                     @NonNull String displayName,
                     @NonNull String voiceDetails,
                     int flagResIdLanguage,
                     int flagResIdCountry) {

        this.voice = voice;

        this.name = name;
        this.locale = locale;
        this.quality = quality;
        this.latency = latency;
        this.requiresNetwork = requiresNetwork;
        this.embedded = embedded;
        this.features = (features == null ? Collections.emptySet() : features);

        this.twoLetterCodeLanguage = twoLetterCodeLanguage;
        this.displayName = displayName;
        this.voiceDetails = voiceDetails;
        this.flagResIdLanguage = flagResIdLanguage;
        this.flagResIdCountry = flagResIdCountry;
    }

    // --- ADD this factory to create the "system/default" VoiceItem ---
    public static @Nullable VoiceItem makeSystemDefault(@NonNull TextToSpeech tts) {
        try {
            Locale loc;
            Voice def = tts.getVoice();
            if (def != null)  {
                loc = def.getLocale();
                myLogI("tts.getVoice(): " + def.getName());
            } else {
                loc = tts.getLanguage();
                myLogI("tts.getLanguage(): " + loc.getCountry());
            }
/*
            def = tts.getDefaultVoice();
            if (def != null)  {
                loc = def.getLocale();
                myLogI("tts.getDefaultVoice(): " + def.getName());
            } else {
                loc = tts.getDefaultLanguage();
                myLogI("tts.getDefaultLanguage(): " + loc.getCountry());
            }

 */

            String lang2 = (loc != null && !loc.getLanguage().isEmpty()) ? loc.getLanguage() : "und";
            String prettyLoc = (loc == null) ? "" : prettyLocale(loc);
            myLogI("lang2: " + lang2 + " - prettyLoc: " + prettyLoc);

            String display = prettyLoc.isEmpty()
                    ? "System (default)"
                    : "System (default: " + prettyLoc + ")";
            String details = display; // simple: same text for subtitle
            myLogI("makeSystemDefault: " + display);

            int flagLang = com.driot.bookplayer.helpers.FlagHelper.getFlagResIdForLanguage(lang2);
            int flagCountry = 0;
            if (loc != null && !loc.getCountry().isEmpty()) {
                flagCountry = com.driot.bookplayer.helpers.FlagHelper.getFlagResIdForCountry(loc.getCountry());
            }

            return new VoiceItem(
                    "system",           // <- stable key
                    null,               // <- NO underlying Voice (that’s the point)
                    loc,                // hint for UI
                    0,                  // quality
                    0,                  // latency
                    false,              // requiresNetwork
                    false,              // embedded
                    Collections.emptySet(),
                    lang2,
                    display,
                    details,
                    flagLang,
                    flagCountry
            );
        } catch (Throwable t) {
            // Fallback minimal item
            return new VoiceItem(
                    "system",
                    null,
                    null,
                    0, 0, false, false,
                    Collections.emptySet(),
                    "und",
                    "System (default)",
                    "System (default)",
                    0, 0
            );
        }
    }


    /** Returns a human-readable one-liner for a voice. */
    public static String describeVoice(Voice v) {
        if (v == null) return "Voice{null}";
        String name = v.getName();
        Locale loc  = v.getLocale();
        int q = v.getQuality();
        int l = v.getLatency();
        Set<String> feat = v.getFeatures();
        boolean net = v.isNetworkConnectionRequired();
        String state;
        // Best-effort “state”: embedded vs network
        boolean embedded = (feat != null && feat.contains("embeddedTts"));
        boolean network  = net || (feat != null && feat.contains("networkTts"));
        if (embedded && network) state = "EMBEDDED+NETWORK";
        else if (embedded)       state = "EMBEDDED";
        else if (network)        state = "NETWORK_ONLY";
        else                     state = "UNKNOWN";
        return "Voice{name=" + name +
                ", locale=" + (loc == null ? "null" : loc.toLanguageTag()) +
                ", quality=" + q +
                ", latency=" + l +
                ", state=" + state +
                ", features=" + (feat == null ? "[]" : feat.toString()) +
                "}";
    }

    @NonNull @Override
    public String toString() {
        String tag = (locale == null ? "und" : locale.toLanguageTag());
        String state = embedded ? "EMBEDDED" : (requiresNetwork ? "NETWORK" : "UNKNOWN");
        return name + "  [" + tag + " · q=" + quality + " · l=" + latency + " · " + state + "]";
    }

    private static String voiceDetails(Voice v) {
        boolean offline = v.getFeatures() != null && v.getFeatures().contains("embeddedTts");
        String kind = offline ? "Offline" : (v.isNetworkConnectionRequired() ? "Online" : "Voice");
        String region = (v.getLocale() == null) ? "" : prettyLocale(v.getLocale());
        String base = v.getName();
        return region.isEmpty() ? base + " (" + kind + ")" : region + " – " + base + " (" + kind + ")";
    }

    private static String displayName(Voice v) {
        boolean offline = v.getFeatures() != null && v.getFeatures().contains("embeddedTts");
        String kind = offline ? "Offline" : (v.isNetworkConnectionRequired() ? "Online" : "Voice");
        String region = (v.getLocale() == null) ? "" : prettyLocale(v.getLocale());
        return region.isEmpty() ? kind : region + " – " + kind;
    }
    private static String prettyLocale(Locale loc) {
        try {
            String lang = cap(loc.getDisplayLanguage(loc));
            String c = loc.getCountry();
            if (c.isEmpty()) return lang;
            String region = cap(new Locale("", c).getDisplayCountry(loc));
            return lang + " (" + region + ")";
        } catch (Throwable t) {
            return loc.toLanguageTag();
        }
    }
    private static String cap(String s) {
        return (s == null || s.isEmpty()) ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ======== LOGGING ========
    private static final String TAG = "VoiceItem";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }

}
