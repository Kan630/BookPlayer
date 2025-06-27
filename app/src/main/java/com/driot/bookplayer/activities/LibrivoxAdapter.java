package com.driot.bookplayer.activities;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.LibrivoxItem;

import java.util.ArrayList;
import java.util.List;

public class LibrivoxAdapter extends RecyclerView.Adapter<LibrivoxAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(LibrivoxItem item);
    }

    private final List<LibrivoxItem> items = new ArrayList<>();
    private final OnItemClickListener listener;

    public LibrivoxAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<LibrivoxItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_librivox, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LibrivoxItem item = items.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title, date, rating;

        ViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.textTitle);
            date = itemView.findViewById(R.id.textDate);
            rating = itemView.findViewById(R.id.textRating);
        }

        void bind(LibrivoxItem item, OnItemClickListener listener) {
            title.setText(item.title);
            date.setText(item.date);
            rating.setText("⭐ " + item.avg_rating + " (" + item.num_reviews + ")");
            itemView.setOnClickListener(v -> listener.onItemClick(item));
        }
    }
}

