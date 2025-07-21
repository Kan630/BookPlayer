package com.driot.bookplayer.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.objects.PodcastFeed;

import java.util.ArrayList;
import java.util.List;

public class PodcastResultsRVAdapter extends RecyclerView.Adapter<PodcastResultsRVAdapter.PodcastViewHolder> {
    private List<PodcastFeed> items = new ArrayList<>();
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(PodcastFeed item);
    }

    public PodcastResultsRVAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<PodcastFeed> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PodcastViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.podcast_item, parent, false);
        return new PodcastViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull PodcastViewHolder holder, int position) {
        PodcastFeed item = items.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class PodcastViewHolder extends RecyclerView.ViewHolder {
        TextView title, desc;
        ImageView image;

        PodcastViewHolder(View v) {
            super(v);
            title = v.findViewById(R.id.podcast_title);
            desc = v.findViewById(R.id.podcast_desc);
            image = v.findViewById(R.id.podcast_image);
        }

        void bind(PodcastFeed item, OnItemClickListener listener) {
            title.setText(item.title);
            desc.setText(item.description);
            Glide.with(image.getContext()).load(item.image).into(image);
            itemView.setOnClickListener(v -> listener.onItemClick(item));
        }
    }
}
