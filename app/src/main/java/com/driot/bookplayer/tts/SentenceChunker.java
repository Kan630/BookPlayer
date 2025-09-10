        package com.driot.bookplayer.tts;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;

public final class SentenceChunker {
    public static List<String> chunk(String text, int maxLen) {
        List<String> out = new ArrayList<>();
        BreakIterator it = BreakIterator.getSentenceInstance();
        it.setText(text);
        int start = it.first(), end = it.next();
        StringBuilder buf = new StringBuilder();
        while (end != BreakIterator.DONE) {
            String s = text.substring(start, end).trim();
            if (buf.length() + s.length() > maxLen && buf.length() > 0) {
                out.add(buf.toString());
                buf.setLength(0);
            }
            buf.append(s).append(' ');
            start = end; end = it.next();
        }
        if (buf.length() > 0) out.add(buf.toString());
        return out;
    }
}
