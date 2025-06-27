package com.driot.bookplayer.utils;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.db.LibrivoxItem;

import java.util.ArrayList;
import java.util.List;

public class LibrivoxAdapter extends RecyclerView.Adapter<LibrivoxAdapter.ViewHolder> {
    List<LibrivoxItem> items = new ArrayList<>();

    public interface OnItemClickListener {
        void onItemClick(LibrivoxItem item);
    }

    private final OnItemClickListener listener;

    public LibrivoxAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<LibrivoxItem> newItems) {
        items = newItems;
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title, date, rating;

        public ViewHolder(LinearLayout layout) {
            super(layout);
            this.title = (TextView) layout.getChildAt(0);
            this.date = (TextView) layout.getChildAt(1);
            this.rating = (TextView) layout.getChildAt(2);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Programmatically build layout
        Context context = parent.getContext();
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 24);

        TextView title = new TextView(context);
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);

        TextView date = new TextView(context);
        TextView rating = new TextView(context);

        layout.addView(title);
        layout.addView(date);
        layout.addView(rating);

        return new ViewHolder(layout);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LibrivoxItem item = items.get(position);
        holder.title.setText(item.title);
        holder.date.setText("Date: " + item.date);
        holder.rating.setText("Rating: " + item.avg_rating + " (" + item.num_reviews + ")");

        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogI(String str) { KanLogger.myLogI(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
    private void myToast(String str) { KanLogger.myToast(this.getClass().getName(), str); }
    private void myToastE(String str) { KanLogger.myToastE(this.getClass().getName(), str); }
}
