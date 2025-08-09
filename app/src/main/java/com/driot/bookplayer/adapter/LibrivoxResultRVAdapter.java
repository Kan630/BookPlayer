package com.driot.bookplayer.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.objects.LibrivoxItem;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.utils.log.LoggingRVAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LibrivoxResultRVAdapter extends LoggingRVAdapter<LibrivoxResultRVAdapter.ViewHolder> {
    private List<LibrivoxItem> items = new ArrayList<>();

    public interface OnItemClickListener {
        void onItemClick(LibrivoxItem item);
    }

    private final OnItemClickListener listener;

    public LibrivoxResultRVAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<LibrivoxItem> newItems) {
        items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title, info, rating;
        RatingBar ratingBar;
        ImageView image;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.librivox_title);
            info = itemView.findViewById(R.id.librivox_info);
            rating = itemView.findViewById(R.id.librivox_rating);
            ratingBar = itemView.findViewById(R.id.librivox_ratingbar);
            image = itemView.findViewById(R.id.librivox_image);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.recyclerview_librivox_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LibrivoxItem item = items.get(position);
        holder.title.setText(item.title);
        holder.info.setText(extractYear(item.date));
        holder.rating.setText(item.num_reviews + " reviews - Avg rating: " + item.avg_rating);
        holder.ratingBar.setRating(item.avg_rating);

        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));

        Context context = holder.image.getContext();

        // 🏷️ Tag the imageView with the identifier to prevent race conditions
        holder.image.setTag(item.identifier);

        File imageFile = ImageHelper.getLibrivoxImageFile(context, item.identifier);

        if (imageFile.exists()) {
            Glide.with(context)
                    .load(imageFile)
                    .placeholder(R.drawable.placeholder_cover)
                    .into(holder.image);
        } else {
            // Show placeholder immediately
            holder.image.setImageResource(R.drawable.placeholder_cover);

            // 🚀 Run actual download in background
            new Thread(() -> {
                String imageUrl = "https://archive.org/services/img/" + item.identifier;
                String localPath = ImageHelper.getOrDownloadLibrivoxImage(context, item.identifier, imageUrl, false);

                if (localPath != null) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        Object tag = holder.image.getTag();
                        if (tag instanceof String && tag.equals(item.identifier)) {
                            try {
                                Glide.with(holder.image)
                                        .load(new File(localPath))
                                        .placeholder(R.drawable.placeholder_cover)
                                        .error(R.drawable.placeholder_cover)
                                        .into(holder.image);
                            } catch (Exception e) {
                                myLogEE(e, "glide error...");
                            }
                        }
                    });
                }
            }).start();  // ✅ Make sure the thread is started!
        }
    }


    @Override
    public int getItemCount() {
        return items.size();
    }

    private String extractYear(String fullDate) {
        if (fullDate == null || fullDate.length() < 4) return "";
        return fullDate.substring(0, 4);
    }

}
