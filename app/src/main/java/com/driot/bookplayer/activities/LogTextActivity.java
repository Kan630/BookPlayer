package com.driot.bookplayer.activities;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;

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
    private ArrayList<MyTextChunk> originalTextChunkArrayList;
    private MyTextChunkRVAdapter adapter;

    private String file;
    private String typeStorage;

    private TextOptions textOptions;

    private boolean destroyedByFlip = false;

    // Filter controls
    private EditText etSearch;
    private SwitchMaterial switchWAR;
    private SwitchMaterial switchERR;

    public ArrayList<MyTextChunk> getTextFileContentInArrayList(Context c, String typeStorage, String textFileName,
            String textFileFolder, int charSize) {
        ArrayList<MyTextChunk> arrayList = new ArrayList<>();
        BufferedReader reader;
        InputStream inputStream = null;
        myLogD("getTextFileContentInArrayList - Opening file -" + textFileName + "- in folder -" + textFileFolder
                + "- with method -" + typeStorage + "-");
        try {

            // FROM ASSET FOLDER (BookPlayer/app/src/main/assets/)
            if ("asset".equals(typeStorage)) {
                inputStream = c.getAssets().open(textFileName);

                // FROM USER FOLDER (usually data/data/com.driot.bookplayer/files/...)
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
            myLog("getTextFileContentInArrayList - Getting file lines into array...    array dim = nb line = ["
                    + arrayList.size() + "]");
            return arrayList;

        } catch (IOException e) {
            myLogEE(e, "getTextFileContentInArrayList");
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
        // Force landscape orientation
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.activity_log_text);
        InsetHelper.apply(this);

        recyclerView = findViewById(R.id.recyclerView_biggerText);

        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        file = getIntent().getStringExtra("file");
        setTitle(getIntent().getStringExtra("title") + " - " + file);
        typeStorage = getIntent().getStringExtra("typeStorage");

        // Initialize filter controls
        etSearch = findViewById(R.id.etSearch);
        switchWAR = findViewById(R.id.switchWAR);
        switchERR = findViewById(R.id.switchERR);

        textOptions = new TextOptions(this);
        loadRecyclerView();

        setupFilters();
    }

    private void loadRecyclerView() {
        myTextChunkArrayList = getTextFileContentInArrayList(this, typeStorage, file, "log", textOptions.getCharSize());
        originalTextChunkArrayList = new ArrayList<>(myTextChunkArrayList);
        adapter = new MyTextChunkRVAdapter(myTextChunkArrayList);
        recyclerView.setAdapter(adapter);
        textOptions.setScrollPosition(this, file, recyclerView);
        recyclerView.scrollToPosition(myTextChunkArrayList.size() - 1);
        myLog("loadRecyclerView()");
    }

    private void setupFilters() {
        // Search field text watcher
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterList();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // WAR switch listener
        switchWAR.setOnCheckedChangeListener((buttonView, isChecked) -> filterList());

        // ERR switch listener
        switchERR.setOnCheckedChangeListener((buttonView, isChecked) -> filterList());
    }

    private void filterList() {
        String searchText = etSearch.getText().toString().toLowerCase();
        boolean filterWAR = switchWAR.isChecked();
        boolean filterERR = switchERR.isChecked();

        ArrayList<MyTextChunk> filteredList = new ArrayList<>();

        for (MyTextChunk chunk : originalTextChunkArrayList) {
            String text = chunk.getText();
            boolean matches = true;

            // Apply search filter (case-insensitive)
            if (!searchText.isEmpty() && !text.toLowerCase().contains(searchText)) {
                matches = false;
            }

            // Apply WAR/ERR filters (OR logic - show if contains WAR OR ERR when enabled)
            if (filterWAR || filterERR) {
                boolean hasWAR = filterWAR && text.contains("WAR");
                boolean hasERR = filterERR && text.contains("ERR");
                if (!hasWAR && !hasERR) {
                    matches = false;
                }
            }

            if (matches) {
                filteredList.add(chunk);
            }
        }

        myTextChunkArrayList = filteredList;
        adapter.updateData(filteredList);
    }

    @Override
    protected void onDestroy() {
        try {
            textOptions.saveScrollPosition(this, file,
                    ((LinearLayoutManager) layoutManager).findFirstCompletelyVisibleItemPosition());
        } catch (Exception e) {
            myLogEE(e, "onDestroy() -  saveScrollPosition");
        }
        if (!destroyedByFlip) {
            try {
                textOptions.saveHighlightedText(this, file, ""); // supprime highLightedWord si on quitte et que c'est
                                                                 // pas un flip
            } catch (Exception e) {
                myLogEE(e, "onDestroy() -  saveHighlightedText");
            }
        }
        super.onDestroy();
    }

}
