package com.driot.bookplayer.nav;

import static com.driot.bookplayer.activities.MainActivity.EXTRA_REQUESTED_NAV_ID;

import android.content.Context;
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
import com.driot.bookplayer.activities.MainActivity;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.nav.NavState;
import com.driot.bookplayer.imports.OngoingTaskHost;
import com.driot.bookplayer.utils.log.BaseActivity;
import com.google.android.material.navigation.NavigationBarView;

public abstract class BaseBottomNavActivity extends BaseActivity {

    protected abstract int getNavId();

    @LayoutRes
    protected abstract int getLayoutResId();

    protected abstract boolean enableOngoingTaskOverlay();

    protected boolean displayBottomNavBar() {
        return Option.getDisplayBottomNavBar();
    }

    private NavigationBarView bottomNav;
    private boolean navSelectionFromCode = false;
    private final boolean VERBOSE_DEBUG = false;

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

        if (!displayBottomNavBar()) {
            if (bottomNav != null) {
                bottomNav.setVisibility(View.GONE);
            }

            // When the bottom nav is hidden, it no longer absorbs the system
            // navigation bar inset. Apply it directly to miniNowPlaying so it
            // sits above the system nav bar instead of going behind it.
            View miniNowPlaying = findViewById(R.id.miniNowPlaying);
            if (miniNowPlaying != null) {
                final int initialPaddingBottom = miniNowPlaying.getPaddingBottom();
                ViewCompat.setOnApplyWindowInsetsListener(miniNowPlaying, (v, insets) -> {
                    Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(
                            v.getPaddingLeft(),
                            v.getPaddingTop(),
                            v.getPaddingRight(),
                            initialPaddingBottom + sys.bottom);
                    return insets; // don't consume — InsetHelper still needs them
                });
                ViewCompat.requestApplyInsets(miniNowPlaying);
            }
        }
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

            if (NavHelper.handleBottomNavClick(BaseBottomNavActivity.this, item.getItemId())) {
                return true;
            }

            return true;
        });

        selectBottomNavItemFromCode(getNavId());
    }

    @Override
    protected void onResume() {
        super.onResume();
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

    public static void startAsRoot(Context ctx, int requestedNavId) {
        Intent intent = new Intent(ctx, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(EXTRA_REQUESTED_NAV_ID, requestedNavId);
        ctx.startActivity(intent);
    }

    private void myLogDD(String txt) { if (VERBOSE_DEBUG) myLogD(txt); }
}
