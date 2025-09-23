package com.driot.bookplayer.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.ModifyZikFileActivity;
import com.driot.bookplayer.activities.PlayActivity;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.objects.PlayList;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingRVAdapter;

import java.util.List;

public class ZikFilesRVAdapter extends LoggingRVAdapter<ZikFilesRVAdapter.ZikFilesViewHolder> {

    private final Context mCtx;
    private final List<ZikFile> zikFileList;

    public ZikFilesRVAdapter(Context mCtx, List<ZikFile> zikFileList) {
        this.mCtx = mCtx;
        this.zikFileList = zikFileList;
    }

    @NonNull
    @Override
    public ZikFilesViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mCtx).inflate(R.layout.recyclerview_zikfiles, parent, false);
        return new ZikFilesViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ZikFilesViewHolder holder, int position) {
        ZikFile t = zikFileList.get(position);
        holder.textViewFileName.setText(t.getDisplayName());
        Option.applyUserTextAppearance(holder.textViewFileName);
        holder.textViewFilePercent.setText(Tonio.FormatPercentString(t.getPercentdone()));
        holder.mProgressBar.setProgress(Tonio.FormatPercentForProgressBar(t.getPercentdone()));
        holder.textViewFileLastAccess.setText(Tonio.formatLastAccess(t.lLastAccess, mCtx));
        holder.textViewDuration.setText(Tonio.formatTime(t.getDuration()));
    }

    @Override
    public int getItemCount() {
        return zikFileList.size();
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

    private void startPlayActivity(boolean forceReload, ZikFile clicked) {
        Intent i = new Intent(mCtx, PlayActivity.class);
        if (clicked != null) i.putExtra("ZikFile", clicked);
        if (forceReload) {
            i.putExtra("force_reload", true);
        } else {
            // same track: just bring the existing activity forward if it exists
            i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        }
        mCtx.startActivity(i);
    }

    class ZikFilesViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

        TextView textViewFileName, textViewFileLastAccess, textViewFilePercent, textViewDuration;
        ProgressBar mProgressBar;

        public ZikFilesViewHolder(View itemView) {
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

            ZikFile clicked = zikFileList.get(position);
            int clickedFolderId = clicked.getIdFolder();

            int currentFolderId = getCurrentFolderIdSafe();
            int currentIndex = getCurrentIndexSafe();

            boolean sameBook = (currentFolderId == clickedFolderId);
            boolean sameTrack = sameBook && (currentIndex == position);

            myLogI("USER CLICKS ZIKFILE : [" + clicked.getName() + "] - sameBook=" + sameBook + " sameTrack=" + sameTrack);

            if (!sameBook) {
                // Different book: rebuild playlist at clicked index and force engine reload
                PlayList.create(mCtx, zikFileList, position);
                startPlayActivity(/*forceReload*/ true, clicked);
                return;
            }

            // Same book
            PlayList pl = PlayList.getInstance();
            if (pl == null) {
                myToastEE(null, mCtx.getString(R.string.error_reading_track));
                return;
            }

            if (!sameTrack) {
                // Same book, different track: set index and force reload
                pl.setNumZikFile(position);
                    startPlayActivity(/*forceReload*/ true, clicked);
            } else {
                // Same book, same track: no reload; just show the player
                startPlayActivity(/*forceReload*/ false, null);
            }
        }

        @Override
        public boolean onLongClick(View view) {
            int pos = getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return false;
            ZikFile zikFile = zikFileList.get(pos);
            myLogI("onLongClick() : [" + zikFile.getName() + "] - [" + zikFile.getPath() + "/" + zikFile.getName() + "]");
            mCtx.startActivity(new Intent(mCtx, ModifyZikFileActivity.class).putExtra("ZikFile", zikFile));
            return true;
        }
    }
}
