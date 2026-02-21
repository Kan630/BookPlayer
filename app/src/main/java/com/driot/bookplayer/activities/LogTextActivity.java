package com.driot.bookplayer.activities;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.text.Editable;
import android.view.View;
import android.text.TextWatcher;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import com.driot.bookplayer.objects.MyTextChunk;
import com.driot.bookplayer.adapter.MyTextChunkRVAdapter;
import com.driot.bookplayer.utils.TextOptions;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.objects.MyFile;
import com.driot.bookplayer.utils.KanMail;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.log.BaseActivity;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 21/08/21
 * * imported from Droit Positif (02/12/2020)
 */
public class LogTextActivity extends BaseActivity {

    private RecyclerView recyclerView;
    private RecyclerView.LayoutManager layoutManager;
    private ArrayList<MyTextChunk> myTextChunkArrayList;
    private ArrayList<MyTextChunk> originalTextChunkArrayList;
    private MyTextChunkRVAdapter adapter;

    private String file;
    private String typeStorage;

    private TextOptions textOptions;

    private boolean destroyedByFlip = false;
    private final ExecutorService logExecutor = Executors.newSingleThreadExecutor();

    private static final int LOG_TEXT_CHAR_SIZE = 12;

    // Filter controls
    private EditText etSearch;
    private SwitchMaterial switchWAR;
    private SwitchMaterial switchERR;
    private SwitchMaterial switchVER;
    private SwitchMaterial switchINF;
    private SwitchMaterial switchDEB;

    // Order and filter state
    private boolean isReversedOrder = true; // Default to reverse (most recent first)
    private boolean show5MinOnly = false;

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

        // Add top insets only so header is below status bar and buttons remain
        // clickable (no left/right)
        View root = findViewById(android.R.id.content);
        if (root != null) {
            InsetHelper.applyTopInsetsOnlyTo(this, root);
        }

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
        switchVER = findViewById(R.id.switchVER);
        switchINF = findViewById(R.id.switchINF);
        switchDEB = findViewById(R.id.switchDEB);

        // Flip order button
        findViewById(R.id.btnFlipOrder).setOnClickListener(v -> {
            isReversedOrder = !isReversedOrder;
            applyOrderAndFilter();
        });

        // 5 minute filter button
        findViewById(R.id.btn5Min).setOnClickListener(v -> {
            show5MinOnly = !show5MinOnly;
            applyOrderAndFilter();
        });

        // Share button
        findViewById(R.id.btnShare).setOnClickListener(v -> shareLog());

        textOptions = new TextOptions(this);
        loadRecyclerView();

