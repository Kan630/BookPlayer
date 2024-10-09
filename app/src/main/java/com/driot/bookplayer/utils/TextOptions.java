package com.driot.bookplayer.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Layout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import static android.content.Context.MODE_PRIVATE;
import static com.driot.bookplayer.utils.KanLogger.myLog;
import static com.driot.bookplayer.utils.KanLogger.myLogE;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 02/12/20
 */
public class TextOptions {

    public static final String SHARED_PREFERENCE_CHAR_SIZE = "SHARED_PREFERENCE_CHAR_SIZE";
    public static final String SHARED_PREFERENCE_POS_SCROLLVIEW = "SHARED_PREFERENCE_POSITION_SCROLLVIEW";
    public static final String SHARED_PREFERENCE_HIGHLIGHTED_TEXT = "SHARED_PREFERENCE_HIGHLIGHTED_TEXT";

    public static final boolean HIGHLIGHT_AFTER_MARK = true;
    public static final String [] LIST_MARK =  {". ", ": ", "; ", "/ "};
    public static final String HIGHLIGHT_CODE_STRONG_START = "<b><span style='background-color:yellow'>";
    public static final String HIGHLIGHT_CODE_STRONG_END = "</span></b>";
    public static final String HIGHLIGHT_CODE_MILD_START = "<b>";
    public static final String HIGHLIGHT_CODE_MILD_END = "</b>";
    public static final String [] LIST_WORD_TO_HIGHTLIGHT_STRONG =  {
            "Cons.", "- Cons.", "Considérant", "Considerant"
            , "Vu "
            , "Attendu "
            , "Au motif ", "Aux motifs ", "Et aux motifs "
            , "Selon le moyen", "Selon les moyens"
            , "Mais attendu"
            , "Sur le motif ", "Sur les motifs"
            , "Selon le motif ", "Selon les motifs"
            , "Par ce motif", "Par ces motifs"
            , "Sur le moyen", "Sur les moyens"
            , "Sur le. pourvoi", "Sur le pourvoi"
            , "Decide", "Décide"
    };
    public static final String [] LIST_WORD_TO_HIGHTLIGHT_MILD = {
            "Alors"
            , "Que", "Qu'"
            , "Résumé", "Analyse", "Article"
            , "Moyen de cassation", "La Cour"
            , "Remet", "Casse et annule", "Rejette", "Condamne"
    };
    public String HIGHLIGHT_CODE_START, HIGHLIGHT_CODE_END;

    public static final int CHAR_SIZE_DEFAULT = 14;
    public static final int CHAR_SIZE_INCREMENT = 2;
    public static final int CHAR_SIZE_MINI = 6;
    public static final int CHAR_SIZE_MAX = 24;

    private int charSize;

    // constructeur
    public TextOptions(Context c) {
        setCharSize(c);
    }

    public int getCharSize() {
        return charSize;
    }

    private void setCharSize(Context c) {
        SharedPreferences prefs = c.getSharedPreferences(SHARED_PREFERENCE_CHAR_SIZE, MODE_PRIVATE);
        charSize = prefs.getInt("charSize", CHAR_SIZE_DEFAULT);
        if (charSize < CHAR_SIZE_MINI || charSize > CHAR_SIZE_MAX) charSize = CHAR_SIZE_DEFAULT;
    }

    public void charSizePlus(Context c) {
        if (charSize + CHAR_SIZE_INCREMENT <= CHAR_SIZE_MAX) {
            charSize = charSize + CHAR_SIZE_INCREMENT;
            saveCharSize(c);
        }
    }

    public void charSizePlus(Context c, TextView tv) {
        if (charSize + CHAR_SIZE_INCREMENT <= CHAR_SIZE_MAX) {
            charSize = charSize + CHAR_SIZE_INCREMENT;
            saveCharSize(c);
            tv.setTextSize(charSize);
        }
    }

    public void charSizeMoins(Context c) {
        if (charSize - CHAR_SIZE_INCREMENT >= CHAR_SIZE_MINI) {
            charSize = charSize - CHAR_SIZE_INCREMENT;
            saveCharSize(c);
        }
    }

    public void charSizeMoins(Context c, TextView tv) {
        if (charSize - CHAR_SIZE_INCREMENT >= CHAR_SIZE_MINI) {
            charSize = charSize - CHAR_SIZE_INCREMENT;
            saveCharSize(c);
            tv.setTextSize(charSize);
        }
    }

    private void saveCharSize(Context c) {
        SharedPreferences.Editor editor = c.getSharedPreferences(SHARED_PREFERENCE_CHAR_SIZE, MODE_PRIVATE).edit();
        editor.putInt("charSize", charSize).apply();
        myLog("savingCharSize :" + charSize);
    }

    /**
     * TextView
     */

    public void saveScrollPosition(Context c, String file, ScrollView scrollView, TextView textView) {
        SharedPreferences.Editor editor = c.getSharedPreferences(SHARED_PREFERENCE_POS_SCROLLVIEW, MODE_PRIVATE).edit();
        editor.putFloat(file, getScrollSpot(scrollView, textView)).apply();
    }

    public void setScrollPosition(Context c, String file, ScrollView scrollView, TextView textView) {
        try {
            SharedPreferences prefs = c.getSharedPreferences(SHARED_PREFERENCE_POS_SCROLLVIEW, MODE_PRIVATE);
            float spot = prefs.getFloat(file, 0.0f);
            setScrollSpot(spot, scrollView, textView);
        } catch (Exception e) {
            myLogE("sharedPref setScrollPosition scrollview - " + e.getMessage());
        }
    }


