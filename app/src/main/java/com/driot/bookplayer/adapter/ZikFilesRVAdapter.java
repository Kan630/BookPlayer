package com.driot.bookplayer.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.ModifyZikFileActivity;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.player.heatmaps.PlayHeatMapView;
import com.driot.bookplayer.player.heatmaps.PlayTickDao;
import com.driot.bookplayer.player.heatmaps.PlayTickHeatMapHelper;
import com.driot.bookplayer.player.heatmaps.PlayTickBucketMerger;
import com.driot.bookplayer.player.heatmaps.PlayTickBucket;
import com.driot.bookplayer.player.heatmaps.PlaySession;
import com.driot.bookplayer.player.heatmaps.PlaySessionDao;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.player.PlaybackUiState;
import com.driot.bookplayer.player.StartPlayHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingListAdapter;


import java.util.List;
import java.util.Locale;

public class ZikFilesRVAdapter extends LoggingListAdapter<ZikFile, ZikFilesRVAdapter.ZikFilesViewHolder> {


    public interface OnStartDragListener {
        void onStartDrag(RecyclerView.ViewHolder vh);
    }

    private final OnStartDragListener dragStart;
    private final boolean activateReOrder;
    private boolean displayHeatMaps = Option.getUseHeatmapForTracksActivity();


    public ZikFilesRVAdapter(@NonNull LifecycleOwner owner,
                             @NonNull LiveData<PlaybackUiState> playbackState,
                             @NonNull OnStartDragListener dragStart,
                             boolean activateReOrder
    ) {
        super(DIFF);
        this.dragStart = dragStart;
        this.activateReOrder = activateReOrder;
        setHasStableIds(true);
        playbackState.observe(owner, s -> {
            if (s == null) return;
            updatePlayingState(s.trackId, s.positionMs, s.durationMs);
        });
    }

