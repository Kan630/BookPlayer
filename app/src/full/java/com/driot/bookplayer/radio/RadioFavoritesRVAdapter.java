package com.driot.bookplayer.radio;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.signature.ObjectKey;
import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.ItemTouchHelperAdapter;
import com.driot.bookplayer.db.RadioStation;
import com.driot.bookplayer.helpers.FlagHelper;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.utils.log.LoggingRVAdapter;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RadioFavoritesRVAdapter extends LoggingRVAdapter<RecyclerView.ViewHolder>
        implements ItemTouchHelperAdapter {

    public interface OnActionListener {
        void onPlay(RadioStation f);

        void onUnfavorite(RadioStation f);

        void onPersistOrder(List<RadioStation> newOrder);

        void onToggleFavorites();

        void onToggleHistory();
    }

    private static final int VT_HEADER = 0;
    private static final int VT_ITEM = 1;

    private final OnActionListener listener;
    private final List<RadioStation> items = new ArrayList<>();
    private final Map<String, String> faviconCache = new HashMap<>();
    private Context appContext;

    private boolean historyMode = false;

    private long trackId = -1;

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView rv) {
        super.onAttachedToRecyclerView(rv);
        this.appContext = rv.getContext().getApplicationContext();
    }

    public RadioFavoritesRVAdapter(@NonNull OnActionListener l) {
        this.listener = l;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        if (getItemViewType(position) == VT_HEADER) {
            return -1;
        }
        int idx = position - 1;
        if (idx >= 0 && idx < items.size()) {
            return items.get(idx).id;
        }
        return RecyclerView.NO_ID;
    }

    public void setItems(List<RadioStation> newItems, boolean isHistoryMode) {
        RadioStationDiffCallback diffCallback = new RadioStationDiffCallback(this.items, newItems, this.historyMode,
                isHistoryMode);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);

        this.historyMode = isHistoryMode;
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        diffResult.dispatchUpdatesTo(this);
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? VT_HEADER : VT_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == VT_HEADER) {
            return new HeaderVH(inf.inflate(R.layout.recyclerview_radio_favorites_header, parent, false));
        } else {
            return new ItemVH(inf.inflate(R.layout.recyclerview_radio_result, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder vh, int position) {
        if (getItemViewType(position) == VT_HEADER) {
            HeaderVH h = (HeaderVH) vh;
            String resultsCount = items.size() + " "
                    + (historyMode ? vh.itemView.getContext().getString(R.string.in_history)
                            : vh.itemView.getContext().getString(R.string.favorites));
            h.tvCount.setText(resultsCount);
            h.tvCount.setVisibility(View.VISIBLE);
            
            h.group.clearOnButtonCheckedListeners();
            h.group.check(historyMode ? R.id.btnRadioHistory : R.id.btnRadioFavorites);
            h.group.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) return;
                if (checkedId == R.id.btnRadioFavorites) {
                    listener.onToggleFavorites();
                } else if (checkedId == R.id.btnRadioHistory) {
                    listener.onToggleHistory();
                }
            });
        } else {
            int idx = position - 1;
            RadioStation f = items.get(idx);
            ItemVH holder = (ItemVH) vh;

            boolean activated = (trackId == f.id);
            holder.itemView.setActivated(activated); // to get bg_radio.xml in layout => activated

            holder.title.setText(nonNull(f.name));

            int flag_resource = FlagHelper.getFlagResId(this.appContext, f.country, "country");
            if (flag_resource == 0) {
                flag_resource = FlagHelper.getFlagResId(this.appContext, f.countrycode, "country");
            }
            if (flag_resource == 0) {
                flag_resource = FlagHelper.getFlagResId(this.appContext, f.language, "language");
            }
            if (flag_resource == 0) {
                holder.ivFlag.setVisibility(View.GONE);
                Glide.with(holder.ivFlag.getContext()).clear(holder.ivFlag);
            } else {
                holder.ivFlag.setVisibility(View.VISIBLE);
                Glide.with(holder.ivFlag)
                        .load(flag_resource)
                        .placeholder(R.drawable.no_flag) // Use a subtle placeholder
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .signature(new ObjectKey(f.stationuuid + "_country_flag"))
                        .into(holder.ivFlag);
            }

            holder.info.setText(RadioHelper.buildShortInfoString(f));

            holder.ibFavorite.setVisibility(View.GONE);

            RadioFaviconHelper.loadRadioFavicon(f, holder.favicon, 0, faviconCache);

            holder.itemView.setOnClickListener(v -> {
                listener.onPlay(f);
            });
        }
    }

    @Override
    public int getItemCount() {
        return items.size() + 1;
    }

    // ---- VHs ----
    static class HeaderVH extends RecyclerView.ViewHolder {
        final TextView tvCount;
        final MaterialButtonToggleGroup group;

        HeaderVH(@NonNull View v) {
            super(v);
            tvCount = v.findViewById(R.id.tvResultsCount);
            group = v.findViewById(R.id.groupFavoriteVsHistory);
        }
    }

    // ---- helpers ----
    private static String nonNull(String s) {
        return s == null ? "" : s;
    }

    private static String normalizeTags(String csv) {
        if (csv == null || csv.trim().isEmpty())
            return "";
        String[] parts = csv.split(",");
        StringBuilder sb = new StringBuilder();
        int added = 0;
        for (String p : parts) {
            String t = p.trim();
            if (t.isEmpty())
                continue;
            if (added > 0)
                sb.append(", ");
            sb.append(t);
            added++;
            if (added >= 3)
                break;
        }
        return sb.toString();
    }

    // --- ItemTouchHelperAdapter (custom) ---
    @Override
    public boolean onItemMove(int fromPos, int toPos) {
        // account for header at pos 0
        if (fromPos == 0 || toPos == 0)
            return false;
        int a = fromPos - 1;
        int b = toPos - 1;
        if (a < 0 || b < 0 || a >= items.size() || b >= items.size())
            return false;
        RadioStation moved = items.remove(a);
        items.add(b, moved);
        notifyItemMoved(fromPos, toPos);
        return true;
    }

    @Override
    public void onItemDropped() {
        // persist after a drag is finished
        if (listener != null)
            listener.onPersistOrder(new ArrayList<>(items));
    }

    @Override
    public void onDroppedInTrash(int adapterPosition) {
        myLogI("onDroppedInTrash (pos) : " + adapterPosition);
        if (adapterPosition < 0)
            myToastEE(null, appContext.getString(R.string.an_error_occurred)); // header
        if (adapterPosition <= 0)
            return; // header
        int idx = adapterPosition - 1;
        if (idx < 0 || idx >= items.size())
            return;
        RadioStation victim = items.get(idx);
        if (listener != null)
            listener.onUnfavorite(victim);
    }

    @Override
    public void onDroppedInTrashUuid(@NonNull String uuid) {
        myLogI("onDroppedInTrash (uuid) : " + uuid);
        RadioStation victim = null;
        for (RadioStation it : items) {
            if (uuid.equals(it.stationuuid)) {
                victim = it;
                break;
            }
        }
        if (victim != null && listener != null) {
            listener.onUnfavorite(victim);
        }
    }

    @Override
    @Nullable
    public String getUuidForAdapterPosition(int adapterPosition) {
        if (adapterPosition <= 0)
            return null; // header
        int idx = adapterPosition - 1;
        if (idx < 0 || idx >= items.size())
            return null;
        return items.get(idx).stationuuid;
    }

    // ---- Drag starter wiring ----
    public interface OnStartDragListener {
        void onStartDrag(RecyclerView.ViewHolder vh);
    }

    private OnStartDragListener startDragListener;

    public void setOnStartDragListener(OnStartDragListener l) {
        this.startDragListener = l;
    }

    static class ItemVH extends RecyclerView.ViewHolder {
        ImageView favicon, ivDefaultIcon, ivFlag;
        TextView title, info;
        ImageButton ibFavorite;

        ItemVH(@NonNull View v) {
            super(v);
            favicon = v.findViewById(R.id.radio_favicon);
            title = v.findViewById(R.id.radio_title);
            info = v.findViewById(R.id.radio_info_txt);
            ibFavorite = v.findViewById(R.id.ibFavorite);
            ivFlag = v.findViewById(R.id.iv_radio_flag);
        }
    }

    public void setHistoryMode(boolean history) {
        this.historyMode = history;
    }

    public void setPlayingRadioStation(long trackId) {
        if (trackId == this.trackId)
            return;

        long oldTrackId = this.trackId;
        this.trackId = trackId;

        // Notify old playing item
        int oldPos = getPositionForTrackId(oldTrackId);
        if (oldPos != RecyclerView.NO_POSITION) {
            notifyItemChanged(oldPos);
        }

        // Notify new playing item
        int newPos = getPositionForTrackId(trackId);
        if (newPos != RecyclerView.NO_POSITION) {
            notifyItemChanged(newPos);
        }
    }

    public int getPositionForTrackId(long trackId) {
        if (trackId <= 0)
            return RecyclerView.NO_POSITION;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id == trackId) {
                return i + 1; // +1 for header
            }
        }
        return RecyclerView.NO_POSITION;
    }

    private static class RadioStationDiffCallback extends DiffUtil.Callback {
        private final List<RadioStation> oldList;
        private final List<RadioStation> newList;
        private final boolean oldHistoryMode;
        private final boolean newHistoryMode;

        RadioStationDiffCallback(List<RadioStation> oldList, List<RadioStation> newList, boolean oldHistoryMode,
                boolean newHistoryMode) {
            this.oldList = oldList != null ? oldList : new ArrayList<>();
            this.newList = newList != null ? newList : new ArrayList<>();
            this.oldHistoryMode = oldHistoryMode;
            this.newHistoryMode = newHistoryMode;
        }

        @Override
        public int getOldListSize() {
            return oldList.size() + 1; // +1 for header
        }

        @Override
        public int getNewListSize() {
            return newList.size() + 1; // +1 for header
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            if (oldItemPosition == 0 && newItemPosition == 0)
                return true;
            if (oldItemPosition == 0 || newItemPosition == 0)
                return false;

            return oldList.get(oldItemPosition - 1).id == newList.get(newItemPosition - 1).id;
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            if (oldItemPosition == 0 && newItemPosition == 0) {
                // Return false if history mode changed OR if the list size changed (so count in
                // header updates)
                return oldHistoryMode == newHistoryMode && oldList.size() == newList.size();
            }
            if (oldItemPosition == 0 || newItemPosition == 0)
                return false;

            RadioStation oldItem = oldList.get(oldItemPosition - 1);
            RadioStation newItem = newList.get(newItemPosition - 1);

            return Objects.equals(oldItem.name, newItem.name) &&
                    Objects.equals(oldItem.favicon, newItem.favicon) &&
                    Objects.equals(oldItem.url, newItem.url) &&
                    Objects.equals(oldItem.isFavorite, newItem.isFavorite) &&
                    Objects.equals(oldItem.display_order, newItem.display_order);
        }
    }
}
