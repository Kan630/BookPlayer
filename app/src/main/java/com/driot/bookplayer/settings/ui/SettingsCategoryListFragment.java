package com.driot.bookplayer.settings.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.Navigation;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.AdminActivity;
import com.driot.bookplayer.activities.MainActivity;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingFragment;
import com.driot.bookplayer.views.SettingsSectionView;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Top-level list of settings categories - the start destination of settings_nav_graph.xml,
 * hosted by SettingsHostActivity. Tapping a category navigates (via NavController) to its
 * destination; each category fragment renders its own title (ARG_SHOW_LOCAL_TITLE defaults
 * to true when reached with no arguments, which is the case for every in-graph navigation
 * here). Converted from the former SettingsActivity's category-list half - see
 * [[radio_deeplink_applinks_fix]].
 *
 * Two-pane mode (R.bool.settings_two_pane, width >= 720dp): the selected category is shown
 * next to the list as a child fragment instead of being navigated to, so the tab's nav graph,
 * back handling and MainActivity.startSettings() direct links are unchanged. The detail
 * fragment class comes from MainActivity's settings destination map.
 */
public class SettingsCategoryListFragment extends LoggingFragment {

    private static final String STATE_SELECTED_SECTION = "selected_section";

    /** Section view id -> nav graph destination id, for the visible categories. */
    private final Map<Integer, Integer> destinationBySection = new LinkedHashMap<>();
    private boolean twoPane;
    private int selectedSectionId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings_category_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        twoPane = getResources().getBoolean(R.bool.settings_two_pane);
        destinationBySection.clear();
        if (savedInstanceState != null)
            selectedSectionId = savedInstanceState.getInt(STATE_SELECTED_SECTION, 0);

        registerCategory(view, R.id.section_language, R.id.languageSettingsFragment, false);
        registerCategory(view, R.id.section_play_behaviour, R.id.playBehaviourSettingsFragment, false);
        registerCategory(view, R.id.section_design, R.id.designSettingsFragment, false);
        registerCategory(view, R.id.section_storage, R.id.storageSettingsFragment, true);
        registerCategory(view, R.id.section_import, R.id.importSettingsFragment, false);
        registerCategory(view, R.id.section_librivox, R.id.repositoriesSettingsFragment, true);
        registerCategory(view, R.id.section_radio, R.id.radioSettingsFragment, true);
        registerCategory(view, R.id.section_podcast, R.id.podcastSettingsFragment, true);
        registerCategory(view, R.id.section_tts, R.id.ttsSettingsFragment, false);
        registerCategory(view, R.id.section_automotive, R.id.automotiveSettingsFragment, false);
        registerCategory(view, R.id.section_network, R.id.networkSettingsFragment, true);
        registerCategory(view, R.id.section_utilities, R.id.utilitiesSettingsFragment, false);
        registerCategory(view, R.id.section_massive_import, R.id.massiveImportSettingsFragment, false);

        // Admin isn't inline settings fields like the categories above - it's a whole separate
        // screen - so it skips the nav graph and just navigates straight to AdminActivity.
        // Admin-only, at the very bottom.
        SettingsSectionView sectionAdmin = view.findViewById(R.id.section_admin);
        if (Tonio.isAdmin()) {
            sectionAdmin.setVisibility(View.VISIBLE);
            sectionAdmin.getHeaderView().setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), AdminActivity.class)));
        }

        FragmentManager fm = getChildFragmentManager();
        Fragment shownDetail = fm.findFragmentById(R.id.settings_detail_container);
        if (!twoPane) {
            // Window shrank below the two-pane width: drop the restored detail fragment.
            if (shownDetail != null)
                fm.beginTransaction().remove(shownDetail).commit();
            selectedSectionId = 0;
        } else if (shownDetail == null || !destinationBySection.containsKey(selectedSectionId)) {
            showInDetailPane(destinationBySection.containsKey(selectedSectionId)
                    ? selectedSectionId
                    : destinationBySection.keySet().iterator().next());
        } else {
            updateSelection();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SELECTED_SECTION, selectedSectionId);
    }

    /** MainActivity forwards permission results to the tab's top fragment - which in two-pane
     * mode is this list, not the settings screen that asked. */
    @Override
    @SuppressWarnings("deprecation")
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Fragment detail = getChildFragmentManager().findFragmentById(R.id.settings_detail_container);
        if (detail != null)
            detail.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void registerCategory(View root, int sectionViewId, @IdRes int destinationId, boolean onlyForFullFlavour) {
        SettingsSectionView sectionView = root.findViewById(sectionViewId);

        if (onlyForFullFlavour && Tonio.isPure(requireContext())) {
            sectionView.setVisibility(View.GONE);
            return;
        }

        destinationBySection.put(sectionViewId, destinationId);
        sectionView.getHeaderView().setOnClickListener(v -> {
            if (twoPane)
                showInDetailPane(sectionViewId);
            else
                Navigation.findNavController(root).navigate(destinationId);
        });
    }

    private void showInDetailPane(int sectionViewId) {
        if (sectionViewId == selectedSectionId
                && getChildFragmentManager().findFragmentById(R.id.settings_detail_container) != null)
            return;
        Integer destinationId = destinationBySection.get(sectionViewId);
        if (destinationId == null) return;
        String fragmentClass = MainActivity.settingsFragmentClassFor(destinationId);
        if (fragmentClass == null) {
            myLogEE(null, "showInDetailPane: no settings fragment class for destination " + destinationId);
            return;
        }
        FragmentManager fm = getChildFragmentManager();
        Fragment detail = fm.getFragmentFactory().instantiate(requireContext().getClassLoader(), fragmentClass);
        fm.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.settings_detail_container, detail)
                .commit();
        selectedSectionId = sectionViewId;
        updateSelection();
    }

    private void updateSelection() {
        View root = getView();
        if (root == null) return;
        for (int sectionId : destinationBySection.keySet()) {
            SettingsSectionView sectionView = root.findViewById(sectionId);
            sectionView.setSelectedInPane(sectionId == selectedSectionId);
        }
    }

}
