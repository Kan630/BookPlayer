package com.driot.bookplayer.activities;

import android.os.Bundle;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.MyFile;
import com.driot.bookplayer.utils.MyFileAdapter;
import com.driot.bookplayer.utils.KanLogger;

import java.io.File;
import java.util.ArrayList;

import static com.driot.bookplayer.utils.Tonio2.getFileInArrayList;
import static com.driot.bookplayer.utils.Utils.recursiveRemove;
import static com.driot.bookplayer.utils.KanLogger.myLog;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 02/12/20
 *
 * Show list of logs, 1 log per day
 *
 */
public class LogListActivity extends LifecycleLoggingActivity {

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
    }

    private void loadRecyclerView() {
        ArrayList<MyFile> myItemArrayList = getFileInArrayList(this, "log");
        recyclerView.setAdapter(new MyFileAdapter(this, myItemArrayList));
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
        recursiveRemove(dir);
        finish();
    }
    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
}
