package com.driot.bookplayer.objects;

import androidx.annotation.NonNull;
import android.speech.tts.Voice;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

public class VoiceItem {
    public final Voice voice;

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
    public final String codeVoice;               // e.g. "en-US-x-sfg#male_1-local"
    public final String displayName;             // pretty label
    public final int flagResIdLanguage;          // your flag for language
    public final int flagResIdCountry;             // optional per-voice icon/flag

    public VoiceItem(Voice v,
                     String twoLetterCodeLanguage,
                     String codeVoice,
                     String displayName,
                     int flagResIdLanguage,
                     int flagResIdVoice) {
        this.voice = v;
        this.name = v.getName();
        this.locale = v.getLocale();
        this.quality = v.getQuality();
        this.latency = v.getLatency();
        this.requiresNetwork = v.isNetworkConnectionRequired();
        Set<String> f = v.getFeatures();
        this.features = (f == null ? Collections.emptySet() : f);
        this.embedded = features.contains("embeddedTts");

        this.twoLetterCodeLanguage = twoLetterCodeLanguage;
        this.codeVoice = codeVoice;
        this.displayName = displayName;
        this.flagResIdLanguage = flagResIdLanguage;
        this.flagResIdCountry = flagResIdVoice;
    }

    @NonNull @Override
    public String toString() {
        String tag = (locale == null ? "und" : locale.toLanguageTag());
        String state = embedded ? "EMBEDDED" : (requiresNetwork ? "NETWORK" : "UNKNOWN");
        return name + "  [" + tag + " · q=" + quality + " · l=" + latency + " · " + state + "]";
    }

    public String getTwoLetterCodeLanguage() { return twoLetterCodeLanguage; }
    public String getCodeVoice() { return codeVoice; }
}
