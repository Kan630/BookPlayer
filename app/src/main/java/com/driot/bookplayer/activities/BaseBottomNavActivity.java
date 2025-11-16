package com.driot.bookplayer.activities;

import android.app.appsearch.GetSchemaResponse;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;

import com.driot.bookplayer.R;
import com.driot.bookplayer.objects.OngoingTaskHost;
import com.driot.bookplayer.player.NavHelper;
import com.driot.bookplayer.utils.log.LoggingActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public abstract class BaseBottomNavActivity extends LoggingActivity {

    /** Which bottom item should be checked in this screen */
    protected abstract int getNavId();

    /** Which layout should be inflated into the content area */
    @LayoutRes
    protected abstract int getLayoutResId();

    /** Override to true in activities that should show the ongoing-task overlay */
    protected boolean enableOngoingTaskOverlay() {
        return false;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1) Set the common layout that contains the FrameLayout + BottomNav
        super.setContentView(R.layout.activity_base_bottom_nav);

        // 2) Inflate the child layout into the container
        ViewGroup container = findViewById(R.id.base_content);
        getLayoutInflater().inflate(getLayoutResId(), container, true);

        // 3) Attach ongoing task overlay if requested
        if (enableOngoingTaskOverlay()) {
            View topOverlay = findViewById(R.id.topOverlayContainer);
            if (topOverlay != null) {
                OngoingTaskHost.attach(
                        this,
                        R.id.topOverlayContainer,
                        new Intent(this, AddResourceActivity.class)
                );
            } else {
                myLogEE(null, "enableOngoingTaskOverlay()==true but no topOverlayContainer in layout " + getLayoutResId());
            }
        }

        // 4) Setup bottom nav once for all activities
        setupBottomNav();
    }

    private void setupBottomNav() {
        BottomNavigationView bottom = findViewById(R.id.bottomNav);
        myLogI("setupBottomNav() -  navId=" + getNavId());
        bottom.setSelectedItemId(getNavId());

        bottom.setOnItemSelectedListener(item -> {
            myLogI("--- user click bottom Nav bar ---    itemId=" + item.getItemId() + " - " + item.getTitle());
            int id = item.getItemId();
            if (id == getNavId()) {
                myLogD("already here");
                return true;
            }

            if (id == R.id.nav_podcast) {
                NavHelper.navigateToPodcastSection(this);
                //overridePendingTransition(0, 0);
                return true;
            }

            Intent intent = null;
            if (id == R.id.nav_library) {
                intent = new Intent(this, MainActivity.class);
            } else if (id == R.id.nav_radio) {
                intent = NavHelper.getNavToRadioActivityIntent(this);
                /*
            } else if (id == R.id.nav_podcast) {
                intent = NavHelper.getNavToPodcastActivityIntent(this);
                 */
            } else if (id == R.id.nav_add) {
                intent = new Intent(this, GetActivity.class);
            } else if (id == R.id.nav_settings) {
                intent = new Intent(this, SettingsActivity.class);
            }

            if (intent != null) {
                startActivity(intent);
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }

    // IMPORTANT: don't call setContentView() in child activities
    @Override
    public void setContentView(int layoutResID) {
        throw new UnsupportedOperationException(
                "Use getLayoutResId() in BaseBottomNavActivity instead of setContentView()"
        );
    }

    @Override
    public void setContentView(View view) {
        throw new UnsupportedOperationException(
                "Use getLayoutResId() in BaseBottomNavActivity instead of setContentView()"
        );
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        throw new UnsupportedOperationException(
                "Use getLayoutResId() in BaseBottomNavActivity instead of setContentView()"
        );
    }
}