    /**
     * @return an encoded float, where the integer portion is the offset of the
     * first character of the first fully visible line, and the decimal
     * portion is the percentage of a line that is visible above it.
     */
    private float getScrollSpot(ScrollView scrollView, TextView textView) {
        int y = scrollView.getScrollY();
        Layout layout = textView.getLayout();
        int topPadding = -layout.getTopPadding();
        if (y <= topPadding) {
            return (float) (topPadding - y) / textView.getLineHeight();
        }

        int line = layout.getLineForVertical(y - 1) + 1;
        int offset = layout.getLineStart(line);
        int above = layout.getLineTop(line) - y;
        return offset + (float) above / textView.getLineHeight();
    }

    private void setScrollSpot(float spot, ScrollView scrollView, TextView textView) {
        int offset = (int) spot;
        int above = (int) ((spot - offset) * textView.getLineHeight());
        Layout layout = textView.getLayout();
        int line = layout.getLineForOffset(offset);
        int y = (line == 0 ? -layout.getTopPadding() : layout.getLineTop(line)) - above;
        scrollView.scrollTo(0, y);
    }


    public void saveHighlightedText(Context c, String file, String highLightedWord) {
        SharedPreferences.Editor editor = c.getSharedPreferences(SHARED_PREFERENCE_HIGHLIGHTED_TEXT, MODE_PRIVATE).edit();
        editor.putString(file, highLightedWord).apply();
    }

    public String getHighlightedText(Context c, String file) {
        SharedPreferences prefs = c.getSharedPreferences(SHARED_PREFERENCE_HIGHLIGHTED_TEXT, MODE_PRIVATE);
        return prefs.getString(file, "");
    }


    /**
     * RecyclerView
     */

    public void saveScrollPosition(Context c, String file, int posRecyclerView) {
        SharedPreferences.Editor editor = c.getSharedPreferences(SHARED_PREFERENCE_POS_SCROLLVIEW, MODE_PRIVATE).edit();
        editor.putFloat(file, (float) posRecyclerView).apply();
        //myLog("saving " + posRecyclerView);
    }

    public void setScrollPosition(Context c, String file, RecyclerView recyclerView) {
        try {
            SharedPreferences prefs = c.getSharedPreferences(SHARED_PREFERENCE_POS_SCROLLVIEW, MODE_PRIVATE);
            float spot = prefs.getFloat(file, 0.0f);
            //myLog("scroll to " + spot);
            recyclerView.scrollToPosition((int) spot);
        } catch (Exception e) {
            myLogE("sharedPref setScrollPosition recyclerview - " + e.getMessage());
        }
    }

    public String highLightJuri(String str) {
        StringBuilder writer = new StringBuilder();
        try (
                BufferedReader reader = new BufferedReader(new StringReader(str))
        ) {
            String line = reader.readLine();
            while (line != null) {
                //result.add(line);

                for (String word : LIST_WORD_TO_HIGHTLIGHT_STRONG) {
                    HIGHLIGHT_CODE_START = HIGHLIGHT_CODE_STRONG_START;
                    HIGHLIGHT_CODE_END = HIGHLIGHT_CODE_STRONG_END;
                    line = highLightWord(line, word);
                    line = highLightWord(line, word.toLowerCase());
                    line = highLightWord(line, word.toUpperCase());
                }
                for (String word : LIST_WORD_TO_HIGHTLIGHT_MILD) {
                    HIGHLIGHT_CODE_START = HIGHLIGHT_CODE_MILD_START;
                    HIGHLIGHT_CODE_END = HIGHLIGHT_CODE_MILD_END;
                    line = highLightWord(line, word);
                    line = highLightWord(line, word.toLowerCase());
                    line = highLightWord(line, word.toUpperCase());
                }
                //writer.append(line);
                writer.append(line).append("<br>");

                line = reader.readLine();
            }
        } catch (IOException exc) {
            // quit
        }
        return writer.toString();
    }

    public String highLightWord(String line, String word) {
        String newline=line;
        int lenWord = word.length();
        if (line.length()>=lenWord) {

            if (line.startsWith(word)) {
                newline = highLightStartingWord(newline, word);
            }

            if (HIGHLIGHT_AFTER_MARK) {
                for (String mark : LIST_MARK) {
                    line = newline;
                    newline = hightLightRecurs(line, word, mark);
                }
            }

        }
        return newline;
    }

    public String hightLightRecurs(String line, String word, String search) {
        String str;
        String s2;
        int pos;
        if (line.contains(search + word)) {
            pos = line.indexOf(search + word);
            s2  = line.substring(pos + search.length());
            str = line.substring(0,pos + search.length()) + highLightStartingWord(s2, word);
            str = hightLightRecurs(str, word, search);
        } else {
            str = line;
        }
        return str;
    }

    public String highLightStartingWord(String line, String word) {
        String newline=line;
        if (word.endsWith("'")) {
            int posBlanc = line.indexOf(" ");
            if (posBlanc>0) {
                word = line.substring(0,posBlanc);
            }
        }
        int lenWord = word.length();
        if (line.length()>=lenWord) {
            newline = HIGHLIGHT_CODE_START + line.substring(0,lenWord) + HIGHLIGHT_CODE_END + line.substring(lenWord);
        }
        return newline;
    }

}
