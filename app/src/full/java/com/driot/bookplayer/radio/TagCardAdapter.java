package com.driot.bookplayer.radio;

import static com.driot.bookplayer.helpers.FlagHelper.getFlagResId;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.signature.ObjectKey;
import com.driot.bookplayer.R;
import com.driot.bookplayer.librivox.LanguageMapper;
import com.driot.bookplayer.utils.log.LoggingRVAdapter;

import java.util.ArrayList;
import java.util.List;

public class TagCardAdapter extends LoggingRVAdapter<TagCardAdapter.VH> {

    public interface OnClick {
        void onTagClick(TagItem t);
    }

    private final List<TagItem> items = new ArrayList<>();
    private final OnClick cb;
    private final boolean showFlags;

    public TagCardAdapter(OnClick cb, boolean showFlags) {
        this.cb = cb;
        this.showFlags = showFlags;
    }

    public void setItems(List<TagItem> newData) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffCallback(this.items, newData));
        this.items.clear();
        if (newData != null) {
            this.items.addAll(newData);
        }
        diffResult.dispatchUpdatesTo(this);
    }

    private static class DiffCallback extends DiffUtil.Callback {
        private final List<TagItem> oldList;
        private final List<TagItem> newList;

        public DiffCallback(List<TagItem> oldList, List<TagItem> newList) {
            this.oldList = oldList;
            this.newList = newList != null ? newList : new ArrayList<>();
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            // Using name as unique identifier for facets
            TagItem oldItem = oldList.get(oldItemPosition);
            TagItem newItem = newList.get(newItemPosition);
            if (oldItem.name == null || newItem.name == null)
                return false;
            return oldItem.name.equals(newItem.name);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            TagItem oldItem = oldList.get(oldItemPosition);
            TagItem newItem = newList.get(newItemPosition);
            return oldItem.stationcount == newItem.stationcount
                    && equals(oldItem.iso_3166_1, newItem.iso_3166_1)
                    && equals(oldItem.iso_639, newItem.iso_639);
        }

        private boolean equals(String s1, String s2) {
            if (s1 == null && s2 == null)
                return true;
            if (s1 == null || s2 == null)
                return false;
            return s1.equals(s2);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int vtype) {
        View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_tag_card, p, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        TagItem t = items.get(pos);
        // NAME
        String name = "---";
        if (t.name != null && !t.name.isEmpty()) {
            name = t.name.substring(0, 1).toUpperCase() + t.name.substring(1);
        }
        h.tvName.setText(name);
        // COUNT
        h.tvCount.setText(String.valueOf(t.stationcount));
        // FLAG
        if (!showFlags) {
            Glide.with(h.ivFlag.getContext()).clear(h.ivFlag);
            h.ivFlag.setVisibility(View.GONE);
        }
        int flagResId = 0;
        if (showFlags && t.iso_3166_1 != null) {
            flagResId = getFlagResId(h.ivFlag.getContext(), t.iso_3166_1, "country");
        } else if (showFlags && t.iso_639 != null) {
            flagResId = getFlagResId(h.ivFlag.getContext(), t.iso_639, "language");
        }
        if (showFlags && flagResId == 0) {
            flagResId = LanguageMapper.getFlagFromName(t.name);
        }
        if (showFlags && flagResId == 0) {
            if ("american english".equals(t.name)) {
                flagResId = getFlagResId(h.ivFlag.getContext(), "us", "country");
            } else if ("brazilian portuguese".equals(t.name)) {
                flagResId = getFlagResId(h.ivFlag.getContext(), "br", "country");
            } else if ("português  brasil".equals(t.name)) {
                flagResId = getFlagResId(h.ivFlag.getContext(), "br", "country");
            } else if ("português (brasil)".equals(t.name)) {
                flagResId = getFlagResId(h.ivFlag.getContext(), "br", "country");
            } else if ("portugues do brasil".equals(t.name)) {
                flagResId = getFlagResId(h.ivFlag.getContext(), "br", "country");
            } else if ("español internacional".equals(t.name)) {
                flagResId = getFlagResId(h.ivFlag.getContext(), "es", "country");
            } else if ("español chile".equals(t.name)) {
                flagResId = getFlagResId(h.ivFlag.getContext(), "cl", "country");
            } else if ("castellano. español".equals(t.name)) {
                flagResId = getFlagResId(h.ivFlag.getContext(), "es", "country");
            } else if ("español - latinoamerica".equals(t.name)) {
                flagResId = getFlagResId(h.ivFlag.getContext(), "es", "country");
            } else if ("español argentina".equals(t.name)) {
                flagResId = getFlagResId(h.ivFlag.getContext(), "ar", "country");
            } else if ("español mexico".equals(t.name)) {
                flagResId = getFlagResId(h.ivFlag.getContext(), "mx", "country");
            } else if ("english uk".equals(t.name)) {
                flagResId = getFlagResId(h.ivFlag.getContext(), "uk", "country");
            } else if ("british english".equals(t.name)) {
                flagResId = getFlagResId(h.ivFlag.getContext(), "uk", "country");
            } else if ("swiss german".equals(t.name)) {
                flagResId = R.drawable.flag_ch;
            } else if ("romania".equals(t.name)) {
                flagResId = R.drawable.flag_ro;
            }
        }

        if (flagResId != 0) {
            // KanLogger.myLog("tagitem - name=[" + t.name + "] - iso_3166_1=[" +
            // t.iso_3166_1 + "] - iso_639=[" + t.iso_639 + "] - flag=[" +
            // h.ivFlag.getContext().getResources().getResourceEntryName(flagResId) + "]");
            Glide.with(h.ivFlag.getContext()).load(flagResId).signature(new ObjectKey(t.name + "_" + flagResId))
                    .into(h.ivFlag);
            h.ivFlag.setVisibility(View.VISIBLE);
        } else {
            // KanLogger.myLog("tagitem - name=[" + t.name + "] - iso_3166_1=[" +
            // t.iso_3166_1 + "] - iso_639=[" + t.iso_639 + "] - flag=[null/0]");
            Glide.with(h.ivFlag.getContext()).clear(h.ivFlag);
            h.ivFlag.setVisibility(View.GONE);
        }
        // CLICK
        h.card.setOnClickListener(v -> {
            if (cb != null)
                cb.onTagClick(t);
        });

    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final View card;
        final TextView tvName;
        final TextView tvCount;
        final ImageView ivFlag;

        VH(@NonNull View v) {
            super(v);
            card = v.findViewById(R.id.card);
            tvName = v.findViewById(R.id.tvTagName);
            tvCount = v.findViewById(R.id.tvTagCount);
            ivFlag = v.findViewById(R.id.ivFlag);
        }
    }

}
