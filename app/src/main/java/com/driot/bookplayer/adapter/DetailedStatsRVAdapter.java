package com.driot.bookplayer.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.helpers.StatsCategoryHelper;
import com.driot.bookplayer.utils.Tonio;

import java.util.ArrayList;
import java.util.List;

/** Read-only list of Folders sorted by time listened, for DetailedStatsActivity. */
public class DetailedStatsRVAdapter extends RecyclerView.Adapter<DetailedStatsRVAdapter.VH> {

    public interface OnFolderClickListener {
        void onFolderClick(Folder folder);
    }

    private final List<Folder> items = new ArrayList<>();
    private final OnFolderClickListener listener;

    public DetailedStatsRVAdapter(OnFolderClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<Folder> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.recyclerview_detailed_stats_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Folder folder = items.get(position);
        Context ctx = holder.itemView.getContext();

        holder.tvName.setText(folder.getName());
        holder.tvCategory.setText(StatsCategoryHelper.categoryLabelRes(StatsCategoryHelper.category(folder)));
        holder.tvTimeListened.setText(Tonio.formatTime(folder.timeListened * 1000));

        if (folder.image != null) {
            holder.ivCover.setVisibility(View.VISIBLE);
            Glide.with(ctx).load(folder.image).into(holder.ivCover);
        } else {
            holder.ivCover.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFolderClick(folder);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView ivCover;
        final TextView tvName, tvCategory, tvTimeListened;

        VH(@NonNull View v) {
            super(v);
            ivCover = v.findViewById(R.id.ivCover);
            tvName = v.findViewById(R.id.tvName);
            tvCategory = v.findViewById(R.id.tvCategory);
            tvTimeListened = v.findViewById(R.id.tvTimeListened);
        }
    }
}
