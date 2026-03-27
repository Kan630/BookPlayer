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

    public interface OnLangCardClick {
        void onLangCardClick(LanguageMapper.RadioLanguageCardItem card);
    }

    private final List<TagItem> items = new ArrayList<>();
    private final List<LanguageMapper.RadioLanguageCardItem> langItems = new ArrayList<>();
    private boolean isLanguageMode = false;

    private final OnClick cb;
    private final OnLangCardClick langCb;
    private final boolean showFlags;

    public TagCardAdapter(OnClick cb, OnLangCardClick langCb, boolean showFlags) {
        this.cb       = cb;
        this.langCb   = langCb;
        this.showFlags = showFlags;
    }

    public void setItems(List<TagItem> newData) {
        isLanguageMode = false;
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffCallback(this.items, newData));
        this.items.clear();
        if (newData != null) this.items.addAll(newData);
        diffResult.dispatchUpdatesTo(this);
    }

    public void setLanguageItems(List<LanguageMapper.RadioLanguageCardItem> newData) {
        isLanguageMode = true;
        this.langItems.clear();
        if (newData != null) this.langItems.addAll(newData);
        notifyDataSetChanged();
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
        if (isLanguageMode) bindLanguageCard(h, langItems.get(pos));
        else                bindTagItem(h, items.get(pos));
    }

    private void bindTagItem(@NonNull VH h, TagItem t) {
        String name = (t.name != null && !t.name.isEmpty())
                ? t.name.substring(0, 1).toUpperCase() + t.name.substring(1) : "---";
        h.tvName.setText(name);
        h.tvCount.setText(String.valueOf(t.stationcount));

        int flagResId = 0;
        if (showFlags && t.iso_3166_1 != null)
            flagResId = getFlagResId(h.ivFlag.getContext(), t.iso_3166_1, "country");
        else if (showFlags && t.iso_639 != null)
            flagResId = getFlagResId(h.ivFlag.getContext(), t.iso_639, "language");
        if (showFlags && flagResId == 0)
            flagResId = LanguageMapper.getFlagFromName(t.name);

        applyFlag(h, flagResId, t.name + "_" + flagResId);
        if (showFlags && flagResId == 0)
            myLogW("no flag — name=[" + t.name + "] iso_639=[" + t.iso_639
                    + "] iso_3166_1=[" + t.iso_3166_1 + "] count=[" + t.stationcount + "]");

        h.card.setOnClickListener(v -> { if (cb != null) cb.onTagClick(t); });
    }

    private void bindLanguageCard(@NonNull VH h, LanguageMapper.RadioLanguageCardItem card) {
        String name = (card.label != null && !card.label.isEmpty())
                ? card.label.substring(0, 1).toUpperCase() + card.label.substring(1) : "---";
        h.tvName.setText(name);
        h.tvCount.setText(String.valueOf(card.stationcount));

        int flagResId = showFlags ? card.flagRes() : 0;
        applyFlag(h, flagResId, card.label + "_" + flagResId);
        if (showFlags && flagResId == 0)
            myLogW("no flag — label=[" + card.label + "]");

        h.card.setOnClickListener(v -> { if (langCb != null) langCb.onLangCardClick(card); });
    }

    private void applyFlag(@NonNull VH h, int flagResId, String sigKey) {
        if (flagResId != 0) {
            Glide.with(h.ivFlag.getContext()).load(flagResId)
                    .signature(new ObjectKey(sigKey)).into(h.ivFlag);
            h.ivFlag.setVisibility(View.VISIBLE);
        } else {
            Glide.with(h.ivFlag.getContext()).clear(h.ivFlag);
            h.ivFlag.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return isLanguageMode ? langItems.size() : items.size();
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
