package com.driot.bookplayer.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.FoldersRVAdapter;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.importexport.AutoBackupSnapshotManager;
import com.driot.bookplayer.importexport.ImportExportActivity;
import com.driot.bookplayer.player.PlaybackViewModel;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingFragment;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * The Library section's root/start destination - the folder list, extracted from MainActivity's
 * former inline implementation. MainActivity now hosts this (and ZikFileFragment/TtsReaderFragment/
 * CleanMemoryFragment/NearbyShareFragment) via its own NavHostFragment (main_library_nav_graph.xml)
 * - see [[radio_deeplink_applinks_fix]] plan. MainActivity keeps the Toolbar/options menu and its
 * launcher/deep-link/voice-search identity untouched; MainViewModel is Activity-scoped
 * (new ViewModelProvider(requireActivity())) so MainActivity's Sort menu item and onNewIntent()
 * scroll/refresh handling can keep operating on the same instance this Fragment observes.
 */
@AndroidEntryPoint
public class MainLibraryFragment extends LoggingFragment {

    private RecyclerView recyclerView;
    private View emptyView;
    private FoldersRVAdapter adapter;
    private MainViewModel mainVm;
    private boolean pendingScrollToTop = false;

    private final ActivityResultLauncher<Intent> autoBackupRecoveryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                int which = result.getData() != null
                        ? result.getData().getIntExtra(MsgBoxActivity.RESULT_WHICH, MsgBoxActivity.WHICH_NEGATIVE)
                        : MsgBoxActivity.WHICH_NEGATIVE;
                Pref.setAutoBackupRecoveryPrompted(true);
                if (which == MsgBoxActivity.WHICH_POSITIVE) {
                    String json = AutoBackupSnapshotManager.readSnapshot(requireContext());
                    if (json != null) {
                        Intent intent = new Intent(requireContext(), ImportExportActivity.class);
                        intent.putExtra(ImportExportActivity.EXTRA_MODE, ImportExportActivity.MODE_RESTORE);
                        intent.putExtra(ImportExportActivity.EXTRA_PRELOADED_JSON, json);
                        startActivity(intent);
                    }
                }
            });

    private final ActivityResultLauncher<Intent> modifyFolderLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || mainVm == null)
                    return;
                Intent data = result.getData();
                if (data.getLongExtra("deletedFolderId", -1) != -1
                        || data.getLongExtra("deleteInProgressFolderId", -1) != -1) {
                    mainVm.notifyFoldersListChanged();
                } else {
                    long folderId = data.getLongExtra(Intents.EXTRA_FOLDER_ID, -1);
                    if (folderId != -1) {
                        mainVm.notifyFolderChanged(folderId);
                    }
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recyclerview_folders);
        emptyView = view.findViewById(R.id.emptyView);
        if (recyclerView != null) {
            int span = getResources().getInteger(R.integer.classic_grid_span);
            GridLayoutManager glm = new GridLayoutManager(requireContext(), span);
            recyclerView.setLayoutManager(glm);
            recyclerView.setHasFixedSize(true);
            recyclerView.addItemDecoration(
                    new ViewHelper.SpacesItemDecoration(ViewHelper.dp(requireContext(), 0)));
        }

        adapter = new FoldersRVAdapter(requireContext());
        recyclerView.setAdapter(adapter);
        adapter.setModifyFolderLauncher(modifyFolderLauncher);

        PlaybackViewModel playbackVm = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);
        adapter.connectPlayback(getViewLifecycleOwner(), playbackVm.getState()); // adapter observe playback (highlight)

        mainVm = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        mainVm.getFolders().observe(getViewLifecycleOwner(), folders -> {
            if (folders == null)
                return;
            boolean isEmpty = folders.isEmpty();
            emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            if (isEmpty)
                setUpWelcomeMessageView();
            adapter.submitList(folders, () -> {
                if (pendingScrollToTop) {
                    pendingScrollToTop = false;
                    recyclerView.scrollToPosition(0);
                }
            });
        });

        mainVm.getScrollToTopEvent().observe(getViewLifecycleOwner(), evt -> {
            if (evt == null || evt.getContentIfNotHandled() == null)
                return;

            pendingScrollToTop = true;
            // Trigger an immediate scroll if the list is already there
            recyclerView.scrollToPosition(0);
        });

        Intent intent = requireActivity().getIntent();
        boolean wantScroll = intent != null && intent.getBooleanExtra("scrollToTop", false);
        if (wantScroll) {
            // either emit now or the list observer will run soon; both are fine
            mainVm.requestScrollToTopNow();
        }
    }

    private void setUpWelcomeMessageView() {
        myLog("no folders, setting up welcome message");
        Button btnWelcomeAddBook = emptyView.findViewById(R.id.btnWelcomeAddBook);
        btnWelcomeAddBook.setOnClickListener(v -> {
            startActivity(new Intent(requireContext().getApplicationContext(), AddBookHostActivity.class));
        });

        LinearLayout ll_welcome_item_podcasts_radio = emptyView.findViewById(R.id.ll_welcome_item_podcasts_radio);
        LinearLayout ll_welcome_item_browse = emptyView.findViewById(R.id.ll_welcome_item_browse);
        if (Tonio.isPure(requireContext())) {
            ll_welcome_item_podcasts_radio.setVisibility(View.GONE);
            ll_welcome_item_browse.setVisibility(View.GONE);
        } else {
            ll_welcome_item_podcasts_radio.setVisibility(View.VISIBLE);
            ll_welcome_item_browse.setVisibility(View.VISIBLE);
        }

        maybeOfferAutoBackupRecovery();
    }

    // Library looks freshly empty (matches the welcome-screen condition) and a snapshot exists
    // on disk (see AutoBackupSnapshotManager) - likely a restore from Android's own backup after
    // a lost/broken phone, since a manual restore via ImportExportActivity would already have
    // populated the library. Ask at most once (Pref.getAutoBackupRecoveryPrompted()).
    private void maybeOfferAutoBackupRecovery() {
        myLog("maybeOfferAutoBackupRecovery: alreadyPrompted=" + Pref.getAutoBackupRecoveryPrompted()
                + " hasSnapshot=" + AutoBackupSnapshotManager.hasSnapshot(requireContext()));
        if (Pref.getAutoBackupRecoveryPrompted() || !AutoBackupSnapshotManager.hasSnapshot(requireContext())) {
            return;
        }
        Intent intent = MsgBoxActivity.buildQuestion(requireContext(),
                getString(R.string.auto_backup_recovery_title),
                getString(R.string.auto_backup_recovery_desc),
                null,
                getString(R.string.auto_backup_recovery_positive), getString(R.string.auto_backup_recovery_negative));
        autoBackupRecoveryLauncher.launch(intent);
    }
}
