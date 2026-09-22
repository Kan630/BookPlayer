package com.driot.bookplayer.settings.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.MainActivity;
import com.driot.bookplayer.activities.HelpActivity;
import com.driot.bookplayer.activities.StatsActivity;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.imports.ImportHelper;
import com.driot.bookplayer.nav.NavHelper;
import com.driot.bookplayer.player.PlaybackCommands;
import com.driot.bookplayer.importexport.FullBackupActivity;
import com.driot.bookplayer.utils.MsgBox;
import com.driot.bookplayer.utils.log.LoggingFragment;

import java.io.File;

import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

@AndroidEntryPoint
public class UtilitiesSettingsFragment extends LoggingFragment {

    private static final int REQ_DELETE_CACHE = 2002;
    private static final int REQ_DELETE_SYSTEM_CACHE = 2003;

    @Inject NavHelper navHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_settings_utilities, container, false);

        // Hide local title when embedded
        boolean showLocalTitle = true;
        Bundle args = getArguments();
        if (args != null)
            showLocalTitle = args.getBoolean("ARG_SHOW_LOCAL_TITLE", true);
        View titleContainer = root.findViewById(R.id.ll_title);
        if (titleContainer != null)
            titleContainer.setVisibility(showLocalTitle ? View.VISIBLE : View.GONE);

        root.findViewById(R.id.btn_reset_settings_values_to_default).setOnClickListener(v -> {
            myLogI("--- user clicks RESET SETTINGS to DEFAULT ---");

            Activity act = requireActivity();

            // 1️⃣ Get the launch intent *before* finishing the activity
            Intent restartIntent = act.getPackageManager()
                    .getLaunchIntentForPackage(act.getPackageName());
            if (restartIntent == null)
                return; // should never happen
            restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            final android.content.Context appCtx = act.getApplicationContext();

            // 2️⃣ Run the deletion *after* the current Activity is closed,
            // to avoid any onPause()/onDestroy() code rewriting prefs.
            act.finish(); // close current activity window

            // Give the system a short beat to finish (optional but avoids race conditions)
            act.getWindow().getDecorView().postDelayed(() -> {
                Option.resetToDefaults(appCtx);
                startActivity(restartIntent); // reopen app fresh
            }, 150);
        });

        root.findViewById(R.id.btn_reset_settings_values_for_power_user).setOnClickListener(v -> {
            myLogI("--- user clicks RESET SETTINGS for POWER USER ---");

            Activity act = requireActivity();

            // 1️⃣ Get the launch intent *before* finishing the activity
            Intent restartIntent = act.getPackageManager()
                    .getLaunchIntentForPackage(act.getPackageName());
            if (restartIntent == null)
                return; // should never happen
            restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            final android.content.Context appCtx = act.getApplicationContext();

            // 2️⃣ Run the deletion *after* the current Activity is closed,
            // to avoid any onPause()/onDestroy() code rewriting prefs.
            act.finish(); // close current activity window

            // Give the system a short beat to finish (optional but avoids race conditions)
            act.getWindow().getDecorView().postDelayed(() -> {
                Option.resetToDefaults(appCtx);
                Option.setCopyFile(false);
                Option.setOpenWith_all(true);
                Option.setStopAudioIfUserClosesApp(false);
                Option.setOpenPlayActivity(false);
                Option.setRadioLandingScreen(Option.RADIO_LANDING_FAVORITES);
                Option.setPodcastLandingScreen(Option.PODCAST_LANDING_FAVORITES);
                startActivity(restartIntent); // reopen app fresh
            }, 150);
        });

        root.findViewById(R.id.btn_reset_app).setOnClickListener(v -> {
            myLogI("--- user clicks RESET APP ---");
            ImportHelper.cancelCurrentImport(requireContext().getApplicationContext());
            ImportHelper.cancelAll_in_DB(requireContext().getApplicationContext());
            navHelper.resetAddBookNav();
            PlaybackCommands.stop(requireContext().getApplicationContext());
            myToast(getString(com.driot.bookplayer.R.string.app_reset_done));
        });

        root.findViewById(R.id.btn_backup_library).setOnClickListener(v -> {
            Intent it = new Intent(getActivity(), FullBackupActivity.class);
            it.putExtra(FullBackupActivity.EXTRA_MODE, FullBackupActivity.MODE_BACKUP);
            startActivity(it);
        });

        root.findViewById(R.id.btn_restore_library).setOnClickListener(v -> {
            Intent it = new Intent(getActivity(), FullBackupActivity.class);
            it.putExtra(FullBackupActivity.EXTRA_MODE, FullBackupActivity.MODE_RESTORE);
            startActivity(it);
        });

        root.findViewById(R.id.btn_app_info).setOnClickListener(v -> openAppInfo());
        root.findViewById(R.id.btn_delete_cache).setOnClickListener(v -> deleteCacheClick());
        root.findViewById(R.id.btn_delete_system_cache).setOnClickListener(v -> deleteSystemCacheClick());

        root.findViewById(R.id.btn_quick_access_stats).setOnClickListener(v ->
                startActivity(new Intent(getActivity(), StatsActivity.class)));
        root.findViewById(R.id.btn_quick_access_cleaning).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), MainActivity.class)
                    .putExtra(MainActivity.EXTRA_NAV_TAB_ID, R.id.nav_library)
                    .putExtra(MainActivity.EXTRA_NAV_DEST_ID, R.id.cleanMemoryFragment)
                    .putExtra(MainActivity.EXTRA_NAV_DIRECT_LINK, true)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });
        root.findViewById(R.id.btn_quick_access_manual).setOnClickListener(v ->
                startActivity(new Intent(getActivity(), HelpActivity.class)));
        root.findViewById(R.id.btn_quick_access_website).setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Var.WEBSITE_URL))));

        return root;
    }

    private void openAppInfo() {
        myLogI("--- user clicks OPEN APP INFO ---");
        try {
            Context context = requireContext();
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", context.getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        } catch (Exception e) {
            myLogEE(e, "openAppInfo()");
        }
    }

    private void deleteCacheClick() {
        myLogI("--- user clicks DELETE CACHE ---");
        MsgBox.ask(this,
                getString(R.string.AskDelete_popupTitle),
                getString(R.string.DeleteCache_AskConfirm),
                null,
                getString(android.R.string.ok),
                getString(android.R.string.cancel),
                REQ_DELETE_CACHE);
    }

    private void deleteCachedImages() {
        Context context = requireContext().getApplicationContext();
        File dir = new File(requireContext().getFilesDir(), "images");
        FileHelper.RemoveCachedImages(context, dir);
    }

    private void deleteSystemCacheClick() {
        myLogI("--- user clicks DELETE SYSTEM CACHE ---");
        MsgBox.ask(this,
                getString(R.string.AskDelete_popupTitle),
                getString(R.string.DeleteSystemCache_AskConfirm),
                null,
                getString(android.R.string.ok),
                getString(android.R.string.cancel),
                REQ_DELETE_SYSTEM_CACHE);
    }

    private void deleteSystemCache() {
        // Only raw-delete the subfolder(s) under getCacheDir() that we manage ourselves.
        // Never touch Glide's own disk cache directory there: it's open in-process with
        // its own journal, and deleting its files out from under it desyncs the journal,
        // breaking image loads until the app restarts.
        Context appCtx = requireContext().getApplicationContext();
        AppDatabase.databaseReadExecutor.execute(() -> FileHelper.deleteFolderChildren(ImageHelper.getEpisodeCoverOsCacheDir(appCtx)));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQ_DELETE_CACHE) {
                deleteCachedImages();
            } else if (requestCode == REQ_DELETE_SYSTEM_CACHE) {
                deleteSystemCache();
            }
        }
    }
}
