package com.driot.bookplayer.nav;

import static com.driot.bookplayer.activities.MainActivity.EXTRA_REQUESTED_NAV_ID;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.LayoutRes;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.AddResourceActivity;
import com.driot.bookplayer.activities.MainActivity;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.imports.OngoingTaskHost;
import com.driot.bookplayer.utils.log.BaseActivity;
import com.google.android.material.navigation.NavigationBarView;

import javax.inject.Inject;

public abstract class BaseBottomNavActivity extends BaseActivity {

    @Inject protected NavState navState;
    @Inject protected NavHelper navHelper;

    /** Which bottom item should be checked in this screen */
    protected abstract int getNavId();

    /** Which layout should be inflated into the content area */
    @LayoutRes
    protected abstract int getLayoutResId();

    /** Override to true in activities that should show the ongoing-task overlay */
    protected abstract boolean enableOngoingTaskOverlay();

    /** false in pure flavour */
    protected boolean displayBottomNavBar() {
        return false;
    }

    private NavigationBarView bottomNav;

    private boolean navSelectionFromCode = false;

    private final boolean VERBOSE_DEBUG = false;

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
                topOverlay.post(() -> {
                    OngoingTaskHost.attach(
                            this,
                            R.id.topOverlayContainer,
                            new Intent(this, AddResourceActivity.class));
                });
            } else {
                myLogEE(null,
                        "enableOngoingTaskOverlay()==true but no topOverlayContainer in layout " + getLayoutResId());
            }
        }

        // 4) Setup bottom nav once for all activities
        setupBottomNav();
        registerSectionRootBackCallback();

        // 5) Optional: hide the bottom nav completely
        if (!displayBottomNavBar()) {
            if (bottomNav != null)
                bottomNav.setVisibility(View.GONE);
        }
    }

    /**
     * For non-library section roots (launched via bottom nav): back goes to MainActivity.
     * Library root (MainActivity) handles its own back callback.
     * Child activities (no EXTRA_IS_SECTION_ROOT) fall through to normal Android back.
     */
    private void registerSectionRootBackCallback() {
        boolean isSectionRoot = getIntent().getBooleanExtra(Intents.EXTRA_IS_SECTION_ROOT, false);
        if (!isSectionRoot) return;
        if (getNavId() == R.id.nav_library) return;

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                myLogI("--- USER CLICK BACK from section root [" + getClass().getSimpleName() + "] --- navigate to MainActivity ---");
                Intent intent = new Intent(BaseBottomNavActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            }
        });
    }

    private void setupBottomNav() {
        bottomNav = findViewById(R.id.bottomNav);
        myLogDD("setupBottomNav() -  navId=" + getNavId());

        bottomNav.setOnItemSelectedListener(item -> {
            boolean fromCode = navSelectionFromCode;
            navSelectionFromCode = false; // reset for next time

            if (fromCode) {
                myLogDD("BottomNav selection changed programmatically: item="
                        + item.getItemId() + " - " + item.getTitle());
                return true; // keep the item checked, but don't treat as user click
            }

            myLogI("--- user click bottom Nav bar ---    item = "
                    + item.getItemId() + " - " + item.getTitle());

            if (navHelper.handleBottomNavClick(BaseBottomNavActivity.this, item.getItemId())) {
                return true;
            }

            return true;
        });

        // Set initial selection (programmatic)
        selectBottomNavItemFromCode(getNavId());
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Save current intent to NavState for state restoration
        int navId = getNavId();
        if (navId != 0 && navState != null) {
            navState.setLastIntent(navId, getIntent());
            navState.setCurrentBottomNavId(navId);
        }

        if (bottomNav != null) {
            int targetId = getNavId();
            if (bottomNav.getSelectedItemId() != targetId) {
                myLogD("onResume(): fixing bottom nav selection to " + targetId);
                selectBottomNavItemFromCode(targetId);
            }
        }
    }

    // IMPORTANT: don't call setContentView() in child activities
    @Override
    public void setContentView(int layoutResID) {
        throw new UnsupportedOperationException(
                "Use getLayoutResId() in BaseBottomNavActivity instead of setContentView()");
    }

    @Override
    public void setContentView(View view) {
        throw new UnsupportedOperationException(
                "Use getLayoutResId() in BaseBottomNavActivity instead of setContentView()");
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        throw new UnsupportedOperationException(
                "Use getLayoutResId() in BaseBottomNavActivity instead of setContentView()");
    }

    private void selectBottomNavItemFromCode(int itemId) {
        if (bottomNav == null)
            return;
        navSelectionFromCode = true;
        bottomNav.setSelectedItemId(itemId);
    }

    public static void startAsRoot(Context ctx, int requestedNavId) {
        Intent intent = new Intent(ctx, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(EXTRA_REQUESTED_NAV_ID, requestedNavId);
        ctx.startActivity(intent);
    }

    private void myLogDD(String txt) {if (VERBOSE_DEBUG) myLogD(txt);}

}
