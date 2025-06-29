package com.driot.bookplayer.objects;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 02/12/20
 */
public class MyTextChunk {
    private final int id;
    private String text;
    private int charSize;

    public MyTextChunk(int id, String text, int charSize) {
        this.id = id;
        this.text = text;
        this.charSize = charSize;
    }

    public int getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getCharSize() {
        return charSize;
    }

    public void setCharSize(int charSize) {
        this.charSize = charSize;
    }

    public boolean contains(String str) {
        return text.contains(str);
    }
}