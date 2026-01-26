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
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.objects.FolderWithSummary;
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

    private List<FolderWithSummary> filesWithSummary;
    private final OnDeleteClickListener onDeleteClickListener;

    private final Context context;

    public CleanMemoryRVAdapter(Context context, OnDeleteClickListener onDeleteClickListener) {
        this.onDeleteClickListener = onDeleteClickListener;
        this.context = context;
    }

    public void setFilesWithSummary(List<FolderWithSummary> list) {
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
        FolderWithSummary item = filesWithSummary.get(position);
        File file = item.file;
        String zeSize = Tonio.getReadableSizeForCleanActivity(item.folderSizeInBytes);

        boolean hasFolder = item.folderName != null && !item.folderName.isEmpty();
        String displayName = hasFolder ? item.folderName : file.getName();
        holder.bookName.setText(displayName);
        int nameColor = hasFolder
                ? ContextCompat.getColor(context, R.color.bp_onSurface)
                : ContextCompat.getColor(context, R.color.red_500);
        holder.bookName.setTextColor(nameColor);
        String percentDone = (int) item.percentDone + "%";
        holder.audioStatus.setText(percentDone);
        holder.fileSize.setText(zeSize);
        holder.fileDate.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(file.lastModified())));

        IconHelper.setSourceIcon(holder.ivSource, item.sourceLocation, item.playType);

        if (item.image != null) {
            holder.iv_cover.setVisibility(View.VISIBLE);
            Glide.with(holder.iv_cover.getContext()).load(item.image).into(holder.iv_cover);
        } else {
            holder.iv_cover.setVisibility(View.GONE);
        }

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
        TextView bookName;
        TextView fileSize;
        TextView fileDate;
        ImageButton deleteButton;
        TextView audioStatus;
        ImageView ivSource, iv_cover;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            bookName = itemView.findViewById(R.id.book_name);
            fileSize = itemView.findViewById(R.id.file_size);
            fileDate = itemView.findViewById(R.id.file_date);
            deleteButton = itemView.findViewById(R.id.delete_button);
            audioStatus = itemView.findViewById(R.id.audio_status);
            ivSource = itemView.findViewById(R.id.ivSource);
            iv_cover = itemView.findViewById(R.id.iv_cover);
        }
    }

    public interface OnDeleteClickListener {
        void onDeleteClick(File file, int position);
    }

 }