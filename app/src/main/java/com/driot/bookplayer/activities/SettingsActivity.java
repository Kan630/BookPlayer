package com.driot.bookplayer.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.nav.FullActivity;
import com.driot.bookplayer.settings.ui.RepositoriesSettingsFragment;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.views.SettingsSectionView;

import com.driot.bookplayer.settings.ui.AutomotiveSettingsFragment;
import com.driot.bookplayer.settings.ui.DesignSettingsFragment;
import com.driot.bookplayer.settings.ui.ImportSettingsFragment;
import com.driot.bookplayer.settings.ui.LanguageSettingsFragment;
import com.driot.bookplayer.settings.ui.MassiveImportSettingsFragment;
import com.driot.bookplayer.settings.ui.NetworkSettingsFragment;
import com.driot.bookplayer.settings.ui.PlayBehaviourSettingsFragment;
import com.driot.bookplayer.settings.ui.PodcastSettingsFragment;
import com.driot.bookplayer.settings.ui.RadioSettingsFragment;
import com.driot.bookplayer.settings.ui.StorageSettingsFragment;
import com.driot.bookplayer.settings.ui.TtsSettingsFragment;
import com.driot.bookplayer.settings.ui.UtilitiesSettingsFragment;

import androidx.fragment.app.Fragment;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 20/12/20
 *
 * Top level: a plain list of settings categories. Tapping one pushes its fragment full-screen
 * into the detail pane (with a back arrow + title header); the system/gesture back button pops
 * it and returns to the list, same as opening a regular screen.
 */

@AndroidEntryPoint
public class SettingsActivity extends FullActivity {

    private static final String DETAIL_BACKSTACK_TAG = "settings_detail";
    private static final String KEY_DETAIL_TITLE = "settings_detail_title";

    ScrollView scrollView;
    private View detailContainer;
    private TextView tvDetailTitle;
    private int detailFragmentContainerId;

    /**
     * Scroll position to restore after recreate (e.g. theme change). Applied in
     * onResume so it runs after any scroll reset.
     */
    private int pendingScrollPosition = -1;

    // TODO new universal toggle to replace checkboxes

    @Override
    protected int getNavSectionId() {
        return R.id.nav_settings;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_settings;
    }

    @Override
    protected boolean enableOngoingTaskOverlay() {
        return true;
    }

