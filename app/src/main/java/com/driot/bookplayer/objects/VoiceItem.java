package com.driot.bookplayer.objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.speech.tts.Voice;

import com.driot.bookplayer.helpers.FlagHelper;

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

}
