package com.driot.bookplayer.activities;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.objects.MyTextChunk;
import com.driot.bookplayer.adapter.MyTextChunkRVAdapter;
import com.driot.bookplayer.utils.TextOptions;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.log.LoggingActivity;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 21/08/21
 * * imported from Droit Positif (02/12/2020)
 */
public class LogTextActivity extends LoggingActivity {

    private RecyclerView recyclerView;
    private RecyclerView.LayoutManager layoutManager;
    private ArrayList<MyTextChunk> myTextChunkArrayList;

    private String file;
    private String typeStorage;

    private TextOptions textOptions;

    private boolean destroyedByFlip = false;

    public ArrayList<MyTextChunk> getTextFileContentInArrayList(Context c, String typeStorage, String textFileName, String textFileFolder, int charSize) {
        ArrayList<MyTextChunk> arrayList = new ArrayList<>();
        BufferedReader reader;
        InputStream inputStream = null;
        myLogD( "getTextFileContentInArrayList - Opening file -" + textFileName + "- in folder -" + textFileFolder + "- with method -" + typeStorage + "-");
        try {

            //FROM ASSET FOLDER (BookPlayer/app/src/main/assets/)
            if ("asset".equals(typeStorage)) {
                inputStream = c.getAssets().open(textFileName);

            //FROM USER FOLDER (usually data/data/com.driot.bookplayer/files/...)
            } else if ("classic".equals(typeStorage)) {
                File dir = new File(c.getFilesDir(), textFileFolder);
                inputStream = new FileInputStream(new File(dir, textFileName));

            }

            reader = new BufferedReader(
                    new InputStreamReader(inputStream));

            String str;
            int i = 0;
            while ((str = reader.readLine()) != null) {
                arrayList.add(new MyTextChunk(i, str, charSize));
                i++;
            }
            reader.close();
            myLog("getTextFileContentInArrayList - Getting file lines into array...    array dim = nb line = [" + arrayList.size() + "]");
            return arrayList;

        } catch (IOException e) {
            myLogEE(e,"getTextFileContentInArrayList");
        }
        return null;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        destroyedByFlip = true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_text);
        InsetHelper.apply(this);

        recyclerView = findViewById(R.id.recyclerView_biggerText);

        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        file = getIntent().getStringExtra("file");
        setTitle(getIntent().getStringExtra("title") + " - " + file);
        typeStorage = getIntent().getStringExtra("typeStorage");

        textOptions = new TextOptions(this);
        loadRecyclerView();

    }

    private void loadRecyclerView() {
        myTextChunkArrayList = getTextFileContentInArrayList(this, typeStorage, file,"log", textOptions.getCharSize());
        recyclerView.setAdapter(new MyTextChunkRVAdapter(myTextChunkArrayList));
        textOptions.setScrollPosition(this, file, recyclerView);
        recyclerView.scrollToPosition(myTextChunkArrayList.size() - 1);
        myLog("loadRecyclerView()");
    }

    @Override
    protected void onDestroy() {
        try {
            textOptions.saveScrollPosition(this, file, ((LinearLayoutManager) layoutManager).findFirstCompletelyVisibleItemPosition());
        } catch (Exception e) {
            myLogEE(e,"onDestroy() -  saveScrollPosition");
        }
        if (!destroyedByFlip) {
            try {
                textOptions.saveHighlightedText(this, file, ""); // supprime highLightedWord si on quitte et que c'est pas un flip
            } catch (Exception e) {
                myLogEE(e,"onDestroy() -  saveHighlightedText");
            }
        }
        super.onDestroy();
    }

}

