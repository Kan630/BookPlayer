package com.driot.bookplayer.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.ZikFilesRVAdapter;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.player.PlaybackUiState;
import com.driot.bookplayer.player.PlaybackViewModel;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.util.List;

public class ZikFileActivity extends BaseBottomNavActivity {

    private RecyclerView recyclerView;
    private ZikFilesViewModel listVm;
    private PlaybackViewModel playbackVm;
    private ZikFilesRVAdapter adapter;
    private int lastObservedTrackId = -1;

    private Folder folder;
    private Folder lastFolder;
    private int folderId;
    private boolean activateChangeTrackOrder;

    private ImageButton ib_settings;

    // ensure we auto-scroll only once after the first list is loaded
    private boolean didAutoScrollToLast = false;

    @Override
    protected void onResume() {
        super.onResume();
        sendBroadcast(new Intent(Intents.ACTION_PING_UI));
    }

    @Override protected int getNavId() { return R.id.nav_library; }
    @Override protected int getLayoutResId() { return R.layout.activity_zikfile; }
    @Override protected boolean enableOngoingTaskOverlay() { return true; }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_zikfile);
        InsetHelper.apply(this);

        recyclerView = findViewById(R.id.recyclerview_zikfiles);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Read initial folder once; keep only the id and always re-read from DB
        folderId = getIntent().getIntExtra(Intents.EXTRA_FOLDER_ID, -1);
        if (!(folderId > 0)) {
            Folder initial = getIntent().getParcelableExtra(Intents.EXTRA_FOLDER);
            if (initial == null) {
                myToastEE(null, "onCreate : Intent folder == null");
                finish();
                return;
            }
            folderId = initial.getId();
        }
        activateChangeTrackOrder = getIntent().getBooleanExtra(Intents.EXTRA_ACTIVATE_CHANGE_TRACK_ORDER, false);

        listVm = new ViewModelProvider(this).get(ZikFilesViewModel.class);
        playbackVm = new ViewModelProvider(this).get(PlaybackViewModel.class);

        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0 /* no swipe */) {


            @Override
            public boolean isLongPressDragEnabled() { return false; } // use the button only

            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                int fromPos = from.getBindingAdapterPosition();
                int toPos   = to.getBindingAdapterPosition();
                adapter.moveItem(fromPos, toPos);
                adapter.notifyRowNumbersChanged(fromPos, toPos);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {}

            @Override
            public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                myLogI("---user reorder ---");
                // Persist order when the drag ends
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    AppDatabase db = AppDatabase.getDatabase(rv.getContext().getApplicationContext());
                    // Use the current visual order from the adapter
                    java.util.List<ZikFile> ordered = adapter.currentInRenderOrder();
                    db.zikFileDao().persistOrder(ordered);
                });
            }
        };

        ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
        adapter = new ZikFilesRVAdapter(this
                , playbackVm.getState()
                ,viewHolder -> touchHelper.startDrag(viewHolder)
                , activateChangeTrackOrder);

        recyclerView.setAdapter(adapter);
        touchHelper.attachToRecyclerView(recyclerView);

        ib_settings = findViewById(R.id.ib_settings);
        ib_settings.setOnClickListener(view -> {
            myLogI("--- User clicks SETTINGS ---");
            if (folder != null) {
                Intent it = new Intent(this, ModifyFolderActivity.class).putExtra(Intents.EXTRA_FOLDER, folder);
                modifyLauncher.launch(it);
            }
        });

        listVm.getFolderLive(folderId).observe(this, f -> {
            if (f == null) {
                myLogI("Folder " + folderId + " deleted — finishing.");
                finish();
            } else {
                folder = f;
                if (lastFolder==null || f.getId() != lastFolder.getId()) {
                    fillHeader(); // uses the latest folder
                    lastFolder = f;
                }
            }
        });


        // ViewModel + LiveData observation
        listVm.getZikFilesLive(folderId).observe(this, list -> {
            if (list == null) return;
            adapter.submitList(list);

            // Auto-scroll only on first load
            if (!didAutoScrollToLast && !list.isEmpty()) {
                didAutoScrollToLast = true;

                // Prefer the currently playing track if it's in this folder
                PlaybackUiState s = playbackVm.getState().getValue();
                if (s != null && s.folderId == folderId && s.trackId > 0) {
                    int pos = adapter.findPositionByTrackId(s.trackId);
                    if (pos >= 0) {
                        scrollRowNearTop(pos);
                        lastObservedTrackId = s.trackId;
                        return;
                    }
                }

                // Fallback: scroll to last listened audio
                scrollToLastPlayed(list);
            }
        });

        playbackVm.getState().observe(this, s -> {
            if (s == null) return;
            if (s.folderId != folderId) return;         // only care if user is viewing the same folder
            if (s.trackId <= 0) return;

            // Only scroll when the track actually changes
            if (s.trackId != lastObservedTrackId) {
                lastObservedTrackId = s.trackId;
                int pos = adapter.findPositionByTrackId(s.trackId);
                if (pos >= 0) {
                    // Optional: only scroll if it's off-screen
                    if (!isVisible(pos)) {
                        smoothScrollRowNearTop(pos);
                    }
                }
            }
        });
    }


    private void scrollToLastPlayed(List<ZikFile> list) {
        long maxTs = 0;
        int targetIndex = -1;

        for (int i = 0; i < list.size(); i++) {
            ZikFile z = list.get(i);
            if (z.lLastAccess != null && z.lLastAccess > maxTs) {
                maxTs = z.lLastAccess;
                targetIndex = i;
            }
        }
        if (targetIndex >= 0) {
            // scroll a little above the last item for context
            int pos = Math.max(targetIndex - 1, 0);
            recyclerView.scrollToPosition(pos);
            myLogD("Scrolling to last played audio at position: " + pos);
        }
    }

    private void fillHeader() {
        myLogD("fillHeader()");
        TextView textViewTitle = findViewById(R.id.textViewTitle);
        ImageView ivCover = findViewById(R.id.coverImage);

        textViewTitle.setText(folder.getName());

        if (folder.image != null && !folder.image.isEmpty()) {
            ivCover.setVisibility(ImageView.VISIBLE);

            ivCover.setImageDrawable(null); // force refresh
            Context gildeContext = ivCover.getContext();
            //Glide.with(gildeContext).load(StorageHelper.checkAndCleanImagePath(gildeContext, folder.image)).into(ivCover);
            Glide.with(gildeContext).load(folder.image).into(ivCover);
            ivCover.invalidate();
        } else {
            ivCover.setImageDrawable(null);
            ivCover.setVisibility(ImageView.GONE);
            myLog("no image for " + folder.getName());
        }

        ivCover.setOnClickListener(v -> {
            myLogI("--- User click header image --");
            goUserClickHeader();
        });
        textViewTitle.setOnClickListener(v -> {
            myLogI("--- User click header text --");
            goUserClickHeader();
        });
    }

    private void goUserClickHeader() {
        if (Var.SOURCE_LOCATION_PODCAST.equals(folder.getSourceLocation())) {
            AppDatabase.databaseReadExecutor.execute(() -> {
                Podcast podcast = AppDatabase.getDatabase(this).podcastDao().getPodcastByFolderId(folderId);
                if (podcast != null) {
                    myLogD("opening PodcastEpisodeActivity for podcast : " + podcast.title);
                    startActivity(new Intent(this, PodcastEpisodeActivity.class).putExtra("podcast", podcast));
                } else {
                    myLogI("No podcast linked to folder " + folderId);
                }
            });
        }
    }

    private final ActivityResultLauncher<Intent> modifyLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                myLogD("coming back from ModifyFolderActivity");
            });

    private boolean isVisible(int position) {
        RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
        if (!(lm instanceof LinearLayoutManager)) return false;
        LinearLayoutManager llm = (LinearLayoutManager) lm;
        int first = llm.findFirstVisibleItemPosition();
        int last = llm.findLastVisibleItemPosition();
        return position >= first && position <= last;
    }

    private void scrollRowNearTop(int position) {
        RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
        if (lm instanceof LinearLayoutManager) {
            ((LinearLayoutManager) lm).scrollToPositionWithOffset(position, dp(56));
        } else {
            recyclerView.scrollToPosition(position);
        }
    }

    private void smoothScrollRowNearTop(int position) {
        // For smooth behavior, we can first ask RecyclerView to smooth scroll;
        // most managers do not support offset in smooth mode, but this feels fine in practice.
        recyclerView.smoothScrollToPosition(position);
    }

    private int dp(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
