package com.driot.bookplayer.settings.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.Navigation;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.AdminActivity;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingFragment;
import com.driot.bookplayer.views.SettingsSectionView;

/**
 * Top-level list of settings categories - the start destination of settings_nav_graph.xml,
 * hosted by SettingsHostActivity. Tapping a category navigates (via NavController) to its
 * destination; each category fragment renders its own title (ARG_SHOW_LOCAL_TITLE defaults
 * to true when reached with no arguments, which is the case for every in-graph navigation
 * here). Converted from the former SettingsActivity's category-list half - see
 * [[radio_deeplink_applinks_fix]].
 */
public class SettingsCategoryListFragment extends LoggingFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings_category_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

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
    }

    private void registerCategory(View root, int sectionViewId, @IdRes int destinationId, boolean onlyForFullFlavour) {
        SettingsSectionView sectionView = root.findViewById(sectionViewId);

        if (onlyForFullFlavour && Tonio.isPure(requireContext())) {
            sectionView.setVisibility(View.GONE);
            return;
        }

        sectionView.getHeaderView().setOnClickListener(v ->
                Navigation.findNavController(root).navigate(destinationId));
    }

}
