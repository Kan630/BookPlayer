package com.driot.bookplayer.nav;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.AddResourceActivity;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.imports.OngoingTaskHost;
import com.driot.bookplayer.utils.log.BaseActivity;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.navigationrail.NavigationRailView;

import javax.inject.Inject;

public abstract class FullActivity extends BaseActivity {

    @Inject protected NavState navState;
    @Inject protected NavHelper navHelper;

    @LayoutRes
    protected abstract int getLayoutResId();

    protected abstract boolean enableOngoingTaskOverlay();

    protected boolean displayAppNavBar() {
        return Option.getDisplayAppNavBar();
    }

    private NavigationBarView appNavBarView;
    private boolean navSelectionFromCode = false;
    private final boolean VERBOSE_DEBUG = false;

    private void myLogDD(String txt) { if (VERBOSE_DEBUG) myLogD(txt); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        super.setContentView(R.layout.activity_app_nav);

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

        setupAppNavBar();

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
            final boolean isNavRail   = appNavBarView instanceof NavigationRailView;
            final boolean applyBottom = isNavRail || !displayAppNavBar();

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
                if (!applyBottom && appNavBarView != null) {
                    appNavBarView.setPadding(
                            appNavBarView.getPaddingLeft(),
                            appNavBarView.getPaddingTop(),
                            appNavBarView.getPaddingRight(),
                            sys.bottom);
                }

                return insets; // don't consume — InsetHelper still needs them
            });
            ViewCompat.requestApplyInsets(miniNowPlaying);
        }

        if (!displayAppNavBar()) {
            if (appNavBarView != null) {
                appNavBarView.setVisibility(View.GONE);
            }
        }
    }

    private void setupAppNavBar() {
        appNavBarView = findViewById(R.id.bottomNav);
        myLogDD("setupBottomNav() -  navId=" + getNavSectionId());

        appNavBarView.setOnItemSelectedListener(item -> {
            boolean fromCode = navSelectionFromCode;
            navSelectionFromCode = false;

            if (fromCode) {
                myLogDD("BottomNav selection changed programmatically: item="
                        + item.getItemId() + " - " + item.getTitle());
                return true;
            }

            myLogI("--- user click bottom Nav bar ---    item = "
                    + item.getItemId() + " - " + item.getTitle());

            if (navHelper.handleAppNavBarClick(FullActivity.this, item.getItemId())) {
                return true;
            }

            return true;
        });

        selectAppNavItemFromCode(getNavSectionId());
    }

    @Override
    public void setContentView(int layoutResID) {
        throw new UnsupportedOperationException(
                "Use getLayoutResId() in FullActivity instead of setContentView()");
    }

    @Override
    public void setContentView(View view) {
        throw new UnsupportedOperationException(
                "Use getLayoutResId() in FullActivity instead of setContentView()");
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        throw new UnsupportedOperationException(
                "Use getLayoutResId() in FullActivity instead of setContentView()");
    }

    private void selectAppNavItemFromCode(int itemId) {
        if (appNavBarView == null) return;
        navSelectionFromCode = true;
        appNavBarView.setSelectedItemId(itemId);
    }

}
