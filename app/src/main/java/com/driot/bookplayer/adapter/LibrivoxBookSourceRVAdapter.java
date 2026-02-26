package com.driot.bookplayer.adapter;

import android.app.Activity;
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
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.BookSource;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.utils.log.LoggingRVAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LibrivoxBookSourceRVAdapter extends LoggingRVAdapter<RecyclerView.ViewHolder> {

    private static final int VT_HEADER = 0;
    private static final int VT_ITEM = 1;

    private List<BookSource> items = new ArrayList<>();

    public interface OnItemClickListener {
        void onItemClick(BookSource item);

        void onFavoriteClick(BookSource item);
    }

    private final OnItemClickListener listener;

    public LibrivoxBookSourceRVAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<BookSource> newItems) {
        List<BookSource> oldItems = this.items;
        this.items = newItems != null ? new ArrayList<>(newItems) : new ArrayList<>();

        DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldItems.size() + 1; // +1 for header
            }

            @Override
            public int getNewListSize() {
                return items.size() + 1; // +1 for header
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                if (oldItemPosition == 0 || newItemPosition == 0) {
                    return oldItemPosition == newItemPosition;
                }
                BookSource oldItem = oldItems.get(oldItemPosition - 1);
                BookSource newItem = items.get(newItemPosition - 1);
                return oldItem.id == newItem.id;
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                if (oldItemPosition == 0 || newItemPosition == 0) {
                    return oldItemPosition == newItemPosition;
                }
                BookSource oldItem = oldItems.get(oldItemPosition - 1);
                BookSource newItem = items.get(newItemPosition - 1);
                // Simple check for content equality
                return oldItem.is_favorite == newItem.is_favorite &&
                        oldItem.book_title.equals(newItem.book_title) &&
                        (oldItem.idFolder == null ? newItem.idFolder == null
                                : oldItem.idFolder.equals(newItem.idFolder));
            }
        }).dispatchUpdatesTo(this);
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
            View v = inf.inflate(R.layout.recyclerview_search_header, parent, false);
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
            h.tvSearch.setVisibility(View.VISIBLE);

            h.tvLang.setText(h.itemView.getContext().getString(R.string.Librivox));
            h.tvLang.setVisibility(View.VISIBLE);

            String tvCountStr = h.itemView.getContext().getString(R.string.nb_of_audios_found) + " : " + items.size();
            h.tvCount.setText(tvCountStr);
            h.tvCount.setVisibility(View.VISIBLE);

            h.tvCountryTag.setVisibility(View.GONE);
            return;
        }

        BookSource item = items.get(position - 1);
        ItemVH holder = (ItemVH) vh;
        Context context = holder.itemView.getContext();

        holder.title.setText(item.book_title);
        holder.info.setText("");
        holder.rating.setText("");
        holder.ratingBar.setRating(0f);
        holder.ratingBar.setVisibility(ViewGroup.GONE);
        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));

        // Enhanced image loading logic
        loadImage(holder.image, item);

        if (item.is_favorite) {
            holder.ibFavorite.setColorFilter(ContextCompat.getColor(context, R.color.red));
        } else {
            holder.ibFavorite.setColorFilter(ContextCompat.getColor(context, R.color.white));
        }

        holder.ibFavorite.setOnClickListener(v -> listener.onFavoriteClick(item));
        ImageView ivImported = holder.itemView.findViewById(R.id.ivImported);
        ivImported.setVisibility(item.idFolder != null && item.idFolder > 0 ? View.VISIBLE : View.GONE);
    }

    private void loadImage(ImageView imageView, BookSource item) {
        Context context = imageView.getContext();

        // 1. Check imageLocal
        if (item.imageLocal != null && !item.imageLocal.isEmpty()) {
            File localFile = new File(item.imageLocal);
            if (localFile.exists()) {
                Glide.with(context).load(localFile).placeholder(R.drawable.placeholder_cover).into(imageView);
                return;
            }
        }

        // 2. Check identifier-based local file (legacy/fallback)
        File idBasedFile = ImageHelper.getLibrivoxImageFile(context, item.repoId);
        if (idBasedFile.exists()) {
            Glide.with(context).load(idBasedFile).placeholder(R.drawable.placeholder_cover).into(imageView);
            // Update BookSource if imageLocal or imageRemote is missing
            new Thread(() -> {
                boolean updated = false;
                if (item.imageLocal == null || item.imageLocal.isEmpty()) {
                    item.imageLocal = idBasedFile.getAbsolutePath();
                    updated = true;
                }
                if (item.imageRemote == null || item.imageRemote.isEmpty()) {
                    item.imageRemote = "https://archive.org/services/img/" + item.repoId;
                    updated = true;
                }
                if (updated) {
                    AppDatabase.getDatabase(context).bookSourceDao().update(item);
                }
            }).start();
            return;
        }

        // 3. Download if needed
        imageView.setImageResource(R.drawable.placeholder_cover);
        new Thread(() -> {
            boolean needsUpdate = false;
            String imageUrl = item.imageRemote;
            if (imageUrl == null || imageUrl.isEmpty()) {
                imageUrl = "https://archive.org/services/img/" + item.repoId;
                item.imageRemote = imageUrl;
                needsUpdate = true;
            }

            String localPath = ImageHelper.getOrDownloadLibrivoxImage(context, item.repoId, imageUrl, false);
            if (localPath != null) {
                item.imageLocal = localPath;
                needsUpdate = true;
                if (context instanceof Activity) {
                    ((Activity) context).runOnUiThread(() -> Glide.with(imageView)
                            .load(new File(localPath))
                            .placeholder(R.drawable.placeholder_cover)
                            .into(imageView));
                }
            }

            if (needsUpdate) {
                AppDatabase.getDatabase(context).bookSourceDao().update(item);
            }
        }).start();
    }

    @Override
    public int getItemCount() {
        return items.size() + 1; // header + items
    }

    static class HeaderVH extends RecyclerView.ViewHolder {
        final TextView tvSearch, tvLang, tvCount, tvCountryTag;

        HeaderVH(View v) {
            super(v);
            tvSearch = v.findViewById(R.id.tvSearchTerms);
            tvLang = v.findViewById(R.id.tvLanguage);
            tvCount = v.findViewById(R.id.tvResultsCount);
            tvCountryTag = v.findViewById(R.id.tvCountryTag);
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
