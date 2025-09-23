package com.driot.bookplayer.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.ModifyZikFileActivity;
import com.driot.bookplayer.activities.PlayActivity;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.objects.PlayList;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingListAdapter;

public class ZikFilesRVAdapter extends LoggingListAdapter<ZikFile, ZikFilesRVAdapter.ZikFilesViewHolder> {

    public ZikFilesRVAdapter() {
        super(DIFF);
        setHasStableIds(true);
    }

    private static final DiffUtil.ItemCallback<ZikFile> DIFF = new DiffUtil.ItemCallback<ZikFile>() {
        @Override public boolean areItemsTheSame(@NonNull ZikFile a, @NonNull ZikFile b) {
            return a.getId() == b.getId();
        }
        @Override public boolean areContentsTheSame(@NonNull ZikFile a, @NonNull ZikFile b) {
            // Compare only what you render to avoid unnecessary redraws
            return a.equalsVisual(b);
        }
    };

    @Override public long getItemId(int position) {
        ZikFile z = getItem(position);
        return (z == null) ? RecyclerView.NO_ID : z.getId();
    }

    @NonNull
    @Override
    public ZikFilesViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.recyclerview_zikfiles, parent, false);
        return new ZikFilesViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ZikFilesViewHolder holder, int position) {
        ZikFile t = getItem(position);
        if (t == null) return;

        Context ctx = holder.itemView.getContext();

        holder.textViewFileName.setText(t.getDisplayName());
        Option.applyUserTextAppearance(holder.textViewFileName);
        holder.textViewFilePercent.setText(Tonio.FormatPercentString(t.getPercentdone()));
        holder.mProgressBar.setProgress(Tonio.FormatPercentForProgressBar(t.getPercentdone()));
        holder.textViewFileLastAccess.setText(Tonio.formatLastAccess(t.lLastAccess, ctx));
        holder.textViewDuration.setText(Tonio.formatTime(t.getDuration()));

        // Optional highlight for "now playing"
        PlayList pl = PlayList.getInstance();
        boolean isCurrent = pl != null && pl.getZikFile() != null && pl.getZikFile().getId() == t.getId();
        holder.itemView.setActivated(isCurrent);
    }

    private int getCurrentFolderIdSafe() {
        PlayList pl = PlayList.getInstance();
        if (pl == null) return -1;
        if (pl.getFolder() != null) return pl.getFolder().getId();
        ZikFile z = pl.getZikFile();
        return (z != null) ? z.getIdFolder() : -1;
    }

    private int getCurrentIndexSafe() {
        PlayList pl = PlayList.getInstance();
        return (pl != null) ? pl.getNumZikFile() : -1;
    }

    private void startPlayActivity(@NonNull Context ctx, boolean forceReload, ZikFile clicked) {
        Intent i = new Intent(ctx, PlayActivity.class);
        if (clicked != null) i.putExtra("ZikFile", clicked);
        if (forceReload) {
            i.putExtra("force_reload", true);
        } else {
            i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        }
        ctx.startActivity(i);
    }

    class ZikFilesViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

        TextView textViewFileName, textViewFileLastAccess, textViewFilePercent, textViewDuration;
        ProgressBar mProgressBar;

        ZikFilesViewHolder(View itemView) {
            super(itemView);
            textViewFileName = itemView.findViewById(R.id.tvBookName);
            textViewFilePercent = itemView.findViewById(R.id.textViewFilePercent);
            textViewFileLastAccess = itemView.findViewById(R.id.textViewFileLastAccess);
            textViewDuration = itemView.findViewById(R.id.textViewDuration);
            mProgressBar = itemView.findViewById(R.id.progressBar);

            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
        }

        @Override
        public void onClick(View view) {
            int position = getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return;

            ZikFile clicked = getItem(position);
            if (clicked == null) return;

            int clickedFolderId = clicked.getIdFolder();
            int currentFolderId = getCurrentFolderIdSafe();

            // Prefer ID-based equality, not position
            PlayList pl = PlayList.getInstance();
            boolean sameBook = (currentFolderId == clickedFolderId);
            boolean sameTrack = sameBook
                    && pl != null && pl.getZikFile() != null
                    && pl.getZikFile().getId() == clicked.getId();

            myLogI("USER CLICKS ZIKFILE : [" + clicked.getName() + "] - sameBook=" + sameBook + " sameTrack=" + sameTrack);

            Context ctx = itemView.getContext();

            if (!sameBook) {
                // Different book: rebuild playlist at clicked index and force engine reload
                PlayList.create(ctx, getCurrentList(), position);
                startPlayActivity(ctx, /*forceReload*/ true, clicked);
                return;
            }

            if (pl == null) {
                myToastEE(null, ctx.getString(R.string.error_reading_track));
                return;
            }

            if (!sameTrack) {
                // Same book, different track: set index and force reload
                pl.setNumZikFile(position);
                startPlayActivity(ctx, /*forceReload*/ true, clicked);
            } else {
                // Same book, same track: just show the player
                startPlayActivity(ctx, /*forceReload*/ false, null);
            }
        }

        @Override
        public boolean onLongClick(View view) {
            int pos = getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return false;
            ZikFile zikFile = getItem(pos);
            if (zikFile == null) return false;

            myLogI("onLongClick() : [" + zikFile.getName() + "] - [" + zikFile.getPath() + "/" + zikFile.getName() + "]");
            Context ctx = itemView.getContext();
            ctx.startActivity(new Intent(ctx, ModifyZikFileActivity.class).putExtra("ZikFile", zikFile));
            return true;
        }
    }
}
