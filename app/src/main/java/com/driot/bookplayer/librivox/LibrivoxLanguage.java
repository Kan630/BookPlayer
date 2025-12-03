package com.driot.bookplayer.librivox;

/**
 * Simple model for Librivox languages as present in res/raw/librivox_languages.json
 *
 * JSON format expected:
 * [
 *   { "lang_en": "Acehnese", "lang_native_alphabet": "Acehnese", "completed": 1, "in_progress": 0 },
 *   ...
 * ]
 */
public final class LibrivoxLanguage {
    private final String langEn;
    private final String langNativeAlphabet;
    private final int completed;
    private final int inProgress;

    public LibrivoxLanguage(String langEn, String langNativeAlphabet, int completed, int inProgress) {
        this.langEn = langEn;
        this.langNativeAlphabet = langNativeAlphabet;
        this.completed = completed;
        this.inProgress = inProgress;
    }

    public String getLangEn() {
        return langEn;
    }

    public String getLangNativeAlphabet() {
        return langNativeAlphabet;
    }

    public int getCompleted() {
        return completed;
    }

    public int getInProgress() {
        return inProgress;
    }

    @Override
    public String toString() {
        return "LibrivoxLanguage{" +
                "langEn='" + langEn + '\'' +
                ", langNativeAlphabet='" + langNativeAlphabet + '\'' +
                ", completed=" + completed +
                ", inProgress=" + inProgress +
                '}';
    }
}
