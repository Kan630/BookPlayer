package com.driot.bookplayer.activities;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.Sql;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.utils.log.LoggingActivity;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AdminActivity extends LoggingActivity {

    private LinearLayout btnContainer;
    private ListView listActivities;

    // Map of label -> Activity class to create buttons dynamically
    private final LinkedHashMap<String, Class<?>> quickButtons = new LinkedHashMap<String, Class<?>>() {{
        put("Debug Database Activity", com.driot.bookplayer.activities.DebugDatabaseActivity.class);
        //put("Tts Read Txt Activity", com.driot.bookplayer.activities.TtsReadTxtActivity.class);
    }};

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

        findViewById(R.id.bCarConnect).setOnClickListener(v -> myToast("nothing"));


//auto stuff
        btnContainer = findViewById(R.id.btnContainer);
        listActivities = findViewById(R.id.listActivities);
        addDynamicButtons();
        populateLaunchableActivitiesList();
    }

    private void addDynamicButtons() {
        int margin = (int) (8 * getResources().getDisplayMetrics().density);

        for (Map.Entry<String, Class<?>> entry : quickButtons.entrySet()) {
            Button b = new Button(this);
            b.setText(entry.getKey());

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
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
            if (ai == null) continue;

            // Skip AdminActivity itself (optional)
            if (AdminActivity.class.getName().equals(ai.name)) continue;

            CharSequence label = ri.loadLabel(pm);
            labels.add(label != null ? label.toString() : ai.name);
            classNames.add(ai.name);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, labels
        );
        listActivities.setAdapter(adapter);

        listActivities.setOnItemClickListener((parent, view, position, id) -> {
            String fqcn = classNames.get(position); // fully qualified class name
            try {
                Intent i = new Intent();
                i.setComponent(new ComponentName(getPackageName(), fqcn));
                startActivity(i);
            } catch (Exception e) {
                Toast.makeText(this, "Cannot launch: " + fqcn, Toast.LENGTH_SHORT).show();
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

}