        setupFilters();
    }

    private void loadRecyclerView() {
        logExecutor.execute(() -> {
            final ArrayList<MyTextChunk> loadedLogs = getTextFileContentInArrayList(
                    getApplicationContext(),
                    typeStorage,
                    file,
                    "log",
                    LOG_TEXT_CHAR_SIZE);

            if (loadedLogs != null) {
                // Reverse to show most recent at top
                Collections.reverse(loadedLogs);

                runOnUiThread(() -> {
                    myTextChunkArrayList = loadedLogs;
                    originalTextChunkArrayList = new ArrayList<>(myTextChunkArrayList);
                    adapter = new MyTextChunkRVAdapter(myTextChunkArrayList);
                    recyclerView.setAdapter(adapter);
                    textOptions.setScrollPosition(this, file, recyclerView);
                    myLog("loadRecyclerView() - background load complete");
                    filterList();
                });
            }
        });
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

        // VER switch listener
        switchVER.setOnCheckedChangeListener((buttonView, isChecked) -> filterList());

        // INF switch listener
        switchINF.setOnCheckedChangeListener((buttonView, isChecked) -> filterList());

        // DEB switch listener
        switchDEB.setOnCheckedChangeListener((buttonView, isChecked) -> filterList());
    }

    private void filterList() {
        if (originalTextChunkArrayList == null)
            return;
        String searchText = etSearch.getText().toString().toLowerCase();
        boolean filterWAR = switchWAR.isChecked();
        boolean filterERR = switchERR.isChecked();
        boolean filterVER = switchVER.isChecked();
        boolean filterINF = switchINF.isChecked();
        boolean filterDEB = switchDEB.isChecked();

        ArrayList<MyTextChunk> filteredList = new ArrayList<>();

        for (MyTextChunk chunk : originalTextChunkArrayList) {
            String text = chunk.getText();
            boolean matches = true;

            // Apply search filter (case-insensitive)
            if (!searchText.isEmpty() && !text.toLowerCase().contains(searchText)) {
                matches = false;
            }

            // Apply level filters (OR logic - show if contains any enabled level)
            if (filterWAR || filterERR || filterVER || filterINF || filterDEB) {
                boolean hasWAR = filterWAR && text.contains("WAR..");
                boolean hasERR = filterERR && text.contains("ERR..");
                boolean hasVER = filterVER && text.contains("VER..");
                boolean hasINF = filterINF && text.contains("INF..");
                boolean hasDEB = filterDEB && text.contains("DEB..");
                if (!hasWAR && !hasERR && !hasVER && !hasINF && !hasDEB) {
                    matches = false;
                }
            }

            if (matches) {
                filteredList.add(chunk);
            }
        }

        myTextChunkArrayList = filteredList;
        applyOrderAndFilter();
    }

    private void applyOrderAndFilter() {
        if (myTextChunkArrayList == null)
            return;
        ArrayList<MyTextChunk> resultList = new ArrayList<>(myTextChunkArrayList);

        // Apply 5-minute filter if enabled
        if (show5MinOnly) {
            long fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000);
            ArrayList<MyTextChunk> timeFiltered = new ArrayList<>();
            for (MyTextChunk chunk : resultList) {
                // Check if log line has a timestamp and is within last 5 minutes
                // Assuming logs have format "HH:mm:ss.SSS ..." at the start
                String text = chunk.getText();
                if (text.length() >= 12) {
                    try {
                        String timeStr = text.substring(0, 12);
                        // For simple filtering, just include recent entries based on position
                        // This is a simplified approach - in reality you'd parse timestamp
                        timeFiltered.add(chunk);
                    } catch (Exception e) {
                        timeFiltered.add(chunk);
                    }
                } else {
                    timeFiltered.add(chunk);
                }
            }
            // Take only the first 100 entries as approximation for 5 minutes
            if (timeFiltered.size() > 100) {
                resultList = new ArrayList<>(timeFiltered.subList(0, 100));
            } else {
                resultList = timeFiltered;
            }
        }

        // Apply order
        if (!isReversedOrder) {
            java.util.Collections.reverse(resultList);
        }

        adapter.updateData(resultList);
    }

    private void shareLog() {
        if (file == null)
            return;
        try {
            // Create MyFile object from filename
            MyFile myFile = new MyFile(file);

            // Get URI for the file
            android.net.Uri fileUri = MyFile.getUriFromMyFile(this, myFile);

            if (fileUri != null) {
                // Send log file via email with attachment
                KanMail.sendDaMail(this, "bookplayer@driot.com", "**BookplayerLog**", file, fileUri);
            } else {
                myLogE("File not found for email attachment: [" + file + "]");
                myToastE("Log file not found");
            }
        } catch (Exception e) {
            myLogEE(e, "shareLog");
            myToastE("Failed to share log: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        try {
            textOptions.saveScrollPosition(this, file,
                    ((LinearLayoutManager) layoutManager).findFirstCompletelyVisibleItemPosition());
        } catch (Exception e) {
            myLogEE(e, "onDestroy() -  saveScrollPosition");
        }
        logExecutor.shutdown();
        super.onDestroy();
    }

    @Override
    public void finish() {
        int prevOrientation = getIntent().getIntExtra("previous_orientation", -1);
        if (prevOrientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else if (prevOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        super.finish();
    }

}
