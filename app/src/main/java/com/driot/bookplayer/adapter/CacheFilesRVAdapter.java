package com.driot.bookplayer.adapter;

import static com.driot.bookplayer.utils.Tonio.formatMem;
import static com.driot.bookplayer.utils.Utils.getCustomLength;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.utils.log.LoggingRVAdapter;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;


/**
 * created by Antoine Driot -- antoine.driot.com -- on 26/05/2024
 *
 *RecyclerViewAdapter
 */
public class CacheFilesRVAdapter extends LoggingRVAdapter<CacheFilesRVAdapter.FileViewHolder> {

    private List<File> filesOnDisk;
    private List<ZikFile> distinctZikFilePaths;
    private final OnDeleteClickListener onDeleteClickListener;

    private final Context context;

    public CacheFilesRVAdapter(Context context, OnDeleteClickListener onDeleteClickListener) {
        this.onDeleteClickListener = onDeleteClickListener;
        this.context = context;
    }

    public void setDistinctZikFilePaths(List<ZikFile> distinctZikFilePaths) {
        this.distinctZikFilePaths = distinctZikFilePaths;
        notifyDataSetChanged();
    }

    public void setFilesOnDisk(List<File> filesOnDisk) {
        this.filesOnDisk = filesOnDisk;
        notifyDataSetChanged();
    }


    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recyclerview_cachefiles, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        File file = filesOnDisk.get(position);
        String zeSize = formatMem(getCustomLength(file) / 1024 / 1024, 5) + " " + context.getString(R.string.MB);

        holder.fileName.setText(file.getName());
        holder.audioStatus.setText(getAudioStatus(file));
        holder.fileSize.setText(zeSize);
        holder.fileDate.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(file.lastModified())));

        holder.deleteButton.setOnClickListener(v -> {
            myLog("Delete Click on " + file.getName());
            onDeleteClickListener.onDeleteClick(file, position);
        });
    }

    private String getAudioStatus(File file) {
        boolean bFound = false;
        double percentDone = 0;
        String zeAudioStatus = "...";
        if (!(distinctZikFilePaths == null)) {
            for (ZikFile f : distinctZikFilePaths) {
                if (file.getPath().equals(f.getPath())) {
                    percentDone = f.getPercentdone();
                    bFound = true;
                    break;
                }
            }
            if (bFound) {
                zeAudioStatus = (int) percentDone + "%";
            } else {
                zeAudioStatus = "-KO-";
            }
        } else {
            zeAudioStatus = "init";
        }
        //myLog("getAudioStatus for [" + file.getPath() + "] => [" + zeAudioStatus + "]");
        return zeAudioStatus;
    }

    @Override
    public int getItemCount() {
        if (filesOnDisk == null) {
            return 0;
        } else {
            return filesOnDisk.size();
        }
    }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        TextView fileName;
        TextView fileSize;
        TextView fileDate;
        //androidx.appcompat.widget.AppCompatImageButton deleteButton;
        ImageButton deleteButton;
        TextView audioStatus;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            fileName = itemView.findViewById(R.id.file_name);
            fileSize = itemView.findViewById(R.id.file_size);
            fileDate = itemView.findViewById(R.id.file_date);
            deleteButton = itemView.findViewById(R.id.delete_button);
            audioStatus = itemView.findViewById(R.id.audio_status);
        }
    }

    public interface OnDeleteClickListener {
        void onDeleteClick(File file, int position);
    }

 }