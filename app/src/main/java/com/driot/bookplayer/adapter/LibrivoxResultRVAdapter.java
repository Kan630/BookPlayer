package com.driot.bookplayer.adapter;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.librivox.ArchiveItem;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.utils.log.LoggingRVAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LibrivoxResultRVAdapter extends LoggingRVAdapter<RecyclerView.ViewHolder> {
    public interface OnItemClickListener {
        void onItemClick(ArchiveItem item);

        void onFavoriteClick(ArchiveItem item);
    }

    private final OnItemClickListener listener;

    private static final int VT_HEADER = 0;
    private static final int VT_ITEM = 1;

    private List<ArchiveItem> items = new ArrayList<>();

    // Header data
    private String headerSearch = "";
    private String headerLang = "";
    private String headerCount = "";

    public LibrivoxResultRVAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    // --- Header API ---
    public void setHeader(String search, String lang) {
        this.headerSearch = search != null ? search : "";
        this.headerLang = lang != null ? lang : "";
        notifyItemChanged(0);
    }

    public void setHeaderSearch(String search) {
        this.headerSearch = search != null ? search : "";
        notifyItemChanged(0);
    }

    public void setHeaderLang(String lang) {
        this.headerLang = lang != null ? lang : "";
        notifyItemChanged(0);
    }

    public void setHeaderCount(String count) {
        this.headerCount = count != null ? count : "";
        notifyItemChanged(0);
    }

    // --- Items API ---
    public void setItems(List<ArchiveItem> newItems) {
        items = newItems != null ? new ArrayList<>(newItems) : new ArrayList<>();
        notifyDataSetChanged();
    }

    /** Append new items (e.g. next page). Header is at position 0. */
    public void appendItems(List<ArchiveItem> newItems) {
        if (newItems != null && !newItems.isEmpty()) {
            int startPosition = items.size() + 1; // +1 for header
            items.addAll(newItems);
            notifyItemRangeInserted(startPosition, newItems.size());
        }
    }

    // --- ViewHolders ---
    public static class HeaderVH extends RecyclerView.ViewHolder {
        final TextView tvSearch, tvLang, tvCount, tvCountryTag;
        final View topOverlayContainer;

        HeaderVH(@NonNull View v) {
            super(v);
            tvSearch = v.findViewById(R.id.tvSearchTerms);
            tvLang = v.findViewById(R.id.tvLanguage);
            tvCount = v.findViewById(R.id.tvResultsCount);
            tvCountryTag = v.findViewById(R.id.tvCountryTag);
            topOverlayContainer = v.findViewById(R.id.topOverlayContainer);
        }
    }

    static class ItemVH extends RecyclerView.ViewHolder {
        TextView title, info, rating, creator;
        RatingBar ratingBar;
        ImageView image;
        ImageButton ibFavorite;

        ItemVH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.librivox_title);
            creator = itemView.findViewById(R.id.creator);
            info = itemView.findViewById(R.id.librivox_info);
            rating = itemView.findViewById(R.id.librivox_rating);
            ratingBar = itemView.findViewById(R.id.librivox_ratingbar);
            image = itemView.findViewById(R.id.librivox_image);
            ibFavorite = itemView.findViewById(R.id.ibFavorite);
        }
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
            return new HeaderVH(inf.inflate(R.layout.recyclerview_search_header, parent, false));
        } else {
            return new ItemVH(inf.inflate(R.layout.recyclerview_librivox_result, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder vh, int position) {
        if (getItemViewType(position) == VT_HEADER) {
            HeaderVH h = (HeaderVH) vh;
            h.tvSearch.setText(headerSearch);

            h.tvLang.setText(headerLang);
            h.tvLang.setVisibility(headerLang.isEmpty() ? View.GONE : View.VISIBLE);

            h.tvCount.setText(headerCount);
            h.tvCount.setVisibility(headerCount.isEmpty() ? View.GONE : View.VISIBLE);

            h.tvCountryTag.setVisibility(View.GONE);
        } else {

            int idx = position - 1;
            ArchiveItem item = items.get(idx);
            ItemVH holder = (ItemVH) vh;

            // View context (for resources, colors, etc.)
            Context viewContext = holder.image.getContext();
            // Application context (for Glide / background work, not tied to Activity
            // lifecycle)
            Context appContext = viewContext.getApplicationContext();

            holder.title.setText(item.title);

            if (item.author != null && !item.author.isEmpty()) {
                holder.creator.setText(item.author);
            } else {
                holder.creator.setText(item.creator);
            }

            holder.info.setVisibility(View.GONE);

            if (item.num_reviews > 0) {
                String year = "xxxx";
                if (item.date != null && item.date.contains("copyright")) { // Hack for pure Librivox Result
                    year = item.date;
                } else {
                    year = extractYear(item.date);
                }

                String ratingText = item.num_reviews + " " +
                        vh.itemView.getContext().getString(R.string.reviews) +
                        " - " +
                        vh.itemView.getContext().getString(R.string.average_rating) +
                        " : " + item.avg_rating +
                        " - added: " + year;
                holder.rating.setText(ratingText);
                holder.ratingBar.setRating(item.avg_rating);
                holder.rating.setVisibility(View.VISIBLE);
                holder.ratingBar.setVisibility(View.VISIBLE);
            } else {
                holder.rating.setVisibility(View.GONE);
                holder.ratingBar.setVisibility(View.GONE);
            }

            holder.itemView.setOnClickListener(v -> listener.onItemClick(item));

            // 🏷️ Tag the imageView with the identifier to prevent race conditions
            holder.image.setTag(item.identifier);

            // Use appContext so we are not tied to a destroyed Activity
            File imageFile = ImageHelper.getLibrivoxImageFile(appContext, item.identifier);

            if (imageFile.exists()) {
                // ✅ No Activity lifecycle issue here
                Glide.with(appContext)
                        .load(imageFile)
                        .placeholder(R.drawable.placeholder_cover)
                        .into(holder.image);
            } else {
                // Show placeholder immediately
                holder.image.setImageResource(R.drawable.placeholder_cover);

                // 🚀 Run actual download in background
                new Thread(() -> {
                    String imageUrl = item.imageRemote;
                    if (imageUrl == null || imageUrl.isEmpty()) {
                        imageUrl = "https://archive.org/services/img/" + item.identifier;
                    }

                    String localPath = ImageHelper.getOrDownloadLibrivoxImage(
                            appContext,
                            item.identifier,
                            imageUrl,
                            false);

                    if (localPath != null) {
                        // Use the View to go back to the UI thread, not Activity.runOnUiThread
                        holder.image.post(() -> {
                            Object tag = holder.image.getTag();
                            if (tag instanceof String && tag.equals(item.identifier)) {
                                try {
                                    Glide.with(appContext)
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
                }).start();
            }

            int tint = ContextCompat.getColor(
                    viewContext,
                    item.is_favorite ? R.color.red : android.R.color.white);
            holder.ibFavorite.setColorFilter(tint);
            holder.ibFavorite.setOnClickListener(v -> {
                int p = holder.getBindingAdapterPosition();
                if (p == RecyclerView.NO_POSITION)
                    return;
                listener.onFavoriteClick(item);
            });

            ImageView ivImported = holder.itemView.findViewById(R.id.ivImported);
            ivImported.setVisibility(item.isImported() ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size() + 1;
    }

    private String extractYear(String fullDate) {
        if (fullDate == null || fullDate.length() < 4)
            return "";
        return fullDate.substring(0, 4);
    }

}
