package com.driot.bookplayer.activities;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ScrollView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.nav.BaseBottomNavActivity;
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
import com.driot.bookplayer.settings.ui.TtsSettingsFragment;
import com.driot.bookplayer.settings.ui.UtilitiesSettingsFragment;

import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 20/12/20
 */

@AndroidEntryPoint
public class SettingsActivity extends BaseBottomNavActivity {

    ScrollView scrollView;
    private SectionHost currentlyExpanded = null;
    private boolean headerTapLocked = false;
    /**
     * Scroll position to restore after recreate (e.g. theme change). Applied in
     * onResume so it runs after any scroll reset.
     */
    private int pendingScrollPosition = -1;

    // TODO new universal toggle to replace checkboxes

    @Override
    protected int getNavId() {
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

        SettingsSectionView sectionLanguage = findViewById(R.id.section_language);
        registerSection(
                sectionLanguage,
                "expand_language",
                LanguageSettingsFragment::new,
                savedInstanceState, false);

        SettingsSectionView sectionPlay = findViewById(R.id.section_play_behaviour);
        registerSection(
                sectionPlay,
                "expand_play_behaviour",
                PlayBehaviourSettingsFragment::new,
                savedInstanceState, false);

        SettingsSectionView sectionDesign = findViewById(R.id.section_design);
        registerSection(
                sectionDesign,
                "expand_design",
                DesignSettingsFragment::new,
                savedInstanceState, false);

        SettingsSectionView sectionImport = findViewById(R.id.section_import);
        registerSection(
                sectionImport,
                "expand_import",
                ImportSettingsFragment::new,
                savedInstanceState, false);

        SettingsSectionView sectionLibrivox = findViewById(R.id.section_librivox);
        registerSection(
                sectionLibrivox,
                "expand_librivox",
                RepositoriesSettingsFragment::new,
                savedInstanceState, true);

        SettingsSectionView sectionRadio = findViewById(R.id.section_radio);
        registerSection(
                sectionRadio,
                "expand_radio",
                RadioSettingsFragment::new,
                savedInstanceState, true);

        SettingsSectionView sectionPodcast = findViewById(R.id.section_podcast);
        registerSection(
                sectionPodcast,
                "expand_podcast",
                PodcastSettingsFragment::new,
                savedInstanceState, true);

        SettingsSectionView sectionTts = findViewById(R.id.section_tts);
        registerSection(
                sectionTts,
                "expand_tts",
                TtsSettingsFragment::new,
                savedInstanceState, false);

        SettingsSectionView sectionAutomotive = findViewById(R.id.section_automotive);
        registerSection(
                sectionAutomotive,
                "expand_automotive",
                AutomotiveSettingsFragment::new,
                savedInstanceState, false);

        SettingsSectionView sectionNetwork = findViewById(R.id.section_network);
        registerSection(
                sectionNetwork,
                "expand_network",
                NetworkSettingsFragment::new,
                savedInstanceState, true);

        SettingsSectionView sectionUtilities = findViewById(R.id.section_utilities);
        registerSection(
                sectionUtilities,
                "expand_utilities",
                UtilitiesSettingsFragment::new,
                savedInstanceState, false);

        SettingsSectionView sectionMassiveImport = findViewById(R.id.section_massive_import);
        registerSection(
                sectionMassiveImport,
                "expand_massive_import",
                MassiveImportSettingsFragment::new,
                savedInstanceState, false);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // If a section is currently expanded, collapse it instead of closing the
        // activity
        if (currentlyExpanded != null) {
            collapseSection(currentlyExpanded, false);
        } else {
            // No section expanded, proceed with default back behavior
            super.onBackPressed();
        }
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
        for (SectionHost s : sectionHosts) {
            outState.putBoolean(s.stateKey, s.expanded);
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

    // =====================
    // Inline section helper
    // =====================
    private interface FragmentFactory {
        Fragment create();
    }

    private static final class SectionHost {
        final SettingsSectionView sectionView;
        final String stateKey;
        final FragmentFactory factory;
        boolean expanded;

        SectionHost(SettingsSectionView sectionView,
                String stateKey,
                FragmentFactory factory) {
            this.sectionView = sectionView;
            this.stateKey = stateKey;
            this.factory = factory;
        }
    }

    private final List<SectionHost> sectionHosts = new ArrayList<>();

    private void registerSection(SettingsSectionView sectionView,
            String stateKey,
            FragmentFactory factory,
            Bundle savedInstanceState, boolean onlyForFullFlavour) {

        if (onlyForFullFlavour && Tonio.isPure(this)) {
            sectionView.setVisibility(View.GONE);
            return;
        }

        SectionHost host = new SectionHost(sectionView, stateKey, factory);

        // onHeaderClicked(host); // removed: caused every section to toggle during
        // onCreate

        if (savedInstanceState != null) {
            host.expanded = savedInstanceState.getBoolean(stateKey, false);
        } else {
            host.expanded = false;
            sectionView.showContainer(false);
        }

        sectionHosts.add(host);

        sectionView.getHeaderView().setOnClickListener(v -> {
            myLogI("--- user clicks header : " + host.stateKey);
            if (host.expanded) {
                collapseSection(host, /* removeFragment */ false);
            } else {
                expandSection(host, /* scrollToHeader */ true);
            }
        });

        // Initial state application (no scroll on first render)
        if (host.expanded) {
            expandSection(host, /* scrollToHeader */ false);
        } else {
            collapseSection(host, /* removeFragment */ false);
        }
    }

    private void expandSection(SectionHost host, boolean scrollToHeader) {
        if (currentlyExpanded != null && currentlyExpanded != host) {
            collapseSection(currentlyExpanded, /* removeFragment */ false);
        }

        host.expanded = true;
        host.sectionView.showContainer(true);

        final int containerId = host.sectionView.getContainerId();
        if (getSupportFragmentManager().findFragmentById(containerId) == null) {
            Fragment frag = host.factory.create();
            Bundle args = (frag.getArguments() != null) ? frag.getArguments() : new Bundle();
            args.putBoolean("ARG_SHOW_LOCAL_TITLE", false);
            frag.setArguments(args);

            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(containerId, frag)
                    .runOnCommit(() -> {
                        if (scrollToHeader)
                            scrollHeaderIntoView(host); // <- now safe (no execPending)
                    })
                    .commit();
        } else {
            // Fragment already present; just scroll after the container is made visible
            if (scrollToHeader)
                scrollHeaderIntoView(host);
        }

        currentlyExpanded = host;
    }

    private void collapseSection(SectionHost host, boolean removeFragment) {
        host.expanded = false;
        host.sectionView.showContainer(false);

        if (removeFragment) {
            int containerId = host.sectionView.getContainerId();
            Fragment f = getSupportFragmentManager().findFragmentById(containerId);
            if (f != null) {
                getSupportFragmentManager().beginTransaction().remove(f).commit();
            }
        }
        if (currentlyExpanded == host)
            currentlyExpanded = null;
    }

    private void scrollHeaderIntoView(final SectionHost host) {
        if (scrollView == null)
            return;

        // Avoid focus-stealing auto-scrolls from inputs/spinners
        View current = getCurrentFocus();
        if (current != null)
            current.clearFocus();

        // Wait until the section lays out (safe whether we just added a fragment or
        // not)
        host.sectionView.post(() -> {
            final View header = host.sectionView.getHeaderView();

            int[] headerLoc = new int[2];
            int[] svLoc = new int[2];
            header.getLocationOnScreen(headerLoc);
            scrollView.getLocationOnScreen(svLoc);

            int targetY = scrollView.getScrollY() + (headerLoc[1] - svLoc[1]);
            if (targetY > 16)
                targetY -= 16;

            scrollView.smoothScrollTo(0, targetY);
        });
    }

    private void onHeaderClicked(SectionHost host) {
        if (headerTapLocked)
            return;
        headerTapLocked = true;
        if (host.expanded)
            collapseSection(host, false);
        else
            expandSection(host, true);
        host.sectionView.postDelayed(() -> headerTapLocked = false, 150);
    }

}
