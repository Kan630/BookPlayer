package com.driot.bookplayer.utils;

import android.content.Context;

import androidx.core.content.res.ResourcesCompat;

import java.util.Arrays;

import com.driot.bookplayer.R;

import static com.driot.bookplayer.utils.KanLogger.myLogE;
import static com.driot.bookplayer.utils.TonioCommonStuff.deleteExtension;


/**
 * created by Antoine Driot -- antoine.driot.com -- on 03/12/20
 */
public class MyJuri {

    private static int color_Juri_CE;
    private static int color_Juri_Cass;
    private static int color_Juri_CC;
    private static int color_Juri_CJUE;
    private static int color_Juri_CEDH;
    private static int color_Juri_CIJ;
    private static int color_Juri_TriCon;
    private static int color_Juri_default;

    private final String fileName;
    private final String date;
    private final String title;
    private final String autorite;
    private final String matiere;
    //private final String extention;

    private String text;

    public MyJuri(Context c, String fileName) {
        color_Juri_CE = ResourcesCompat.getColor(c.getResources(), R.color.color_Juri_CE, null); //without theme
        color_Juri_Cass = ResourcesCompat.getColor(c.getResources(), R.color.color_Juri_Cass, null); //without theme
        color_Juri_CC = ResourcesCompat.getColor(c.getResources(), R.color.color_Juri_CC, null); //without theme
        color_Juri_CJUE = ResourcesCompat.getColor(c.getResources(), R.color.color_Juri_CJUE, null); //without theme
        color_Juri_CEDH = ResourcesCompat.getColor(c.getResources(), R.color.color_Juri_CEDH, null); //without theme
        color_Juri_CIJ = ResourcesCompat.getColor(c.getResources(), R.color.color_Juri_CIJ, null); //without theme
        color_Juri_TriCon = ResourcesCompat.getColor(c.getResources(), R.color.color_Juri_TriCon, null); //without theme
        color_Juri_default = ResourcesCompat.getColor(c.getResources(), R.color.color_Juri_default, null); //without theme

        this.fileName = fileName;
        String str;
        String[] separated = new String[4];
        Arrays.fill(separated, "");
        try {
            str = deleteExtension(fileName);
            separated = str.split("-");
        } catch (Exception e) {
            myLogE("error constructeur MyJuri :" + e.getMessage());
        }
        this.date = separated[0];
        this.title = separated[1];
        this.autorite = separated[2];
        if (separated.length > 3) {
            this.matiere = separated[3];
        } else {
            this.matiere = "";
        }
        //this.extention = getExtention(fileName);
        //myLog("added :" + fileName + "  =>  " + date + " / " + title + " / " + autorite);
    }

    public String getDate() {
        return date;
    }

    public String getTitle() {
        return title;
    }

    public String getText() {
        return text;
    }

    public String getAutorite() {
        return autorite;
    }

    public String getFileName() {
        return fileName;
    }

    public String getMatiere() {
        return matiere;
    }

    //public String getExtention() {
        //return extention;
    //}

    public int getBackColor() {
        //int bc = c.getColor(R.color.Juri_default);
        int bc;

        if (this.autorite.startsWith("CE")) {
            bc = color_Juri_CE;
        } else if (this.autorite.startsWith("CC")) {
            bc = color_Juri_CC;
        } else if (this.autorite.startsWith("Cass")) {
            bc = color_Juri_Cass;
        } else if (this.autorite.startsWith("CJUE")) {
            bc = color_Juri_CJUE;
        } else if (this.autorite.startsWith("CEDH")) {
            bc = color_Juri_CEDH;
        } else if (this.autorite.startsWith("TriCon")) {
            bc = color_Juri_CIJ;
        } else if (this.autorite.startsWith("CIJ")) {
            bc = color_Juri_TriCon;
        } else {
            bc = color_Juri_default;
        }
        return bc;
    }


}
