package com.driot.bookplayer.activities;

import static com.driot.tonylib.KanFiles.copyFile;
import static com.driot.tonylib.KanLogger.myLogE;

import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;

import android.widget.Button;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.DatabaseClient;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;

public class DbBackupActivity extends LifecycleLoggingActivity {

    private Button bt1;
    private Button bt2;
    private final String myDBname = "BookPlayer";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_db_backup);

        bt1 = findViewById(R.id.bt1);
        bt2 = findViewById(R.id.bt2);

        bt1.setOnClickListener(view -> { pr_db_backup(); });
        bt2.setOnClickListener(view -> { pr_db_restore(); });
    }

    private void pr_db_backup() {
        int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if(permission == PackageManager.PERMISSION_GRANTED) {
            DatabaseClient.getInstance(this).getAppDatabase().close();

            File db = getDatabasePath(myDBname);
            File dbShm = new File(db.getParent(), myDBname + "-shm");
            File dbWal = new File(db.getParent(), myDBname + "-wal");

            File db2 = new File("/sdcard/", myDBname);
            File dbShm2 = new File(db2.getParent(), myDBname + "-shm");
            File dbWal2 = new File(db2.getParent(), myDBname + "-wal");

            try {
                copyFile(db, db2);   //FileUtils.copyFile
                copyFile(dbShm, dbShm2);
                copyFile(dbWal, dbWal2);
            } catch (Exception e) {
                myLogE("SAVEDB " + e);
            }
        } else {
            Snackbar.make(bt1, "Please allow access to your storage", Snackbar.LENGTH_LONG)
                    .setAction("Allow", view -> ActivityCompat.requestPermissions(this, new String[] {
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    }, 0)).show();
        }
    }
    private void pr_db_restore() {
        int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        if(permission == PackageManager.PERMISSION_GRANTED) {
            DatabaseClient.getInstance(this).getAppDatabase().close();

            File db = new File("/sdcard/", myDBname);
            File dbShm = new File(db.getParent(), myDBname + "-shm");
            File dbWal = new File(db.getParent(), myDBname + "-wal");

            File db2 = getDatabasePath("my-db");
            File dbShm2 = new File(db2.getParent(), myDBname + "-shm");
            File dbWal2 = new File(db2.getParent(), myDBname + "-wal");

            try {
                copyFile(db, db2);
                copyFile(dbShm, dbShm2);
                copyFile(dbWal, dbWal2);
            } catch (Exception e) {
                myLogE("RESTOREDB " + e);
            }
        } else {
            Snackbar.make(bt1, "Please allow access to your storage", Snackbar.LENGTH_LONG)
                    .setAction("Allow", view -> ActivityCompat.requestPermissions(this, new String[] {
                            Manifest.permission.READ_EXTERNAL_STORAGE
                    }, 0)).show();
        }
    }
}