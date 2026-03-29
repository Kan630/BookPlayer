package com.driot.bookplayer.nav;

import static com.driot.bookplayer.activities.MainActivity.EXTRA_REQUESTED_NAV_ID;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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

    /**
     * Override to declare the "section parent" of this activity.
     * - null (default): this activity IS the section root → back goes to MainActivity.
     * - non-null: back navigates to that activity (which must itself be IS_SECTION_ROOT).
     */
    @Nullable
    protected Class<? extends BaseBottomNavActivity> getSectionParent() { return null; }

    private NavigationBarView bottomNav;
    private boolean navSelectionFromCode = false;
    private final boolean VERBOSE_DEBUG = false;
    private OnBackPressedCallback sectionRootBackCallback = null;

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

        // Apply window insets to miniNowPlaying:
        // - side insets (sys.left/right) always, to handle landscape nav bar on the side
        // - bottom inset only when bottomNav is hidden (pure flavour: always)
        View miniNowPlaying = findViewById(R.id.miniNowPlaying);
        if (miniNowPlaying != null) {
            final int initLeft   = miniNowPlaying.getPaddingLeft();
            final int initTop    = miniNowPlaying.getPaddingTop();
            final int initRight  = miniNowPlaying.getPaddingRight();
            final int initBottom = miniNowPlaying.getPaddingBottom();
            final boolean applyBottom = !displayBottomNavBar();

            ViewCompat.setOnApplyWindowInsetsListener(miniNowPlaying, (v, insets) -> {
                Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(
                        initLeft  + sys.left,
                        initTop,
                        initRight + sys.right,
                        applyBottom ? initBottom + sys.bottom : initBottom);
                return insets; // don't consume — InsetHelper still needs them
            });
            ViewCompat.requestApplyInsets(miniNowPlaying);
        }

        // 5) Optional: hide the bottom nav completely
        if (!displayBottomNavBar()) {
            if (bottomNav != null)
                bottomNav.setVisibility(View.GONE);
        }
    }

    private void registerSectionRootBackCallback() {
        if (sectionRootBackCallback != null) return;
        if (!getIntent().getBooleanExtra(Intents.EXTRA_IS_SECTION_ROOT, false)) return;

        Class<? extends BaseBottomNavActivity> parent = getSectionParent();

        sectionRootBackCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (parent == null) {
                    myLogI("--- BACK from section root [" + getClass().getSimpleName() + "] → MainActivity ---");
                    Intent intent = new Intent(BaseBottomNavActivity.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                } else {
                    myLogI("--- BACK from [" + getClass().getSimpleName() + "] → " + parent.getSimpleName() + " ---");
                    Intent intent = new Intent(BaseBottomNavActivity.this, parent);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    intent.putExtra(Intents.EXTRA_IS_SECTION_ROOT, true);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, sectionRootBackCallback);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        registerSectionRootBackCallback();
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
