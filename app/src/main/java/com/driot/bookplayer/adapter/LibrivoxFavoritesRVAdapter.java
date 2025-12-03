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
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.librivox.ArchiveItem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LibrivoxFavoritesRVAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VT_HEADER = 0;
    private static final int VT_ITEM = 1;

    private List<ArchiveItem> items = new ArrayList<>();

    public interface OnItemClickListener {
        void onItemClick(ArchiveItem item);
        void onFavoriteClick(ArchiveItem item);
    }

    private final OnItemClickListener listener;

    public LibrivoxFavoritesRVAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<ArchiveItem> newItems) {
        items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
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
            View v = inf.inflate(R.layout.recyclerview_librivox_header, parent, false);
            return new HeaderVH(v);
        } else {
            View v = inf.inflate(R.layout.recyclerview_librivox_result, parent, false);
            return new ItemVH(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder vh, int position) {
        if (getItemViewType(position) == VT_HEADER) {
            HeaderVH h = (HeaderVH) vh;
            h.tvSearch.setText(h.itemView.getContext().getString(R.string.Favorites));
            h.tvLang.setText(h.itemView.getContext().getString(R.string.Librivox));
            String tvCountStr = h.itemView.getContext().getString(R.string.nb_of_audios_found) + " : " + items.size();
            h.tvCount.setText(tvCountStr);
            return;
        }

        ArchiveItem item = items.get(position - 1);
        ItemVH holder = (ItemVH) vh;
        Context context = holder.itemView.getContext();

        holder.title.setText(item.title);
        holder.info.setText("");
        holder.rating.setText("");
        holder.ratingBar.setRating(0f);
        holder.ratingBar.setVisibility(ViewGroup.GONE);
        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));

        // image
        File imageFile = ImageHelper.getLibrivoxImageFile(context, item.identifier);
        if (imageFile.exists()) {
            Glide.with(context).load(imageFile)
                    .placeholder(R.drawable.placeholder_cover)
                    .into(holder.image);
        } else {
            holder.image.setImageResource(R.drawable.placeholder_cover);
            new Thread(() -> {
                String imageUrl = "https://archive.org/services/img/" + item.identifier;
                String localPath = ImageHelper.getOrDownloadLibrivoxImage(context, item.identifier, imageUrl, false);
                if (localPath != null) {
                    ((android.app.Activity) context).runOnUiThread(() ->
                            Glide.with(holder.image)
                                    .load(new File(localPath))
                                    .placeholder(R.drawable.placeholder_cover)
                                    .into(holder.image));
                }
            }).start();
        }

        if (item.is_favorite) {
            holder.ibFavorite.setColorFilter(ContextCompat.getColor(context, R.color.red));
        } else {
            holder.ibFavorite.setColorFilter(ContextCompat.getColor(context, R.color.white));
        }
        holder.ibFavorite.setOnClickListener(v -> listener.onFavoriteClick(item));

        ImageView ivImported = holder.itemView.findViewById(R.id.ivImported);
        if (item.isImported()) {
            ivImported.setVisibility(View.VISIBLE);
        } else {
            ivImported.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size() + 1; // header + items
    }

    // --- ViewHolders ---

    static class HeaderVH extends RecyclerView.ViewHolder {
        TextView tvSearch, tvLang, tvCount;
        HeaderVH(View v) {
            super(v);
            tvSearch = v.findViewById(R.id.tvSearchTerms);
            tvLang = v.findViewById(R.id.tvLanguage);
            tvCount = v.findViewById(R.id.tvResultsCount);
        }
    }

    static class ItemVH extends RecyclerView.ViewHolder {
        TextView title, info, rating;
        RatingBar ratingBar;
        ImageView image;
        ImageButton ibFavorite;
        ItemVH(View v) {
            super(v);
            title = v.findViewById(R.id.librivox_title);
            info = v.findViewById(R.id.librivox_info);
            rating = v.findViewById(R.id.librivox_rating);
            ratingBar = v.findViewById(R.id.librivox_ratingbar);
            image = v.findViewById(R.id.librivox_image);
            ibFavorite = v.findViewById(R.id.ibFavorite);
        }
    }
}
