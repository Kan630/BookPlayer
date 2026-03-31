// com.driot.bookplayer.adapters/CoverResultAdapter.java
package com.driot.bookplayer.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.objects.CoverResult;
import java.util.ArrayList;
import java.util.List;

public class CoverResultAdapter extends RecyclerView.Adapter<CoverResultAdapter.VH> {
    public interface OnClick { void onClick(CoverResult r); }
    private final ArrayList<CoverResult> data = new ArrayList<>();
    private final OnClick onClick;
    public CoverResultAdapter(OnClick onClick) { this.onClick = onClick; }

    public void submit(List<CoverResult> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    public void addResults(List<CoverResult> newItems) {
        if (newItems == null || newItems.isEmpty()) return;
        int start = data.size();
        data.addAll(newItems);
        notifyItemRangeInserted(start, newItems.size());
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
        View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_cover_result, p, false);
        return new VH(v);
    }
    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        CoverResult r = data.get(pos);
        h.tvTitle.setText(r.title);
        h.tvSource.setText(r.source);
        Glide.with(h.iv).load(r.imageUrl).into(h.iv);
        h.itemView.setOnClickListener(v -> onClick.onClick(r));
    }
    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView iv; TextView tvTitle; TextView tvSource;
        VH(View v){ super(v);
            iv = v.findViewById(R.id.ivThumb);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvSource = v.findViewById(R.id.tvSource);
        }
    }
}
