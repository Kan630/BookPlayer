package com.driot.bookplayer.activities;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.driot.bookplayer.R;
import com.driot.bookplayer.helpers.InsetHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AdminActivity extends AppCompatActivity {

    private LinearLayout btnContainer;
    private ListView listActivities;

    // Map of label -> Activity class to create buttons dynamically
    private final LinkedHashMap<String, Class<?>> quickButtons = new LinkedHashMap<String, Class<?>>() {{
        // TODO: replace these with your real classes/packages if different
        put("Debug Database Activity", com.driot.bookplayer.activities.DebugDatabaseActivity.class);
        //put("Tts Read Txt Activity", com.driot.bookplayer.activities.TtsReadTxtActivity.class);
    }};

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);
        InsetHelper.apply(this);

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
