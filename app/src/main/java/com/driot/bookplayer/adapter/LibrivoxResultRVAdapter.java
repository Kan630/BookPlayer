package com.driot.bookplayer.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.objects.LibrivoxItem;
import com.driot.bookplayer.utils.log.LoggingRVAdapter;

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
        TextView title, date, rating;
        RatingBar ratingBar;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tvTitle);
            date = itemView.findViewById(R.id.tvDate);
            rating = itemView.findViewById(R.id.tvRating);
            ratingBar = itemView.findViewById(R.id.ratingBar);
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
        holder.date.setText("audio available since " + extractYear(item.date));
        holder.rating.setText("Nb of reviews: " + item.num_reviews + "  -  Avg rating: " + item.avg_rating);

        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));

        float ratingValue = item.avg_rating;
        holder.ratingBar.setRating(ratingValue);
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
