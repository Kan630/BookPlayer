package com.driot.bookplayer.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.LogTextActivity;
import com.driot.bookplayer.adapter.MyTextChunkRVAdapter;
import com.driot.bookplayer.objects.MyTextChunk;
import com.driot.bookplayer.utils.TextOptions;
import com.driot.bookplayer.utils.log.LoggingFragment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Live log viewer fragment that displays recent log entries with auto-refresh.
 * Takes half the screen and updates every 3 seconds.
 */
public class LiveLogFragment extends LoggingFragment {

    private static final int REFRESH_INTERVAL_MS = 1000; // 1 second
    private static final int MAX_LOG_LINES = 500;

    private RecyclerView recyclerView;
    private MyTextChunkRVAdapter adapter;
    private ArrayList<MyTextChunk> logChunks = new ArrayList<>();

    private Handler refreshHandler;
    private Runnable refreshRunnable;
    private String currentLogFile;

    public static LiveLogFragment newInstance() {
        return new LiveLogFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_live_log, container, false);

        recyclerView = view.findViewById(R.id.recyclerViewLiveLogs);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new MyTextChunkRVAdapter(logChunks);
        recyclerView.setAdapter(adapter);

        // Click on fragment opens LogTextActivity
        view.setOnClickListener(v -> openFullLogActivity());

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize refresh handler
        refreshHandler = new Handler(Looper.getMainLooper());
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                loadLatestLogs();
                refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        // Start auto-refresh
        loadLatestLogs();
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Stop auto-refresh to save resources
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
    }

    private void loadLatestLogs() {
        if (getContext() == null)
            return;

        try {
            // Get current log file
            currentLogFile = getCurrentLogFileName();

            // Read logs
            ArrayList<MyTextChunk> newLogs = getTextFileContentInArrayList(
                    getContext(),
                    "classic",
                    currentLogFile,
                    "log",
                    10 // smaller text size for compact view
            );

            if (newLogs != null && !newLogs.isEmpty()) {
                // Reverse to show newest first
                Collections.reverse(newLogs);

                // Limit to last 100 lines
                if (newLogs.size() > MAX_LOG_LINES) {
                    newLogs = new ArrayList<>(newLogs.subList(0, MAX_LOG_LINES));
                }

                logChunks.clear();
                logChunks.addAll(newLogs);
                adapter.updateData(logChunks);
            }
        } catch (Exception e) {
            myLogEE(e, "loadLatestLogs");
        }
    }

    private String getCurrentLogFileName() {
        // Get the most recent log file from the log directory
        try {
            File logDir = new File(requireContext().getFilesDir(), "log");
            if (!logDir.exists())
                return "";

            File[] files = logDir.listFiles();
            if (files == null || files.length == 0)
                return "";

            // Find most recent file
            File newest = files[0];
            for (File file : files) {
                if (file.lastModified() > newest.lastModified()) {
                    newest = file;
                }
            }

            return newest.getName();
        } catch (Exception e) {
            myLogEE(e, "getCurrentLogFileName");
            return "";
        }
    }

    private ArrayList<MyTextChunk> getTextFileContentInArrayList(Context c, String typeStorage,
            String textFileName,
            String textFileFolder,
            int charSize) {
        ArrayList<MyTextChunk> myTextChunkArrayList = new ArrayList<>();
        String line;
        int lineNumber = 0;

        try {
            File file = new File(c.getFilesDir(), textFileFolder + "/" + textFileName);

            if (!file.exists()) {
                return myTextChunkArrayList;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            while ((line = br.readLine()) != null) {
                myTextChunkArrayList.add(new MyTextChunk(lineNumber, line, charSize));
                lineNumber++;
            }
            br.close();
        } catch (Exception e) {
            myLogEE(e, "getTextFileContentInArrayList");
        }

        return myTextChunkArrayList;
    }

    private void openFullLogActivity() {
        if (getContext() == null || currentLogFile == null || currentLogFile.isEmpty()) {
            return;
        }

        Intent intent = new Intent(getContext(), LogTextActivity.class);
        intent.putExtra("typeStorage", "classic");
        intent.putExtra("file", currentLogFile);
        intent.putExtra("title", "Log");
        startActivity(intent);
    }
}
