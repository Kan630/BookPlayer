package com.driot.bookplayer.activities;

import android.Manifest;
import android.app.Activity;
import android.app.AsyncNotedAppOp;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.sqlite.db.SimpleSQLiteQuery;

import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.ZikFile;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.sql.Date;
import java.sql.Time;
import java.util.List;
import java.util.concurrent.Callable;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static com.driot.bookplayer.utils.Utils.animateView;


public class MainActivity extends LifecycleLoggingActivity {

    static final String TAG = "MainActivity.java";
    private RecyclerView recyclerView;

    private View progressOverlay;
    public static final int DELAY_ANIMATION = 200;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerview_folders);
        FloatingActionButton btn_Add = findViewById(R.id.FAB_Add);
        progressOverlay = findViewById(R.id.progress_overlay);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        btn_Add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                performFileSearch();
            }
        });

        getFolders();

        checkPermissions();
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
                    FoldersAdapter adapter = new FoldersAdapter(MainActivity.this, result);
                    recyclerView.setAdapter(adapter);
                });
    }

    /********************************************************************************
     * ******************************************************************************
     ***        AJOUT NOUVEAU DOSSIER
     ********************************************************************************
     ********************************************************************************
     */

    public void performFileSearch() {
        Intent intent = new Intent(getApplicationContext(),GetRessourceActivity.class);
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

    // PERMISSIONS
    private void checkPermissions() {
        int REQUEST_READ_SD_CARD=1;
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},REQUEST_READ_SD_CARD);
        }
    }

}
