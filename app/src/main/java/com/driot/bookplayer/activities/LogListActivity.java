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

import java.io.File;
import java.util.ArrayList;

import static com.driot.bookplayer.utils.Tonio2.getFileInArrayList;
import static com.driot.bookplayer.utils.Utils.recursiveRemove;
import static com.driot.tonylib.KanLogger.myLog;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 02/12/20
 *
 * Show list of logs, 1 log per day
 *
 */
public class LogListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private RecyclerView.LayoutManager layoutManager;
    private ArrayList<MyFile> myItemArrayList;

    private String file;
    private Button btnDeleteLogs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_list);

        recyclerView = findViewById(R.id.rec);

        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        btnDeleteLogs = findViewById(R.id.btnDeleteLogs);
        btnDeleteLogs.setOnClickListener(view -> btnDeleteLogsClick());

        //Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        //setSupportActionBar(myToolbar);

        setTitle(getIntent().getStringExtra("title"));

        file = getIntent().getStringExtra("file");

        //textOptions = new TextOptions(this);
        loadRecyclerView();
    }

    private void loadRecyclerView() {
        myItemArrayList = getFileInArrayList(this);
        recyclerView.setAdapter(new MyFileAdapter(this, myItemArrayList));
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        myLog("LogListActivity back press");
    }
/*
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.action_bar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_tailleCharPlus:
                textOptions.charSizePlus(this);
                loadRecyclerView();
                return true;

            case R.id.action_tailleCharMoins:
                textOptions.charSizeMoins(this);
                loadRecyclerView();
                return true;

            case R.id.action_search:
                myToast(this, "! Not yet implemented !");
                return true;

            default:
                myLog("default : " + item.toString());
                return super.onOptionsItemSelected(item);

        }
    }
*/

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
    }}
