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

import javax.inject.Inject;

public abstract class FullActivity extends BaseActivity {

    @Inject protected NavState navState;
    @Inject protected NavHelper navHelper;

    protected abstract int getNavId();

    @LayoutRes
    protected abstract int getLayoutResId();

    protected abstract boolean enableOngoingTaskOverlay();

    protected boolean displayAppNavBar() {
        return Option.getDisplayAppNavBar();
    }

    /**
     * Override to declare the "section parent" of this activity.
     * - null (default): this activity IS the section root → back goes to MainActivity.
     * - non-null: back navigates to that activity (which must itself be IS_SECTION_ROOT).
     *
     * Example chain: RadioFavoritesActivity(parent=GetRadioActivity) → GetRadioActivity(root) → MainActivity
     */
    @Nullable
    protected Class<? extends FullActivity> getSectionParent() { return null; }

    private NavigationBarView appNavBar;
    private boolean navSelectionFromCode = false;
    private final boolean VERBOSE_DEBUG = true;
    private OnBackPressedCallback sectionRootBackCallback = null;

    private void myLogDD(String txt) { if (VERBOSE_DEBUG) myLogD(txt); }

    @Override
    public void onBackPressed() {
        myLogDD("--- user pressed BACK (FullActivity override) ---     on " + TAG_FROM_BRACKET);
        myLogDD("current section :" + navState.getCurrentNavName());
        myLogDD("last intent :" + navState.getLastIntent(navState.getCurrentAppNavId()));
        //TODO if we have a specific stack, follow it instead of super that can lead to to another section like nav_id_library
        super.onBackPressed(); // keep default behaviour (finish / navigate back)
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        super.setContentView(R.layout.activity_full);

        // activity layout
        ViewGroup container = findViewById(R.id.base_content);
        getLayoutInflater().inflate(getLayoutResId(), container, true);

        // top overlay : Ongoing Task
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

        // app nav bar
        setupBottomNav();

        registerSectionRootBackCallback();


        View miniNowPlaying = findViewById(R.id.miniNowPlaying);
        if (miniNowPlaying != null) {
            ViewCompat.setOnApplyWindowInsetsListener(miniNowPlaying, (v, insets) -> {
                Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(
                        sys.left,
                        sys.top,
                        sys.right,
                        sys.bottom);
                return insets; // don't consume — InsetHelper still needs them
            });
            ViewCompat.requestApplyInsets(miniNowPlaying);
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

        Class<? extends FullActivity> parent = getSectionParent();

        sectionRootBackCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (parent == null) {
                    // True section root → go to MainActivity
                    myLogI("--- BACK from section root [" + getClass().getSimpleName() + "] → MainActivity ---");
                    Intent intent = new Intent(FullActivity.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                } else {
                    // Has a declared parent → navigate up, marking it as section root too
                    myLogI("--- BACK from [" + getClass().getSimpleName() + "] → " + parent.getSimpleName() + " ---");
                    Intent intent = new Intent(FullActivity.this, parent);
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
        appNavBar = findViewById(R.id.AppNav);
        myLogDD("setupBottomNav() -  navId=" + getNavId());

        appNavBar.setOnItemSelectedListener(item -> {
            boolean fromCode = navSelectionFromCode;
            navSelectionFromCode = false;

            if (fromCode) {
                myLogDD("BottomNav selection changed programmatically: item="
                        + item.getItemId() + " - " + item.getTitle());
                return true;
            }

            myLogI("--- user click bottom Nav bar ---    item = "
                    + item.getItemId() + " - " + item.getTitle());

            if (navHelper.handleAppNavClick(FullActivity.this, item.getItemId())) {
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
            navState.setCurrentAppNavId(navId);
        }

        if (appNavBar != null) {
            int targetId = getNavId();
            if (appNavBar.getSelectedItemId() != targetId) {
                myLogD("onResume(): fixing bottom nav selection to " + targetId);
                selectBottomNavItemFromCode(targetId);
            }
        }
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

    private void selectBottomNavItemFromCode(int itemId) {
        if (appNavBar == null) return;
        navSelectionFromCode = true;
        appNavBar.setSelectedItemId(itemId);
    }

}
