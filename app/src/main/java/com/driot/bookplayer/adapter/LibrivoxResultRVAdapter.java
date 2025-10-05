package com.driot.bookplayer.adapter;

import android.content.Context;
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
import com.driot.bookplayer.objects.LibrivoxItem;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.utils.log.LoggingRVAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LibrivoxResultRVAdapter extends LoggingRVAdapter<RecyclerView.ViewHolder> {
    public interface OnItemClickListener {
        void onItemClick(LibrivoxItem item);
        void onFavoriteClick(LibrivoxItem item);
    }
    private final OnItemClickListener listener;

    private static final int VT_HEADER = 0;
    private static final int VT_ITEM   = 1;

    private List<LibrivoxItem> items = new ArrayList<>();

    // Header data
    private CharSequence headerSearch = "";
    private CharSequence headerLang = "";
    private CharSequence headerCount = "";


    public LibrivoxResultRVAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    // --- Header API ---
    public void setHeader(CharSequence search, CharSequence lang) {
        this.headerSearch = search != null ? search : "";
        this.headerLang = lang != null ? lang : "";
        notifyItemChanged(0); // header
    }
    public void setHeaderCount(CharSequence count) {
        this.headerCount = count != null ? count : "";
        notifyItemChanged(0);
    }

    // --- Items API ---
    public void setItems(List<LibrivoxItem> newItems) {
        items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    // --- ViewHolders ---
    public static class HeaderVH extends RecyclerView.ViewHolder {
        final TextView tvSearch, tvLang, tvCount;
        final View topOverlayContainer;
        HeaderVH(@NonNull View v) {
            super(v);
            tvSearch = v.findViewById(R.id.tvSearchTerms);
            tvLang   = v.findViewById(R.id.tvLanguage);
            tvCount  = v.findViewById(R.id.tvResultsCount);
            topOverlayContainer = v.findViewById(R.id.topOverlayContainer);
        }
    }
    static class ItemVH extends RecyclerView.ViewHolder {
        TextView title, info, rating;
        RatingBar ratingBar;
        ImageView image;
        ImageButton ibFavorite;
        ItemVH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.librivox_title);
            info = itemView.findViewById(R.id.librivox_info);
            rating = itemView.findViewById(R.id.librivox_rating);
            ratingBar = itemView.findViewById(R.id.librivox_ratingbar);
            image = itemView.findViewById(R.id.librivox_image);
            ibFavorite = itemView.findViewById(R.id.ibFavorite);
        }
    }

    @Override public int getItemViewType(int position) {
        return position == 0 ? VT_HEADER : VT_ITEM;
    }

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == VT_HEADER) {
            return new HeaderVH(inf.inflate(R.layout.recyclerview_librivox_header, parent, false));
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
            h.tvCount.setText(headerCount);
            // If you still want the overlay badge here, attach into this container
            // from the Activity AFTER setting the adapter.
        } else {

            int idx = position - 1;
            LibrivoxItem item = items.get(idx);
            ItemVH holder = (ItemVH) vh;
            Context context = holder.image.getContext();

            holder.title.setText(item.title);
            holder.info.setText(extractYear(item.date));
            String ratingText = item.num_reviews + " " + vh.itemView.getContext().getString(R.string.reviews) + " - " + vh.itemView.getContext().getString(R.string.reviews) + " : " + item.avg_rating;
            holder.rating.setText(ratingText);
            holder.ratingBar.setRating(item.avg_rating);

            holder.itemView.setOnClickListener(v -> listener.onItemClick(item));


            // 🏷️ Tag the imageView with the identifier to prevent race conditions
            holder.image.setTag(item.identifier);

            File imageFile = ImageHelper.getLibrivoxImageFile(context, item.identifier);

            if (imageFile.exists()) {
                Glide.with(context)
                        .load(imageFile)
                        .placeholder(R.drawable.placeholder_cover)
                        .into(holder.image);
            } else {
                // Show placeholder immediately
                holder.image.setImageResource(R.drawable.placeholder_cover);

                // 🚀 Run actual download in background
                new Thread(() -> {
                    String imageUrl = "https://archive.org/services/img/" + item.identifier;
                    String localPath = ImageHelper.getOrDownloadLibrivoxImage(context, item.identifier, imageUrl, false);

                    if (localPath != null) {
                        ((android.app.Activity) context).runOnUiThread(() -> {
                            Object tag = holder.image.getTag();
                            if (tag instanceof String && tag.equals(item.identifier)) {
                                try {
                                    holder.image.post(() -> {
                                        Glide.with(holder.image)
                                                .load(new File(localPath))
                                                .placeholder(R.drawable.placeholder_cover)
                                                .error(R.drawable.placeholder_cover)
                                                .into(holder.image);
                                    });
                                } catch (Exception e) {
                                    myLogEE(e, "glide error...");
                                }
                            }
                        });
                    }
                }).start();
            }

            int tint = ContextCompat.getColor(context, item.is_favorite ? R.color.red : android.R.color.white);
            holder.ibFavorite.setColorFilter(tint);
            holder.ibFavorite.setOnClickListener(v -> {
                int p = holder.getBindingAdapterPosition();
                if (p == RecyclerView.NO_POSITION) return;
                listener.onFavoriteClick(item);  // delegate to VM
            });
            ImageView ivImported = holder.itemView.findViewById(R.id.ivImported);
            if (item.isImported()) {
                ivImported.setVisibility(View.VISIBLE);
            } else {
                ivImported.setVisibility(View.GONE);
            }
        }
    }


    @Override
    public int getItemCount() {
        return items.size() + 1;
    }

    private String extractYear(String fullDate) {
        if (fullDate == null || fullDate.length() < 4) return "";
        return fullDate.substring(0, 4);
    }

}
