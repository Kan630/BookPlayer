package com.driot.bookplayer;

import android.app.Activity;
import android.os.Bundle;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 30/10/20
 */
public class FolderContentActivity extends Activity {

    private RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_foldercontent);

        recyclerView = findViewById(R.id.recyclerview_zikfiles);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

    }
}
