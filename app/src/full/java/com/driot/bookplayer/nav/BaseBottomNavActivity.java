package com.driot.bookplayer.nav;

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
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.imports.OngoingTaskHost;
import com.driot.bookplayer.utils.log.BaseActivity;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.navigationrail.NavigationRailView;

import javax.inject.Inject;

public abstract class BaseBottomNavActivity extends BaseActivity {

    @Inject protected NavState navState;
    @Inject protected NavHelper navHelper;

    protected abstract int getNavId();

    @LayoutRes
    protected abstract int getLayoutResId();

    protected abstract boolean enableOngoingTaskOverlay();

    protected boolean displayBottomNavBar() {
        return Option.getDisplayBottomNavBar();
    }

    /**
     * Override to declare the "section parent" of this activity.
     * - null (default): this activity IS the section root → back goes to MainActivity.
     * - non-null: back navigates to that activity (which must itself be IS_SECTION_ROOT).
     *
     * Example chain: RadioFavoritesActivity(parent=GetRadioActivity) → GetRadioActivity(root) → MainActivity
     */
    @Nullable
    protected Class<? extends BaseBottomNavActivity> getSectionParent() { return null; }

    private NavigationBarView bottomNav;
    private boolean navSelectionFromCode = false;
    private final boolean VERBOSE_DEBUG = false;
    private OnBackPressedCallback sectionRootBackCallback = null;

    private void myLogDD(String txt) { if (VERBOSE_DEBUG) myLogD(txt); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        super.setContentView(R.layout.activity_base_bottom_nav);

        ViewGroup container = findViewById(R.id.base_content);
        getLayoutInflater().inflate(getLayoutResId(), container, true);

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

        setupBottomNav();
        registerSectionRootBackCallback();

        // Apply window insets to miniNowPlaying:
        // - side insets (sys.left/right) always, to handle landscape nav bar on the side
        //   (phone: nav bar moves to the right edge, hiding the close button without this)
        // - bottom inset when:
        //     a) landscape layout: bottomNav is a NavigationRailView (on the left), so it does
        //        NOT absorb sys.bottom — miniNowPlaying is constrained bottom_toBottomOf=parent
        //        and must apply sys.bottom itself.
        //     b) portrait layout: bottomNav (BottomNavigationView) is hidden by the user option,
        //        so nothing at the bottom absorbs sys.bottom.
        //   When portrait + bottomNav IS visible, we apply sys.bottom directly to bottomNav in the
        //   listener below instead, so the constraint chain positions miniNowPlaying correctly.
        View miniNowPlaying = findViewById(R.id.miniNowPlaying);
        if (miniNowPlaying != null) {
            final int initLeft   = miniNowPlaying.getPaddingLeft();
            final int initTop    = miniNowPlaying.getPaddingTop();
            final int initRight  = miniNowPlaying.getPaddingRight();
            final int initBottom = miniNowPlaying.getPaddingBottom();
            // NavigationRailView (landscape) sits on the left — it does not absorb sys.bottom
            final boolean isNavRail   = bottomNav instanceof NavigationRailView;
            final boolean applyBottom = isNavRail || !displayBottomNavBar();

            ViewCompat.setOnApplyWindowInsetsListener(miniNowPlaying, (v, insets) -> {
                Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(
                        initLeft  + sys.left,
                        initTop,
                        initRight + sys.right,
                        applyBottom ? initBottom + sys.bottom : initBottom);

                // When bottomNav is visible, Material's BottomNavigationView may not receive
                // sys.bottom if insets are consumed upstream (e.g. by the recyclerView).
                // Apply it directly here so the constraint chain (miniNowPlaying above bottomNav)
                // positions the mini player correctly above the system nav bar on all devices,
                // including tablets where the nav bar / taskbar can be taller than the items height.
                if (!applyBottom && bottomNav != null) {
                    bottomNav.setPadding(
                            bottomNav.getPaddingLeft(),
                            bottomNav.getPaddingTop(),
                            bottomNav.getPaddingRight(),
                            sys.bottom);
                }

                return insets; // don't consume — InsetHelper still needs them
            });
            ViewCompat.requestApplyInsets(miniNowPlaying);
        }

        if (!displayBottomNavBar()) {
            if (bottomNav != null) {
                bottomNav.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Registers a back callback for activities that are section roots (IS_SECTION_ROOT=true).
     *
     * Two cases based on getSectionParent():
     *  - null  → this is the true section root → back goes to MainActivity
     *  - Class → this activity sits above a parent → back navigates to that parent (also IS_SECTION_ROOT)
     *
     * Only registers once (sectionRootBackCallback guard). Called from onCreate and onNewIntent
     * so that activities reused via REORDER_TO_FRONT also get the callback when needed.
     */
    private void registerSectionRootBackCallback() {
        if (sectionRootBackCallback != null) return; // already registered
        if (!getIntent().getBooleanExtra(Intents.EXTRA_IS_SECTION_ROOT, false)) return;

        Class<? extends BaseBottomNavActivity> parent = getSectionParent();

        sectionRootBackCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (parent == null) {
                    // True section root → go to MainActivity
                    myLogI("--- BACK from section root [" + getClass().getSimpleName() + "] → MainActivity ---");
                    Intent intent = new Intent(BaseBottomNavActivity.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                } else {
                    // Has a declared parent → navigate up, marking it as section root too
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
        setIntent(intent); // keep getIntent() up-to-date
        // Activity reused via REORDER_TO_FRONT: register callback if now acting as section root
        registerSectionRootBackCallback();
    }

    private void setupBottomNav() {
        bottomNav = findViewById(R.id.bottomNav);
        myLogDD("setupBottomNav() -  navId=" + getNavId());

        bottomNav.setOnItemSelectedListener(item -> {
            boolean fromCode = navSelectionFromCode;
            navSelectionFromCode = false;

            if (fromCode) {
                myLogDD("BottomNav selection changed programmatically: item="
                        + item.getItemId() + " - " + item.getTitle());
                return true;
            }

            myLogI("--- user click bottom Nav bar ---    item = "
                    + item.getItemId() + " - " + item.getTitle());

            if (navHelper.handleBottomNavClick(BaseBottomNavActivity.this, item.getItemId())) {
                return true;
            }

            return true;
        });

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
        if (bottomNav == null) return;
        navSelectionFromCode = true;
        bottomNav.setSelectedItemId(itemId);
    }

}
