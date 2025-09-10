package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.objects.MyFile;
import com.driot.bookplayer.adapter.MyFileRVAdapter;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.io.File;
import java.util.ArrayList;

import static com.driot.bookplayer.utils.Tonio2.getFileInArrayList;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 02/12/20
 *
 * Show list of logs, 1 log per day
 *
 */
public class LogListActivity extends LoggingActivity {

    private RecyclerView recyclerView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_list);

        recyclerView = findViewById(R.id.rec);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        Button btnDeleteLogs = findViewById(R.id.btnDeleteLogs);
        btnDeleteLogs.setOnClickListener(view -> btnDeleteLogsClick());

        setTitle(getIntent().getStringExtra("title"));

        String file = getIntent().getStringExtra("file");

        //textOptions = new TextOptions(this);
        loadRecyclerView();

        View secretEntry = findViewById(R.id.viewSecretEntry);
        final long[] taps = new long[3];
        secretEntry.setOnClickListener(v -> {
            System.arraycopy(taps, 1, taps, 0, taps.length - 1);
            taps[taps.length - 1] = System.currentTimeMillis();

            if (taps[0] >= System.currentTimeMillis() - 1000) {
                startActivity(new Intent(this, AdminActivity.class));
            }
        });
    }

    private void loadRecyclerView() {
        ArrayList<MyFile> myItemArrayList = getFileInArrayList(this, "log");
        recyclerView.setAdapter(new MyFileRVAdapter(this, myItemArrayList));
    }

    @Override
    public void onBackPressed() {
        myLog("onBackPressed()");
        super.onBackPressed();
    }

    private void btnDeleteLogsClick() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.AskDelete_popupTitle))
                .setMessage(getString(R.string.DeleteLogs_AskConfirm))
                .setCancelable(false)
                .setPositiveButton("ok", (dialog, which) -> deleteLogs())
                .setNegativeButton("cancel", (dialogInterface, i) -> {})
                .show();
    }
    private void deleteLogs() {
        File dir = new File(this.getFilesDir(), "log");
        FileHelper.recursiveRemove(dir);
        finish();
    }
}
