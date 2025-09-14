package com.driot.bookplayer.adapter;

import static com.driot.bookplayer.utils.Tonio.formatMemPadding;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.objects.FileWithSummary;
import com.driot.bookplayer.helpers.IconHelper;
import com.driot.bookplayer.utils.Tonio;
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
public class CleanMemoryRVAdapter extends LoggingRVAdapter<CleanMemoryRVAdapter.FileViewHolder> {

    private List<FileWithSummary> filesWithSummary;
    private final OnDeleteClickListener onDeleteClickListener;

    private final Context context;

    public CleanMemoryRVAdapter(Context context, OnDeleteClickListener onDeleteClickListener) {
        this.onDeleteClickListener = onDeleteClickListener;
        this.context = context;
    }

    public void setFilesWithSummary(List<FileWithSummary> list) {
        this.filesWithSummary = list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recyclerview_clean_memory, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        FileWithSummary item = filesWithSummary.get(position);
        File file = item.file;
        String zeSize = formatMemPadding(item.fileSizeMB, 5) + " " + context.getString(R.string.MB);

        holder.fileName.setText(file.getName());
        String percentDone = (int) item.percentDone + "%";
        holder.audioStatus.setText(percentDone);
        holder.fileSize.setText(zeSize);
        holder.fileDate.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(file.lastModified())));
        String extension = item.originalFile==null ? "" : Tonio.getExtension(item.originalFile);
        IconHelper.setSourceIcon(holder.ivSource, item.sourceLocation, extension);
        holder.deleteButton.setOnClickListener(v -> {
            myLog("Delete Click on " + file.getName());
            onDeleteClickListener.onDeleteClick(file, position);
        });
    }

    @Override
    public int getItemCount() {
        return filesWithSummary != null ? filesWithSummary.size() : 0;
    }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        TextView fileName;
        TextView fileSize;
        TextView fileDate;
        ImageButton deleteButton;
        TextView audioStatus;
        ImageView ivSource;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            fileName = itemView.findViewById(R.id.file_name);
            fileSize = itemView.findViewById(R.id.file_size);
            fileDate = itemView.findViewById(R.id.file_date);
            deleteButton = itemView.findViewById(R.id.delete_button);
            audioStatus = itemView.findViewById(R.id.audio_status);
            ivSource = itemView.findViewById(R.id.ivSource);
        }
    }

    public interface OnDeleteClickListener {
        void onDeleteClick(File file, int position);
    }

 }