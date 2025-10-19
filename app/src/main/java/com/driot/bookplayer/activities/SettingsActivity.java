package com.driot.bookplayer.activities;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.utils.log.LoggingActivity;
import com.driot.bookplayer.views.SettingsSectionView;


import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 20/12/20
 */
public class SettingsActivity extends LoggingActivity {

    ScrollView scrollView;
    private SectionHost currentlyExpanded = null;
    private boolean headerTapLocked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings); //trigers AutofillManager notifyValueChanged  ignoring on state UNKNOWN  (pollute log in Android 12)
        InsetHelper.apply(this);

        scrollView = findViewById(R.id.scrollView);

/*  //TODO new universal toggle to replace checkboxes
        SettingSwitchRow rowOpenPlay = findViewById(R.id.row_sd_card);
        rowOpenPlay.setChecked(Option.getOpenPlayActivity());
        rowOpenPlay.setOnCheckedChangeListener((button, checked) -> {
            Option.setOpenPlayActivity(checked);
        });
 */

        SettingsSectionView sectionPlay = findViewById(R.id.section_play_behaviour);
        registerSection(
                sectionPlay,
                "expand_play_behaviour",
                () -> new com.driot.bookplayer.settings.ui.PlayBehaviourSettingsFragment(),
                savedInstanceState
        );

        SettingsSectionView sectionDesign = findViewById(R.id.section_design);
        registerSection(
                sectionDesign,
                "expand_design",
                () -> new com.driot.bookplayer.settings.ui.DesignSettingsFragment(),
                savedInstanceState
        );

        SettingsSectionView sectionImport = findViewById(R.id.section_import);
        registerSection(
                sectionImport,
                "expand_import",
                () -> new com.driot.bookplayer.settings.ui.ImportSettingsFragment(),
                savedInstanceState
        );

        SettingsSectionView sectionLibrivox = findViewById(R.id.section_librivox);
        registerSection(
                sectionLibrivox,
                "expand_librivox",
                () -> new com.driot.bookplayer.settings.ui.LibrivoxSettingsFragment(),
                savedInstanceState
        );

        SettingsSectionView sectionPodcast = findViewById(R.id.section_podcast);
        registerSection(
                sectionPodcast,
                "expand_podcast",
                () -> new com.driot.bookplayer.settings.ui.PodcastSettingsFragment(),
                savedInstanceState
        );

        SettingsSectionView sectionTts = findViewById(R.id.section_tts);
        registerSection(
                sectionTts,
                "expand_tts",
                () -> new com.driot.bookplayer.settings.ui.TtsSettingsFragment(),
                savedInstanceState
        );

        SettingsSectionView sectionAutomotive = findViewById(R.id.section_automotive);
        registerSection(
                sectionAutomotive,
                "expand_automotive",
                () -> new com.driot.bookplayer.settings.ui.AutomotiveSettingsFragment(),
                savedInstanceState
        );

        SettingsSectionView sectionNetwork = findViewById(R.id.section_network);
        registerSection(
                sectionNetwork,
                "expand_network",
                () -> new com.driot.bookplayer.settings.ui.NetworkSettingsFragment(),
                savedInstanceState
        );

        SettingsSectionView sectionUtilities = findViewById(R.id.section_utilities);
        registerSection(
                sectionUtilities,
                "expand_utilities",
                () -> new com.driot.bookplayer.settings.ui.UtilitiesSettingsFragment(),
                savedInstanceState
        );

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    // PERMISSIONS REMOVAL
    // adb shell pm revoke com.driot.bookplayer android.permission.RECORD_AUDIO
    // cd C:\Users\adrio\AppData\Local\Android\Sdk\platform-tools\
    // Developer Options => Security settings of USB debugging... = OFF

    //adb shell dumpsys package com.driot.bookplayer
    //adb -s P7LFRGOFKVKRLNPF shell dumpsys package com.driot.bookplayer

    //adb devices

    // tablet
    //R9JT308QFNA

    // old Oppo
    //P7LFRGOFKVKRLNPF

    // Xiaomi Redmi
    //36085d331d5c

    @Override
    public void finish() { //needed because of recreate()
        if (this.getSharedPreferences(Option.SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).getBoolean("ACTIVITY_OPTION_HAS_RESULT", false)) { //trick to reload MainActivity
            setResult(Activity.RESULT_OK);
        }
        super.finish();
    }

    //// Saving scroll position where reloading activity (after applying theme color change)
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
        final int scrollPosition = savedInstanceState.getInt("scroll_position");
        scrollView.post(() -> scrollView.scrollTo(0, scrollPosition));
    }

    @Override
    protected void onResume() {
        super.onResume();
    }


    private void setChildButtonAutomotive(CheckBox chk, LinearLayout ll, boolean checked, boolean enabled) {
        if (!chk.isChecked()) chk.setChecked(checked);
        chk.setEnabled(enabled);
        ll.setEnabled(enabled);
    }



    // =====================
// Inline section helper
// =====================
    private interface FragmentFactory { Fragment create(); }

    private static final class SectionHost {
        final com.driot.bookplayer.views.SettingsSectionView sectionView;
        final String stateKey;
        final FragmentFactory factory;
        boolean expanded;

        SectionHost(com.driot.bookplayer.views.SettingsSectionView sectionView,
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
                                 Bundle savedInstanceState) {

        SectionHost host = new SectionHost(sectionView, stateKey, factory);

        onHeaderClicked(host);

        if (savedInstanceState != null) {
            host.expanded = savedInstanceState.getBoolean(stateKey, false);
        }

        sectionHosts.add(host);

        sectionView.getHeaderView().setOnClickListener(v -> {
            if (host.expanded) {
                collapseSection(host, /*removeFragment*/ false);
            } else {
                expandSection(host, /*scrollToHeader*/ true);
            }
        });

        // Initial state application (no scroll on first render)
        if (host.expanded) {
            expandSection(host, /*scrollToHeader*/ false);
        } else {
            collapseSection(host, /*removeFragment*/ false);
        }
    }
    private void expandSection(SectionHost host, boolean scrollToHeader) {
        if (currentlyExpanded != null && currentlyExpanded != host) {
            collapseSection(currentlyExpanded, /*removeFragment*/ false);
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
                        if (scrollToHeader) scrollHeaderIntoView(host); // <- now safe (no execPending)
                    })
                    .commit();
        } else {
            // Fragment already present; just scroll after the container is made visible
            if (scrollToHeader) scrollHeaderIntoView(host);
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
        if (currentlyExpanded == host) currentlyExpanded = null;
    }

    private void scrollHeaderIntoView(final SectionHost host) {
        if (scrollView == null) return;

        // Avoid focus-stealing auto-scrolls from inputs/spinners
        View current = getCurrentFocus();
        if (current != null) current.clearFocus();

        // Wait until the section lays out (safe whether we just added a fragment or not)
        host.sectionView.post(() -> {
            final View header = host.sectionView.getHeaderView();

            int[] headerLoc = new int[2];
            int[] svLoc = new int[2];
            header.getLocationOnScreen(headerLoc);
            scrollView.getLocationOnScreen(svLoc);

            int targetY = scrollView.getScrollY() + (headerLoc[1] - svLoc[1]);
            if (targetY > 16) targetY -= 16;

            scrollView.smoothScrollTo(0, targetY);
        });
    }

    private void onHeaderClicked(SectionHost host) {
        if (headerTapLocked) return;
        headerTapLocked = true;
        if (host.expanded) collapseSection(host, false); else expandSection(host, true);
        host.sectionView.postDelayed(() -> headerTapLocked = false, 150);
    }

}
