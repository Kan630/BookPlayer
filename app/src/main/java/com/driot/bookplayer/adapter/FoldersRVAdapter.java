package com.driot.bookplayer.adapter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.ModifyFolderActivity;
import com.driot.bookplayer.activities.PlayActivity;
import com.driot.bookplayer.activities.PodcastEpisodeActivity;
import com.driot.bookplayer.activities.ZikFileActivity;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.player.PlayList;
import com.driot.bookplayer.helpers.IconHelper;
import com.driot.bookplayer.player.PlaybackUiState;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingRVAdapter;

import java.util.List;

import static com.driot.bookplayer.utils.Tonio.*;

public class FoldersRVAdapter extends LoggingRVAdapter<FoldersRVAdapter.FoldersViewHolder>
        implements View.OnClickListener, View.OnLongClickListener {

    private final Context mCtx;

    private final AsyncListDiffer<Folder> differ = new AsyncListDiffer<>(this, DIFF);

    // DiffUtil: compare by stable id; rebind if important fields change
    private static final DiffUtil.ItemCallback<Folder> DIFF = new DiffUtil.ItemCallback<Folder>() {
        @Override public boolean areItemsTheSame(@NonNull Folder a, @NonNull Folder b) {
            return a.getId() == b.getId();
        }
        @Override public boolean areContentsTheSame(@NonNull Folder a, @NonNull Folder b) {
            return a.getName().equals(b.getName())
                    && eq(a.getPercentdone(), b.getPercentdone())
                    && a.lLastAccess == b.lLastAccess
                    && a.getDuration() == b.getDuration()
                    && eq(a.image, b.image)
                    && eq(a.getSourceLocation(), b.getSourceLocation())
                    && eq(a.playType, b.playType);
        }
        @Override public Object getChangePayload(@NonNull Folder a, @NonNull Folder b) {
            // Return fine-grained payloads to avoid full row rebinds
            if (!eq(a.getPercentdone(), b.getPercentdone())) return "progress";
            if (a.lLastAccess != b.lLastAccess)              return "lastAccess";
            if (a.getDuration() != b.getDuration())          return "duration";
            if (!eq(a.image, b.image))                       return "image";
            if (!eq(a.getName(), b.getName()))               return "name";
            if (!eq(a.getSourceLocation(), b.getSourceLocation())
                    || !eq(a.playType, b.playType))          return "source";
            return null;
        }
        private boolean eq(Object x, Object y) { return x == null ? y == null : x.equals(y); }
    };


    // === Highlight état interne ===
    private long highlightedFolderId = -1;

    public void connectPlayback(@NonNull LifecycleOwner owner,
                                @NonNull LiveData<PlaybackUiState> playbackState) {
        playbackState.observe(owner, s -> {
            if (s == null) return;
            setHighlightedFolderId(s.folderId);
        });
    }

    private void setHighlightedFolderId(long newId) {
        if (newId == highlightedFolderId) return;
        int oldPos = findPositionByFolderId(highlightedFolderId);
        int newPos = findPositionByFolderId(newId);
        highlightedFolderId = newId;
        if (oldPos >= 0) notifyItemChanged(oldPos, "playstate");
        if (newPos >= 0) notifyItemChanged(newPos, "playstate");
    }

    public int findPositionByFolderId(long id) {
        if (id <= 0) return -1;
        List<Folder> list = differ.getCurrentList();
        for (int i = 0; i < list.size(); i++) {
            Folder f = list.get(i);
            if (f != null && f.getId() == id) return i;
        }
        return -1;
    }

    public FoldersRVAdapter(Context ctx) {
        super();
        this.mCtx = ctx;
        setHasStableIds(true);
    }

    // Use ListAdapter API
    @Override public long getItemId(int position) {
        Folder f = getItem(position);
        return (f == null) ? RecyclerView.NO_ID : f.getId();
    }

    public Folder getItem(int position) {
        List<Folder> list = differ.getCurrentList();
        return (position >= 0 && position < list.size()) ? list.get(position) : null;
    }

    public void submitList(List<Folder> folders) {
        differ.submitList(folders);
    }
    public void submitList(List<Folder> folders, @Nullable Runnable commit) {
        differ.submitList(folders, commit);
    }

    @Override public int getItemCount() {
        return differ.getCurrentList().size();
    }


    @NonNull
    @Override
    public FoldersViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(mCtx).inflate(R.layout.recyclerview_folders, parent, false);
        v.setOnClickListener(this);
        v.setOnLongClickListener(this);
        return new FoldersViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull FoldersViewHolder h, int position, @NonNull List<Object> payloads) {
        Folder folder = getItem(position);
        if (folder == null) return;

        if (!payloads.isEmpty()) {
            Object p = payloads.get(0);
            if ("playstate".equals(p)) {
                boolean activated = (folder.getId() == highlightedFolderId);
                h.rowContent.setActivated(activated); // applique le selector
                h.itemView.setActivated(activated);
                return;
            } else if ("progress".equals(p)) {
                h.textViewFilePercent.setText(FormatPercentString(folder.getPercentdone()));
                h.mProgressBar.setProgress(FormatPercentForProgressBar(folder.getPercentdone()));
                return;
            } else if ("lastAccess".equals(p)) {
                h.textViewFileLastAccess.setText(Tonio.formatLastAccess(folder.lLastAccess, mCtx));
                return;
            } else if ("duration".equals(p)) {
                h.textViewDuration.setText(formatTime(folder.getDuration()));
                return;
            } else if ("image".equals(p)) {
                if (folder.image != null) {
                    h.ivBookCover.setVisibility(View.VISIBLE);
                    Glide.with(h.ivBookCover.getContext()).load(folder.image).into(h.ivBookCover);
                } else {
                    h.ivBookCover.setVisibility(View.GONE);
                }
                return;
            } else if ("name".equals(p) || "source".equals(p)) {
                // petit refresh ciblé
                h.textViewFileName.setText(folder.getName());
                Option.applyUserTextAppearance(h.textViewFileName);
                IconHelper.setSourceIcon(h.ivSource, folder.getSourceLocation(), folder.playType);
                return;
            }
        }
        // fallback : bind complet
        onBindViewHolder(h, position);
    }

    @Override
    public void onBindViewHolder(@NonNull FoldersViewHolder h, int position) {
        Folder folder = getItem(position);
        if (folder == null) return;

        h.textViewFileName.setText(folder.getName());
        Option.applyUserTextAppearance(h.textViewFileName);

        h.textViewFilePercent.setText(FormatPercentString(folder.getPercentdone()));
        h.mProgressBar.setProgress(FormatPercentForProgressBar(folder.getPercentdone()));
        h.textViewFileLastAccess.setText(Tonio.formatLastAccess(folder.lLastAccess, mCtx));
        h.textViewDuration.setText(formatTime(folder.getDuration()));

        if (folder.image != null) {
            h.ivBookCover.setVisibility(View.VISIBLE);
            Glide.with(h.ivBookCover.getContext()).load(folder.image).into(h.ivBookCover);
        } else {
            h.ivBookCover.setVisibility(View.GONE);
        }
        IconHelper.setSourceIcon(h.ivSource, folder.getSourceLocation(), folder.playType);
        h.rowContent.setActivated(folder.getId() == highlightedFolderId);
        h.itemView.setActivated(folder.getId() == highlightedFolderId);
    }

    // Click handling at adapter-level so ViewHolder stays dumb
    @Override
    public void onClick(View v) {
        RecyclerView.ViewHolder vh = (RecyclerView.ViewHolder) v.getTag(R.id.view_holder_tag);
        if (vh == null) return;
        int pos = vh.getBindingAdapterPosition();
        if (pos == RecyclerView.NO_POSITION) return;

        Folder folder = getItem(pos);
        if (folder == null) return;
        myLogI("--- USER CLICKS on FOLDER/BOOK ---    position=" + pos + " - " + folder.getName());

        // DB work off main; UI nav back on main
        AppDatabase.databaseReadExecutor.execute(() -> {
            try {
                List<ZikFile> zikFilesList = AppDatabase.getDatabase(mCtx).ZikFileDao().getZikFiles(folder.getId());
                if (zikFilesList.isEmpty()) {
                    if (Var.SOURCE_LOCATION_PODCAST.equals(folder.getSourceLocation())) {
                        if (!Option.getPodcastOpenSpecificView()) {
                            postToast(mCtx.getString(R.string.no_episode_all_deleted));
                            //lets open the podcast specific view anyway
                        }
                    } else {
                        postToast(mCtx.getString(R.string.ErrorCouldNotLoadAudios_emptyfolder));
                        return;
                    }
                }

                if (Var.SOURCE_LOCATION_PODCAST.equals(folder.getSourceLocation())
                        && (Option.getPodcastOpenSpecificView() || (!Option.getPodcastOpenSpecificView() && zikFilesList.isEmpty()))
                ) {
                    Podcast p = AppDatabase.getDatabase(mCtx).PodcastDao().getPodcastByFolderId(folder.getId());
                    runOnUi(() -> mCtx.startActivity(new Intent(mCtx, PodcastEpisodeActivity.class).putExtra("podcast", p)));
                } else {
                    if (zikFilesList.size() > 1) {
                        runOnUi(() -> mCtx.startActivity(new Intent(mCtx, ZikFileActivity.class).putExtra("folder", folder)));
                    } else {
                        myLogD("Single file");
                        // SINGLE FILE: only reload if it's a different folder than what's playing
                        int currentFolderId = getCurrentFolderIdSafe();
                        myLogD("currentFolderId=" + currentFolderId + " - folder.getId()=" + folder.getId() + " -");
                        boolean sameBook = (currentFolderId == folder.getId());

                        if (!sameBook) {
                            // new selection → rebuild playlist and force reload in PlayActivity
                            PlayList.create(mCtx, zikFilesList, /*startIndex*/0);
                            runOnUi(() -> mCtx.startActivity(
                                    new Intent(mCtx, PlayActivity.class)
                                            .putExtra("ZikFile", zikFilesList.get(0))
                                            .putExtra("force_reload", true)
                            ));
                        } else {
                            myLogD("same book");
                            // same book → do NOT recreate playlist or reload; just bring player forward
                            runOnUi(() -> mCtx.startActivity(
                                    new Intent(mCtx, PlayActivity.class)
                                            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) // nice UX if it's already open
                            ));
                        }
                    }
                }
            } catch (Exception e) {
                myLogEE(e, "error getting nb of ZikFiles");
                postToast(mCtx.getString(R.string.ErrorCouldNotLoadAudios));
            }
        });
    }

    @Override
    public boolean onLongClick(View v) {
        RecyclerView.ViewHolder vh = (RecyclerView.ViewHolder) v.getTag(R.id.view_holder_tag);
        if (vh == null) return false;
        int pos = vh.getBindingAdapterPosition();
        if (pos == RecyclerView.NO_POSITION) return false;
        Folder folder = getItem(pos);
        if (folder == null) return false;
        runOnUi(() -> mCtx.startActivity(new Intent(mCtx, ModifyFolderActivity.class).putExtra("folder", folder)));
        return true;
    }

    private void postToast(String msg) {
        runOnUi(() -> myToastE(msg));
    }

    private void runOnUi(Runnable r) {
        if (mCtx instanceof Activity) ((Activity) mCtx).runOnUiThread(r);
        else r.run(); // best effort
    }

    class FoldersViewHolder extends RecyclerView.ViewHolder {
        View rowContent;
        TextView textViewFileName, textViewFileLastAccess, textViewFilePercent, textViewDuration;
        ProgressBar mProgressBar;
        ImageView ivBookCover, ivMemory, ivSource;

        FoldersViewHolder(View itemView) {
            super(itemView);
            rowContent = itemView.findViewById(R.id.stuff);
            textViewFileName = itemView.findViewById(R.id.tvBookName);
            textViewFilePercent = itemView.findViewById(R.id.textViewFilePercent);
            textViewFileLastAccess = itemView.findViewById(R.id.textViewFileLastAccess);
            textViewDuration = itemView.findViewById(R.id.textViewDuration);
            mProgressBar = itemView.findViewById(R.id.progressBar);
            ivMemory = itemView.findViewById(R.id.imageViewStorageIcon);
            ivBookCover = itemView.findViewById(R.id.ivBookCover);
            ivSource = itemView.findViewById(R.id.ivSource);

            // Store the holder on the root view so adapter can retrieve it in onClick/onLongClick
            itemView.setTag(R.id.view_holder_tag, this);
        }
    }

    private int getCurrentFolderIdSafe() {
        PlayList pl = PlayList.getInstance();
        if (pl == null) return -1;
        ZikFile z = pl.getZikFile();
        return (z != null) ? z.getIdFolder() : -1;
    }
}
