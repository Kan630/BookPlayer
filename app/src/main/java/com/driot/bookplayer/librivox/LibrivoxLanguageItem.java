package com.driot.bookplayer.librivox;

import java.io.Serial;
import java.io.Serializable;

public class LibrivoxLanguageItem implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public final String name;        // lang_en
    public final String nativeName;  // lang_native_alphabet
    public final String code2;       // ISO 639-1
    public final String code3;       // ISO 639-2/3
    public final int flagRes;        // drawable
    public final int completed;      // number of completed books

    public LibrivoxLanguageItem(String name, String nativeName, String code2, String code3, int flagRes, int completed) {
        this.name = name;
        this.nativeName = nativeName;
        this.code2 = code2;
        this.code3 = code3;
        this.flagRes = flagRes;
        this.completed = completed;
    }

    @Override
    public String toString() {
        return "LibrivoxLanguageItem{" +
                "name='" + name + '\'' +
                ", nativeName='" + nativeName + '\'' +
                ", code2='" + code2 + '\'' +
                ", code3='" + code3 + '\'' +
                ", flagRes=" + flagRes +
                ", completed=" + completed +
                '}';
    }
}
