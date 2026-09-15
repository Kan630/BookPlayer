package com.driot.bookplayer.tts;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.helpers.FlagHelper;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
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

        String lang2 = normalizeToTwoLetterLanguage(this.locale);
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
                myLogD("tts.getVoice(): " + def.getName());
            } else {
                loc = tts.getLanguage();
                myLogW("tts.getLanguage(): " + loc.getCountry());
            }

            String lang = normalizeToTwoLetterLanguage(loc);
            String prettyLoc = (loc == null) ? "" : prettyLocale(loc);

            String display = prettyLoc.isEmpty()
                    ? "System (default)"
                    : "System (default: " + prettyLoc + ")";
            myLogD("makeSystemDefault (spinner entry) : [" + display + "]");

            int flagLang = com.driot.bookplayer.helpers.FlagHelper.getFlagResIdForLanguage(lang);
            int flagCountry = 0;
            if (loc != null && !loc.getCountry().isEmpty()) {
                flagCountry = com.driot.bookplayer.helpers.FlagHelper.getFlagResIdForCountry(loc.getCountry());
            }

            return new VoiceItem(
                    Option.DEFAULT_VOICE,           // <- stable key
                    null,               // <- NO underlying Voice (that’s the point)
                    loc,                // hint for UI
                    0,                  // quality
                    0,                  // latency
                    false,              // requiresNetwork
                    false,              // embedded
                    Collections.emptySet(),
                    lang,
                    display,
                    display,
                    flagLang,
                    flagCountry
            );
        } catch (Throwable t) {
            // Fallback minimal item
            return new VoiceItem(
                    Option.DEFAULT_VOICE,
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

    // Lazily-built ISO 639-2/T (3-letter) -> ISO 639-1 (2-letter) lookup. Needed because
    // some TTS engines (observed: Samsung TTS, e.g. Voice locale "eng"/"fra"/"deu") report
    // Voice#getLocale() with a 3-letter language code instead of Google's 2-letter one, and
    // java.util.Locale#getLanguage() does not convert between the two - without this, every
    // language-based voice match (e.g. ImportBookSingleActivity's book-language preselect)
    // silently fails for such engines because "en".equalsIgnoreCase("eng") is false.
    private static volatile Map<String, String> iso3ToIso2;

    private static Map<String, String> iso3ToIso2Map() {
        Map<String, String> m = iso3ToIso2;
        if (m == null) {
            m = new HashMap<>();
            for (String code2 : Locale.getISOLanguages()) {
                try {
                    String code3 = new Locale(code2).getISO3Language();
                    if (code3 != null && !code3.isEmpty()) {
                        m.put(code3.toLowerCase(Locale.ROOT), code2);
                    }
                } catch (MissingResourceException ignored) {
                }
            }
            iso3ToIso2 = m;
        }
        return m;
    }

    /** Normalizes a Voice's locale language to a 2-letter ISO 639-1 code, converting a
     *  3-letter ISO 639-2/T code if that's what the engine reported. Falls back to
     *  whatever the engine gave us (lower-cased) if it can't be mapped, or "und" if unknown. */
    private static String normalizeToTwoLetterLanguage(@Nullable Locale locale) {
        if (locale == null)
            return "und";
        String lang = locale.getLanguage();
        if (lang == null || lang.isEmpty())
            return "und";
        lang = lang.toLowerCase(Locale.ROOT);
        if (lang.length() == 2)
            return lang;
        String mapped = iso3ToIso2Map().get(lang);
        return (mapped != null) ? mapped : lang;
    }
}