    @Override protected boolean isSectionRoot() { return true; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        scrollView = findViewById(R.id.scrollView);
        detailContainer = findViewById(R.id.detailContainer);
        tvDetailTitle = findViewById(R.id.tvDetailTitle);
        FrameLayout detailFragmentContainer = findViewById(R.id.detailFragmentContainer);
        detailFragmentContainerId = detailFragmentContainer.getId();

        ImageButton btnDetailBack = findViewById(R.id.btnDetailBack);
        btnDetailBack.setOnClickListener(v -> getSupportFragmentManager().popBackStack());

        registerCategory(R.id.section_language, LanguageSettingsFragment::new, false);
        registerCategory(R.id.section_play_behaviour, PlayBehaviourSettingsFragment::new, false);
        registerCategory(R.id.section_design, DesignSettingsFragment::new, false);
        registerCategory(R.id.section_storage, StorageSettingsFragment::new, true);
        registerCategory(R.id.section_import, ImportSettingsFragment::new, false);
        registerCategory(R.id.section_librivox, RepositoriesSettingsFragment::new, true);
        registerCategory(R.id.section_radio, RadioSettingsFragment::new, true);
        registerCategory(R.id.section_podcast, PodcastSettingsFragment::new, true);
        registerCategory(R.id.section_tts, TtsSettingsFragment::new, false);
        registerCategory(R.id.section_automotive, AutomotiveSettingsFragment::new, false);
        registerCategory(R.id.section_network, NetworkSettingsFragment::new, true);
        registerCategory(R.id.section_utilities, UtilitiesSettingsFragment::new, false);
        registerCategory(R.id.section_massive_import, MassiveImportSettingsFragment::new, false);

        // Admin isn't inline settings fields like the categories above - it's a whole separate
        // screen - so it skips the fragment push machinery and just navigates straight to
        // AdminActivity. Admin-only, at the very bottom.
        SettingsSectionView sectionAdmin = findViewById(R.id.section_admin);
        if (Tonio.isAdmin()) {
            sectionAdmin.setVisibility(View.VISIBLE);
            sectionAdmin.getHeaderView().setOnClickListener(v ->
                    startActivity(new Intent(this, AdminActivity.class)));
        }

        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            boolean detailOpen = getSupportFragmentManager().getBackStackEntryCount() > 0;
            showDetail(detailOpen);
        });

        // addOnBackStackChangedListener() above only fires on *future* changes, not for a
        // back stack the FragmentManager already restored during super.onCreate() (e.g. after
        // Activity.recreate() following a theme change) - without this, the detail pane's
        // fragment is correctly restored underneath, but detailContainer/scrollView visibility
        // stays at their default XML state (list visible), so the user appears to be dumped
        // back on the category list instead of staying on the screen they were on.
        boolean detailOpen = getSupportFragmentManager().getBackStackEntryCount() > 0;
        showDetail(detailOpen);
        if (detailOpen && savedInstanceState != null) {
            CharSequence restoredTitle = savedInstanceState.getCharSequence(KEY_DETAIL_TITLE);
            if (restoredTitle != null) tvDetailTitle.setText(restoredTitle);
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    getSupportFragmentManager().popBackStack();
                } else {
                    // Detail already closed - defer to the next callback in the chain (e.g.
                    // BaseActivity's nav-section-aware back handling).
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private interface FragmentFactory {
        Fragment create();
    }

    private void registerCategory(int sectionViewId, FragmentFactory factory, boolean onlyForFullFlavour) {
        SettingsSectionView sectionView = findViewById(sectionViewId);

        if (onlyForFullFlavour && Tonio.isPure(this)) {
            sectionView.setVisibility(View.GONE);
            return;
        }

        sectionView.getHeaderView().setOnClickListener(v ->
                openCategory(sectionView.getTitle(), factory.create()));
    }

    private void openCategory(CharSequence title, Fragment frag) {
        // The list only actually becomes hidden once the back-stack-changed listener fires,
        // which happens asynchronously after this transaction commits - not synchronously here.
        // Without this guard, a fast double-tap on two different rows (easy to do by accident)
        // pushes two fragments onto the back stack in one go, so a single back press doesn't
        // return to the list - exactly the kind of "weird" nav behavior a race like this causes.
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) return;

        Bundle args = (frag.getArguments() != null) ? frag.getArguments() : new Bundle();
        args.putBoolean("ARG_SHOW_LOCAL_TITLE", false);
        frag.setArguments(args);

        tvDetailTitle.setText(title);

        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        R.anim.settings_enter_from_right, R.anim.settings_exit_to_left,
                        R.anim.settings_pop_enter_from_left, R.anim.settings_pop_exit_to_right)
                .replace(detailFragmentContainerId, frag)
                .addToBackStack(DETAIL_BACKSTACK_TAG)
                .commit();
    }

    private void showDetail(boolean show) {
        detailContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        scrollView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    // PERMISSIONS REMOVAL
    // adb shell pm revoke com.driot.bookplayer android.permission.RECORD_AUDIO
    // cd C:\Users\adrio\AppData\Local\Android\Sdk\platform-tools\
    // Developer Options => Security settings of USB debugging... = OFF

    // adb shell dumpsys package com.driot.bookplayer
    // adb -s P7LFRGOFKVKRLNPF shell dumpsys package com.driot.bookplayer

    // adb devices

    // tablet
    // R9JT308QFNA

    // old Oppo
    // P7LFRGOFKVKRLNPF

    // Xiaomi Redmi
    // 36085d331d5c

    @Override
    public void finish() { // needed because of recreate()
        if (Pref.getNeedsRecreate()) { // trick to reload MainActivity
            setResult(Activity.RESULT_OK);
        }
        super.finish();
    }

    //// Saving scroll position where reloading activity (after applying theme color
    //// change)
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("scroll_position", scrollView.getScrollY());
        if (detailContainer.getVisibility() == View.VISIBLE) {
            outState.putCharSequence(KEY_DETAIL_TITLE, tvDetailTitle.getText());
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        pendingScrollPosition = savedInstanceState.getInt("scroll_position", 0);
        myLog("reading scroll position : " + pendingScrollPosition);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingScrollPosition >= 0 && scrollView != null) {
            final int position = pendingScrollPosition;
            pendingScrollPosition = -1;
            // Apply after layout and after any scroll-to-top (e.g. initGoToTop); post twice
            // so we run last
            scrollView.post(() -> scrollView.post(() -> {
                if (Math.abs(scrollView.getScrollY() - position) <= 4) { // tolerance of ~4px
                    myLogD("scroll already around position " + position + " - skip restore");
                    return;
                }
                myLogD("scrolling to position : " + position);
                scrollView.scrollTo(0, position);
            }));
        }
    }

}
