// LibrivoxFacetCardAdapter.java
package com.driot.bookplayer.librivox;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;

import java.util.ArrayList;
import java.util.List;

public class LibrivoxFacetCardAdapter
        extends RecyclerView.Adapter<LibrivoxFacetCardAdapter.VH> {

    public interface OnClick {
        void onClick(LibrivoxFacetItem item);
    }

    private final OnClick listener;
    private final List<LibrivoxFacetItem> items = new ArrayList<>();

    public LibrivoxFacetCardAdapter(OnClick l) {
        this.listener = l;
    }

    public void setItems(List<LibrivoxFacetItem> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_card_name_count, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        LibrivoxFacetItem it = items.get(position);
        h.bind(it, listener);
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvCount;

        VH(@NonNull View itemView) {
            super(itemView);
            tvName  = itemView.findViewById(R.id.tvName);
            tvCount = itemView.findViewById(R.id.tvCount);
        }

        void bind(LibrivoxFacetItem it, OnClick listener) {
            tvName.setText(it.name);

            if (it.count != null && it.count > 0) {
                tvCount.setVisibility(View.VISIBLE);
                tvCount.setText(String.valueOf(it.count));
            } else {
                tvCount.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> listener.onClick(it));
        }
    }
}