    @NonNull
    @Override
    public ZikFilesViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.recyclerview_zikfiles, parent, false);
        return new ZikFilesViewHolder(view);
    }


    ///  SORT - Drag and Drop - Called by Activity

    private static final String PAYLOAD_ROWNUM = "rownum";
    public void moveItem(int fromPosition, int toPosition) {
        if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) return;
        java.util.ArrayList<ZikFile> copy = new java.util.ArrayList<>(getCurrentList());
        java.util.Collections.swap(copy, fromPosition, toPosition);
        // Run after diff is applied, so positions are final when we update numbers
        submitList(copy, () -> notifyItemRangeChanged(0, getItemCount(), PAYLOAD_ROWNUM));
    }
    public void notifyRowNumbersChanged(int fromPosition, int toPosition) {
        if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) return;
        int start = Math.min(fromPosition, toPosition);
        int count = Math.abs(toPosition - fromPosition) + 1;
        notifyItemRangeChanged(start, count, PAYLOAD_ROWNUM);
    }
    public java.util.List<ZikFile> currentInRenderOrder() {
        return getCurrentList();
    }

    ///  TRACK HIGHLIGHT + PLAYING POSITION (triangle)

    private long highlightedTrackId = -1;
    private float playingPositionNorm = -1f;

    private void updatePlayingState(long trackId, long positionMs, long durationMs) {
        float norm = (durationMs > 0) ? (float) Math.min(positionMs, durationMs) / durationMs : -1f;
        int newPos = findPositionById(trackId);
        boolean trackChanged = (trackId != highlightedTrackId);
        boolean positionChanged = (trackId == highlightedTrackId && Math.abs(norm - playingPositionNorm) >= 0.001f);
        if (!trackChanged && !positionChanged) return;

        int oldPos = findPositionById(highlightedTrackId);
        highlightedTrackId = trackId;
        playingPositionNorm = norm;
        if (oldPos >= 0) notifyItemChanged(oldPos, "playstate");
        if (newPos >= 0 && newPos != oldPos) notifyItemChanged(newPos, "playstate");
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

    // PARTIAL BIND
    @Override
    public void onBindViewHolder(@NonNull ZikFilesViewHolder h, int position, @NonNull java.util.List<Object> payloads) {
        ZikFile t = getItem(position);
        if (t == null) return;
        h.ibSort.setText(String.valueOf(position + 1));

        if (!payloads.isEmpty()) {
            Object p = payloads.get(0);
            Context ctx = h.itemView.getContext();
            switch (String.valueOf(p)) {
                case PAYLOAD_ROWNUM:
                    h.ibSort.setText(String.valueOf(position + 1));
                case "playstate":
                    h.itemView.setActivated(t.getId() == highlightedTrackId);
                    if (displayHeatMaps && h.heatMapView != null) {
                        boolean isCurrent = t.getId() == highlightedTrackId;
                        if (isCurrent) {
                            h.heatMapView.setPlayingCursor(playingPositionNorm);
                            h.heatMapView.setCursors(new float[0]);
                        } else {
                            h.heatMapView.setPlayingCursor(-1f);
                            long dur = (long) t.getDuration();
                            float[] cursors = (t.getPosition() > 0 && dur > 0)
                                    ? new float[]{(float) t.getPosition() / dur}
                                    : new float[0];
                            h.heatMapView.setCursors(cursors);
                        }
                    }
                    return;
                case "progress":
                    h.textViewFilePercent.setText(Tonio.formatPercentString(t.getPercentdone()));
                    h.mProgressBar.setProgress(Tonio.formatPercentForProgressBar(t.getPercentdone()));
                    if (displayHeatMaps) {
                        boolean isCurrent = t.getId() == highlightedTrackId;
                        float playingNorm = isCurrent ? playingPositionNorm : -1f;
                        h.updateHeatMap(t, isCurrent, playingNorm);
                        h.mProgressBar.setVisibility(View.GONE);
                        h.heatMapView.setVisibility(View.VISIBLE);
                    } else {
                        h.mProgressBar.setVisibility(View.VISIBLE);
                        h.heatMapView.setVisibility(View.GONE);
                    }
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
        ZikFile zikFile = getItem(position);
        if (zikFile == null) return;
        holder.ibSort.setText(String.valueOf(position + 1));

        Context ctx = holder.itemView.getContext();

        holder.textViewFileName.setText(zikFile.getDisplayName());
        Option.applyUserTextAppearance(holder.textViewFileName);
        holder.textViewFilePercent.setText(Tonio.formatPercentString(zikFile.getPercentdone()));
        holder.textViewFileLastAccess.setText(Tonio.formatLastAccess(zikFile.lLastAccess, ctx));
        holder.textViewDuration.setText(Tonio.formatTime(zikFile.getDuration()));

        holder.itemView.setActivated(zikFile.getId() == highlightedTrackId);

        if (displayHeatMaps) {
            boolean isCurrent = zikFile.getId() == highlightedTrackId;
            float playingNorm = isCurrent ? playingPositionNorm : -1f;
            holder.updateHeatMap(zikFile, isCurrent, playingNorm);
            holder.mProgressBar.setVisibility(View.GONE);
            holder.heatMapView.setVisibility(View.VISIBLE);
        } else {
            holder.mProgressBar.setProgress(Tonio.formatPercentForProgressBar(zikFile.getPercentdone()));
            holder.mProgressBar.setVisibility(View.VISIBLE);
            holder.heatMapView.setVisibility(View.GONE);
        }
    }

    class ZikFilesViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

        TextView textViewFileName, textViewFileLastAccess, textViewFilePercent, textViewDuration;
        ProgressBar mProgressBar;
        TextView ibSort;
        PlayHeatMapView heatMapView;

        void updateHeatMap(ZikFile zikFile, boolean isCurrentTrack, float playingPositionNorm) {
            if (heatMapView == null) return;

            final long zikFileId = zikFile.getId();
            final long durationMs = (long) zikFile.getDuration() ;

            if (durationMs <= 0) {
                heatMapView.setIntensities(new float[0]);
                return;
            }

            final Context appCtx = itemView.getContext().getApplicationContext();
            int nbBuckets = Math.max(1, Math.min((int) durationMs /1000, Var.HEATMAP_PROGRESSBAR_BUCKET_SIZE));

            AppDatabase.databaseReadExecutor.execute(() -> {
                PlayTickDao dao = AppDatabase.getInstance(appCtx).playTickDao();
                long bucketSizeMs = Math.max(1L, durationMs / nbBuckets);

                PlayTickDao tickDao = AppDatabase.getInstance(appCtx).playTickDao();
                PlaySessionDao sessionDao = AppDatabase.getInstance(appCtx).playSessionDao();

                List<PlayTickBucket> tickBuckets = tickDao.getBucketCounts(zikFileId, bucketSizeMs);
                if (PlayTickHeatMapHelper.LOG_DEBUG_PLAYTICK) myLog(zikFile.getDisplayName());
                if (PlayTickHeatMapHelper.LOG_DEBUG_PLAYTICK) myLogD(Tonio.lpad(tickBuckets.size(), 3) + " tick: " + tickBuckets.toString());


                // Get sessions and convert to buckets
                List<PlaySession> sessions = sessionDao.getAllForFile(zikFileId);
                List<PlayTickBucket> sessionBuckets = PlaySessionDao.getBucketCounts(sessions, bucketSizeMs);
                if (PlayTickHeatMapHelper.LOG_DEBUG_PLAYTICK) myLogD(Tonio.lpad(sessionBuckets.size(), 3) + " sess: " + sessionBuckets.toString());

                List<PlayTickBucket> buckets = PlayTickBucketMerger.merge(sessionBuckets, tickBuckets);
                if (PlayTickHeatMapHelper.LOG_DEBUG_PLAYTICK) myLogD(Tonio.lpad(buckets.size(), 3) + " merg: " + buckets.toString());

                final float[] intensities = PlayTickHeatMapHelper.computeIntensities(buckets, durationMs, nbBuckets);
                if (PlayTickHeatMapHelper.LOG_DEBUG_PLAYTICK) myLogD("----");

                // Come back on UI thread and check that this ViewHolder still represents the same item
                itemView.post(() -> {
                    int pos = getBindingAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return;

                    ZikFile current = ZikFilesRVAdapter.this.getItem(pos);
                    if (current == null || current.getId() != zikFileId) return;

                    heatMapView.setIntensities(intensities);
                    if (isCurrentTrack) {
                        heatMapView.setPlayingCursor(playingPositionNorm);
                        heatMapView.setCursors(new float[0]);
                    } else {
                        heatMapView.setPlayingCursor(-1f);
                        final float[] cursors;
                        if (zikFile.getPosition() > 0) {
                            cursors = new float[] { (float) zikFile.getPosition() / durationMs };
                        } else {
                            cursors = new float[0];
                        }
                        heatMapView.setCursors(cursors);
                    }
                });
            });
        }


        ZikFilesViewHolder(View itemView) {
            super(itemView);
            textViewFileName = itemView.findViewById(R.id.tvBookName);
            textViewFilePercent = itemView.findViewById(R.id.textViewFilePercent);
            textViewFileLastAccess = itemView.findViewById(R.id.textViewFileLastAccess);
            textViewDuration = itemView.findViewById(R.id.textViewDuration);
            mProgressBar = itemView.findViewById(R.id.progressBar);
            heatMapView = itemView.findViewById(R.id.heatMapView);
            ibSort = itemView.findViewById(R.id.ib_drag_sort);
            if (ibSort != null) {
                if (activateReOrder) {
                    ibSort.setOnTouchListener((v, e) -> {
                        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                            if (dragStart != null) dragStart.onStartDrag(this);
                            return true;  // consume so itemView.onClick won't fire
                        }
                        ibSort.setVisibility(View.VISIBLE);
                        return false;
                    });
                } else {
                    ibSort.setVisibility(View.GONE);
                }
            }

            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
        }

        @Override
        public void onClick(View view) {
            int position = getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return;

            ZikFile clickedZikFile = getItem(position);
            if (clickedZikFile == null) return;
            StartPlayHelper.onZikFileClick(itemView.getContext(), clickedZikFile, this.getClass().getSimpleName() + ".onClick() [ZikFilesRVAdapter]");
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

    public void refreshDisplayHeatMaps() {
        boolean newValue = Option.getUseHeatmapForTracksActivity();
        if (newValue == displayHeatMaps) return; // nothing to do
        displayHeatMaps = newValue;
        notifyDataSetChanged(); // rebind all rows so visibility + heatmaps update
    }

}
