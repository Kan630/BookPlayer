package com.driot.bookplayer.activities;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.DbClean;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.Sql;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.helpers.DeleteHelper;
import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.objects.MyFile;
import com.driot.bookplayer.podcasts.PodcastHelper;
import com.driot.bookplayer.utils.MsgBox;
import com.driot.bookplayer.utils.log.BaseActivity;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminActivity extends BaseActivity {

    private static final int REQ_DELETE_LOGS = 2001;
    private static final int REQ_WRITE_STORAGE_FOR_LOG_FLUSH = 2002;
    private static final String LOG_FLUSH_DOWNLOADS_FOLDER = "BookPlayerLogs";

    private int pendingLogFlushDays = 7;

    private LinearLayout btnContainer;
    private ListView listActivities;

    // Map of label -> Activity class to create buttons dynamically
    private final LinkedHashMap<String, Class<?>> quickButtons = new LinkedHashMap<String, Class<?>>() {
        {
            put("Debug Database Activity", com.driot.bookplayer.activities.DebugDatabaseActivity.class);
            // put("Tts Read Txt Activity",
            // com.driot.bookplayer.activities.TtsReadTxtActivity.class);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);
        InsetHelper.apply(this);

        // Technical log
        MaterialCheckBox chkTechLog = findViewById(R.id.chk_tech_log_file);
        LinearLayout llTechLog = findViewById(R.id.ll_tech_log_file);
        chkTechLog.setChecked(Option.getTechLog());
        llTechLog.setOnClickListener(v -> chkTechLog.toggle());
        chkTechLog.setOnCheckedChangeListener((b, checked) -> Option.setTechLog(checked));

        // Live log viewer
        MaterialCheckBox chkLiveLog = findViewById(R.id.chk_live_log);
        LinearLayout llLiveLog = findViewById(R.id.ll_live_log);
        boolean showLiveLogs = Pref.getShowLiveLogs();
        chkLiveLog.setChecked(showLiveLogs);
        llLiveLog.setOnClickListener(v -> chkLiveLog.toggle());
        chkLiveLog.setOnCheckedChangeListener((b, checked) -> {
            Pref.setShowLiveLogs(checked);
        });

        // Live log height slider
        SeekBar seekBar = findViewById(R.id.seekbar_live_log_height);
        TextView tvHeight = findViewById(R.id.tv_live_log_height);
        int savedHeight = Pref.getLiveLogsSavedHeight();
        int progress = Math.max(0, Math.min(48, savedHeight - 2)); // Convert percentage to slider position (0-48),
                                                                   // range 2-50%
        seekBar.setProgress(progress);
        tvHeight.setText("Live Log Height: " + savedHeight + "%");

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int percentage = 2 + progress; // 0->2%, 48->50%
                tvHeight.setText("Live Log Height: " + percentage + "%");
                if (fromUser) {
                    Pref.setLiveLogsSavedHeight(percentage);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Apply height change when user releases slider
                // recreate();
            }
        });

        // Green button to open LogListActivity
        findViewById(R.id.bOpenLogList).setOnClickListener(v -> {
            startActivity(new Intent(this, LogListActivity.class));
        });

        findViewById(R.id.bDeleteLogs).setOnClickListener(v -> deleteLogsClick());

        findViewById(R.id.bFlushLogsToDownloads).setOnClickListener(v -> flushLogsToDownloadsClick());

        findViewById(R.id.bFlushDiskBooks).setOnClickListener(v -> {
            new Thread(() -> {
                myLogD("-----------------");
                myLogD("-- SD CARD");
                myLogD("-----------------");
                FileHelper.listAllFiles(StorageHelper.getUnzipFolder(this, true));
                myLogD("-----------------");
                myLogD("-- DEVICE");
                myLogD("-----------------");
                FileHelper.listAllFiles(StorageHelper.getUnzipFolder(this, false));
                myLogD("-----------------");
            }).start();
        });

        findViewById(R.id.bFlushDiskImages).setOnClickListener(v -> {
            new Thread(() -> {
                myLogD("-----------------");
                myLogD("-- NORMAL");
                myLogD("-----------------");
                FileHelper.listAllFiles(StorageHelper.getImageFolder(this, false));
                myLogD("-----------------");
            }).start();
        });

        findViewById(R.id.bFlushDiskCachedImages).setOnClickListener(v -> {
            new Thread(() -> {
                myLogD("-----------------");
                myLogD("-- CACHED");
                myLogD("-----------------");
                FileHelper.listAllFiles(StorageHelper.getImageFolder(this, true));
                myLogD("-----------------");
            }).start();
        });


        findViewById(R.id.bImageStuff).setOnClickListener(v -> {
            new Thread(() -> {
                ImageHelper.processPendingImages(this.getApplicationContext(), System.currentTimeMillis() + 24*60*60*1000, "Admin activity");
            }).start();
        });
        findViewById(R.id.bPodcastStuff).setOnClickListener(v -> {
            new Thread(() -> {
                PodcastHelper.doAutoDownloadAndDelete(this.getApplicationContext());
            }).start();
        });
        findViewById(R.id.bDbStuff).setOnClickListener(v -> {
            new Thread(() -> {
                DbClean.doClean(this.getApplicationContext(), true, true, true);
            }).start();
        });



        findViewById(R.id.bFlushSQL).setOnClickListener(v -> {
            new Thread(() -> {
                Sql.log_all_Folders(this);
                Sql.log_all_ZikFiles(this);
            }).start();
        });

        findViewById(R.id.bCrash).setOnClickListener(v -> {
            String crashText = ((EditText) findViewById(R.id.etCrashText)).getText().toString();
            throw new RuntimeException(crashText); // Force a crash
        });

        findViewById(R.id.bDeleteRecentBooks).setOnClickListener(v -> {
            EditText etMinutes = findViewById(R.id.etMinutesAgo);
            String minutesStr = etMinutes.getText().toString();
            if (minutesStr.isEmpty()) {
                Toast.makeText(this, "Please enter number of minutes", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                int minutes = Integer.parseInt(minutesStr);
                if (minutes < 0) {
                    Toast.makeText(this, "Minutes must be >= 0", Toast.LENGTH_SHORT).show();
                    return;
                }
                deleteBooksSinceMinutes(minutes);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number", Toast.LENGTH_SHORT).show();
            }
        });

        // auto stuff
        btnContainer = findViewById(R.id.btnContainer);
        listActivities = findViewById(R.id.listActivities);
        addDynamicButtons();
        populateLaunchableActivitiesList();
    }

    private void deleteBooksSinceMinutes(int minutesAgo) {
        new Thread(() -> {
            try {
                long cutoffTime = System.currentTimeMillis() - (minutesAgo * 60L * 1000L);
                AppDatabase db = AppDatabase.getDatabase(this);
                List<Folder> foldersToDelete = db.folderDao().getFoldersCreatedSince(cutoffTime);

                runOnUiThread(() -> {
                    if (foldersToDelete.isEmpty()) {
                        Toast.makeText(this, "No books found imported in the last " + minutesAgo + " minutes",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Show confirmation dialog
                    new android.app.AlertDialog.Builder(this)
                            .setTitle("Delete Recent Books")
                            .setMessage("Delete " + foldersToDelete.size() + " book(s) imported in the last "
                                    + minutesAgo + " minutes?")
                            .setPositiveButton("Delete", (dialog, which) -> {
                                DeleteHelper.deleteBooksByTimeDelta(this, minutesAgo);
                                Toast.makeText(this, "Deleting " + foldersToDelete.size() + " book(s)...", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    myLogEE(e, "deleteBooksSinceMinutes");
                });
            }
        }).start();
    }

    private void addDynamicButtons() {
        int margin = (int) (8 * getResources().getDisplayMetrics().density);

        for (Map.Entry<String, Class<?>> entry : quickButtons.entrySet()) {
            Button b = new Button(this);
            b.setText(entry.getKey());

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = margin;
            b.setLayoutParams(lp);

            Class<?> target = entry.getValue();
            b.setOnClickListener(v -> launchActivitySafely(target));

            btnContainer.addView(b);
        }
    }

    private void populateLaunchableActivitiesList() {
        PackageManager pm = getPackageManager();

        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        // Limit to this app’s package
        query.setPackage(getPackageName());

        List<ResolveInfo> infos = pm.queryIntentActivities(query, 0);
        List<String> labels = new ArrayList<>();
        List<String> classNames = new ArrayList<>();

        for (ResolveInfo ri : infos) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null)
                continue;

            // Skip AdminActivity itself (optional)
            if (AdminActivity.class.getName().equals(ai.name))
                continue;

            CharSequence label = ri.loadLabel(pm);
            labels.add(label != null ? label.toString() : ai.name);
            classNames.add(ai.name);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, labels);
        listActivities.setAdapter(adapter);

        listActivities.setOnItemClickListener((parent, view, position, id) -> {
            String className = classNames.get(position);
            try {
                Class<?> cls = Class.forName(className);
                startActivity(new Intent(this, cls));
            } catch (ClassNotFoundException e) {
                Toast.makeText(this, "Activity not found", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void launchActivitySafely(Class<?> clazz) {
        try {
            startActivity(new Intent(this, clazz));
        } catch (Exception e) {
            Toast.makeText(this, "Cannot launch " + clazz.getSimpleName(), Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteLogsClick() {
        myLogI("--- user clicks DELETE LOGS ---");
        MsgBox.ask(this,
                getString(R.string.AskDelete_popupTitle),
                getString(R.string.DeleteLogs_AskConfirm),
                null,
                getString(android.R.string.ok),
                getString(android.R.string.cancel),
                REQ_DELETE_LOGS);
    }

    private void deleteLogs() {
        File dir = new File(this.getFilesDir(), "log");
        FileHelper.recursiveRemove(dir);
        recreate();
    }

    private void flushLogsToDownloadsClick() {
        EditText etDays = findViewById(R.id.etLogDaysToFlush);
        String daysStr = etDays.getText().toString().trim();
        int days = 7;
        if (!daysStr.isEmpty()) {
            try {
                days = Math.max(0, Integer.parseInt(daysStr));
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number of days", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        pendingLogFlushDays = days;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQ_WRITE_STORAGE_FOR_LOG_FLUSH);
            return;
        }

        flushLogsToDownloads(pendingLogFlushDays);
    }

    private void flushLogsToDownloads(int days) {
        new Thread(() -> {
            File logDir = new File(getFilesDir(), "log");
            File[] files = logDir.listFiles();
            if (files == null || files.length == 0) {
                runOnUiThread(() -> Toast.makeText(this, "No log files found", Toast.LENGTH_SHORT).show());
                return;
            }

            long cutoffMillis = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

            int copied = 0;
            for (File f : files) {
                if (!f.isFile()) continue;

                MyFile myFile = new MyFile(f.getName());
                Date fileDate;
                try {
                    fileDate = sdf.parse(myFile.getDate());
                } catch (Exception e) {
                    fileDate = null;
                }
                if (fileDate == null || fileDate.getTime() < cutoffMillis) continue;

                if (copyLogFileToDownloads(f)) copied++;
            }

            int finalCopied = copied;
            runOnUiThread(() -> Toast.makeText(this,
                    "Copied " + finalCopied + " log file(s) to Downloads/" + LOG_FLUSH_DOWNLOADS_FOLDER,
                    Toast.LENGTH_LONG).show());
        }).start();
    }

    private boolean copyLogFileToDownloads(File src) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File targetDir = new File(downloadsDir, LOG_FLUSH_DOWNLOADS_FOLDER);
                if (!targetDir.exists() && !targetDir.mkdirs()) {
                    myLogE("flushLogsToDownloads: failed to create " + targetDir);
                    return false;
                }
                try (InputStream in = new FileInputStream(src);
                        OutputStream out = new FileOutputStream(new File(targetDir, src.getName()))) {
                    copyStream(in, out);
                }
            } else {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, src.getName());
                values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                values.put(MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/" + LOG_FLUSH_DOWNLOADS_FOLDER);

                Uri destUri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (destUri == null) {
                    myLogE("flushLogsToDownloads: MediaStore insert returned null for " + src.getName());
                    return false;
                }
                try (InputStream in = new FileInputStream(src);
                        OutputStream out = getContentResolver().openOutputStream(destUri)) {
                    copyStream(in, out);
                }
            }
            return true;
        } catch (IOException e) {
            myLogEE(e, "copyLogFileToDownloads " + src.getName());
            return false;
        }
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_WRITE_STORAGE_FOR_LOG_FLUSH) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                flushLogsToDownloads(pendingLogFlushDays);
            } else {
                Toast.makeText(this, "Permission denied, cannot copy logs to Downloads.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQ_DELETE_LOGS) {
                deleteLogs();
            }
        }
    }

}
