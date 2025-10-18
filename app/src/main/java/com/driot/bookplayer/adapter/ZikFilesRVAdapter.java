package com.driot.bookplayer.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.ModifyZikFileActivity;
import com.driot.bookplayer.activities.PlayActivity;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.player.AudioService;
import com.driot.bookplayer.player.PlayList;
import com.driot.bookplayer.player.PlaybackUiState;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingListAdapter;

import java.util.Objects;

public class ZikFilesRVAdapter extends LoggingListAdapter<ZikFile, ZikFilesRVAdapter.ZikFilesViewHolder> {

    public ZikFilesRVAdapter(@NonNull LifecycleOwner owner,
                             @NonNull LiveData<PlaybackUiState> playbackState) {
        super(DIFF);
        setHasStableIds(true);
        playbackState.observe(owner, s -> {
            if (s == null) return;
            setHighlightedTrackId(s.trackId); // triggers minimal payload updates
        });
    }

    private long highlightedTrackId = -1;

    private void setHighlightedTrackId(long newId) {
        if (newId == highlightedTrackId) return;
        int oldPos = findPositionById(highlightedTrackId);
        int newPos = findPositionById(newId);
        highlightedTrackId = newId;
        if (oldPos >= 0) notifyItemChanged(oldPos, "playstate");
        if (newPos >= 0) notifyItemChanged(newPos, "playstate");
    }

    private int findPositionById(long id) {
        if (id < 0) return -1;
        for (int i = 0; i < getItemCount(); i++) {
            ZikFile z = getItem(i);
            if (z != null && z.getId() == id) return i;
        }
        return -1;
    }

    private static final DiffUtil.ItemCallback<ZikFile> DIFF = new DiffUtil.ItemCallback<>() {
        @Override public boolean areItemsTheSame(@NonNull ZikFile a, @NonNull ZikFile b) {
            return a.getId() == b.getId();
        }
        @Override public boolean areContentsTheSame(@NonNull ZikFile a, @NonNull ZikFile b) {
            // Only fields you render; do NOT include “is playing” here.
            return a.equalsVisual(b);
        }
        @Override public Object getChangePayload(@NonNull ZikFile a, @NonNull ZikFile b) {
            if (a.getPercentdone() != b.getPercentdone()) return "progress";
            if (!safeEq(a.lLastAccess, b.lLastAccess))    return "lastAccess";
            if (a.getDuration() != b.getDuration())       return "duration";
            if (!safeEq(a.getDisplayName(), b.getDisplayName())) return "name";
            return null; // full bind fallback
        }
        private boolean safeEq(Object x, Object y) { return x == null ? y == null : x.equals(y); }
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

    // PARTIAL BIND
    @Override
    public void onBindViewHolder(@NonNull ZikFilesViewHolder h, int position, @NonNull java.util.List<Object> payloads) {
        ZikFile t = getItem(position);
        if (t == null) return;

        if (!payloads.isEmpty()) {
            Object p = payloads.get(0);
            Context ctx = h.itemView.getContext();
            switch (String.valueOf(p)) {
                case "playstate":
                    h.itemView.setActivated(t.getId() == highlightedTrackId);
                    return;
                case "progress":
                    h.textViewFilePercent.setText(Tonio.formatPercentString(t.getPercentdone()));
                    h.mProgressBar.setProgress(Tonio.formatPercentForProgressBar(t.getPercentdone()));
                    return;
                case "lastAccess":
                    h.textViewFileLastAccess.setText(Tonio.formatLastAccess(t.lLastAccess, ctx));
                    return;
                case "duration":
                    h.textViewDuration.setText(Tonio.formatTime(t.getDuration()));
                    return;
                case "name":
                    h.textViewFileName.setText(t.getDisplayName());
                    Option.applyUserTextAppearance(h.textViewFileName);
                    return;
            }
        }
        onBindViewHolder(h, position); // fallback to full bind
    }

    // FULL BIND
    @Override
    public void onBindViewHolder(@NonNull ZikFilesViewHolder holder, int position) {
        ZikFile t = getItem(position);
        if (t == null) return;

        Context ctx = holder.itemView.getContext();

        holder.textViewFileName.setText(t.getDisplayName());
        Option.applyUserTextAppearance(holder.textViewFileName);
        holder.textViewFilePercent.setText(Tonio.formatPercentString(t.getPercentdone()));
        holder.mProgressBar.setProgress(Tonio.formatPercentForProgressBar(t.getPercentdone()));
        holder.textViewFileLastAccess.setText(Tonio.formatLastAccess(t.lLastAccess, ctx));
        holder.textViewDuration.setText(Tonio.formatTime(t.getDuration()));

        holder.itemView.setActivated(t.getId() == highlightedTrackId);
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

            ZikFile clickedZikFile = getItem(position);
            if (clickedZikFile == null) return;
            Context ctx = itemView.getContext();

            AppDatabase.databaseReadExecutor.execute(()-> {
                //TTS ?
                final boolean isTTS;
                Folder folder = AppDatabase.getDatabase(ctx).folderDao().getById(clickedZikFile.getIdFolder());
                isTTS = Objects.equals(folder.playType, Var.PLAY_TYPE_TEXT);

                // was something playing ?
                PlaybackUiState lastUiState = AudioService.lastUiState;

                //is same track clicked ?
                PlayList pl = PlayList.getInstance();
                boolean sameTrack = (pl != null && pl.getZikFile() != null && pl.getZikFile().getId() == clickedZikFile.getId());  //keep getId() => needed !

                myLogI("USER CLICKS ZIKFILE : [" + clickedZikFile.getName() + "] - sameTrack=" + sameTrack + " - TTS=" + isTTS + " - lastUiState = " + lastUiState);

                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    if (lastUiState==null
                            || !lastUiState.playing
                            || !sameTrack
                    ) {
                        ContextCompat.startForegroundService(
                                ctx.getApplicationContext(),
                                new Intent(ctx.getApplicationContext(), AudioService.class)
                                        .setAction(Intents.ACTION_PLAY_FROM_TRACK)
                                        .putExtra(Intents.EXTRA_TRACK_ID, clickedZikFile.getId())
                                        .putExtra(Intents.EXTRA_CALLER, this.getClass().getSimpleName() + ".onClick() [ZikFilesRVAdapter]")
                                        .putExtra(Intents.EXTRA_FOREGROUND, true)
                        );
                    }

                    //maybe open PlayActivity
                    if (sameTrack || Option.getOpenPlayActivity() || isTTS) {
                        ctx.startActivity(new Intent(ctx, PlayActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                    }
                });
            });
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
    // Public helper to locate a track in the current list
    public int findPositionByTrackId(long trackId) {
        if (trackId <= 0) return -1;
        for (int i = 0; i < getItemCount(); i++) {
            ZikFile z = getItem(i);
            if (z != null && z.getId() == trackId) return i;
        }
        return -1;
    }
}
