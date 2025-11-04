package com.driot.bookplayer.radio;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;

import java.util.ArrayList;
import java.util.List;

public class TagCardAdapter extends RecyclerView.Adapter<TagCardAdapter.VH> {

    public interface OnClick { void onTagClick(TagItem t); }

    private final List<TagItem> items = new ArrayList<>();
    private final OnClick cb;

    public TagCardAdapter(OnClick cb) { this.cb = cb; }

    public void setItems(List<TagItem> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int vtype) {
        View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_tag_card, p, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        TagItem t = items.get(pos);
        h.tvName.setText(t.name);
        h.tvCount.setText(String.valueOf(t.stationcount));
        h.card.setOnClickListener(v -> { if (cb != null) cb.onTagClick(t); });
    }

    @Override public int getItemCount() { return items.size(); }

    static final class VH extends RecyclerView.ViewHolder {
        final View card; final TextView tvName; final TextView tvCount;
        VH(@NonNull View v) {
            super(v);
            card = v.findViewById(R.id.card);
            tvName  = v.findViewById(R.id.tvTagName);
            tvCount = v.findViewById(R.id.tvTagCount);
        }
    }
}

