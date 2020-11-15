package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;


public class MainActivity extends LifecycleLoggingActivity {

    static final String TAG = "MainActivity.java";
    private RecyclerView recyclerView;

    private View progressOverlay;

    private boolean HasBeenProposedToOpenFile;


    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("HasBeenProposedToOpenFile", HasBeenProposedToOpenFile);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        HasBeenProposedToOpenFile = savedInstanceState.getBoolean("HasBeenProposedToOpenFile", false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerview_folders);
        FloatingActionButton btn_Add = findViewById(R.id.FAB_Add);
        progressOverlay = findViewById(R.id.progress_overlay);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        btn_Add.setOnClickListener(view -> performFileSearch());

        getFolders();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        getFolders();
        Log.d("recyclerview","drawing through setAdapter on restart");
    }

    private void getFolders() {
        Observable.fromCallable(() -> {
            List<Folder> folders = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .FolderDao()
                    .getAll();
            return folders;
        })
                //.subscribeOn(Schedulers.io())
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                //.subscribe(new Observer<Boolean>() {
                .subscribe((result) -> {
                    if (result.size()==0) {
                        if (!HasBeenProposedToOpenFile) performFileSearch();
                        HasBeenProposedToOpenFile=true;
                    } else {
                        FoldersAdapter adapter = new FoldersAdapter(MainActivity.this, result);
                        recyclerView.setAdapter(adapter);
                    }
                });
    }

    /********************************************************************************
     * ******************************************************************************
     ***        AJOUT NOUVEAU DOSSIER
     ********************************************************************************
     ********************************************************************************
     */

    public void performFileSearch() {
        Intent intent = new Intent(getApplicationContext(), GetResourceActivity.class);
        startActivity(intent);
    }

    /********************
     *
     * END STUFF
     */

    private void myLog(String str) {
        //String TAG = this.getClass().getName().substring(this.getClass().getName().lastIndexOf(".")+1);
        Log.d("titi " + TAG + " ",str);
        System.out.println(str);
    }

}
