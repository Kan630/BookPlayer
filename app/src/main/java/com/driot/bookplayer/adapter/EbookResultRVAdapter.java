package com.driot.bookplayer.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.ebooks.EbookItem;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingRVAdapter;

import java.util.ArrayList;
import java.util.List;

public class EbookResultRVAdapter extends LoggingRVAdapter<RecyclerView.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(EbookItem item);
    }

    private final OnItemClickListener listener;

    private static final int VT_HEADER = 0;
    private static final int VT_ITEM   = 1;
    private static final int VT_LOADING = 2;

    private List<EbookItem> items = new ArrayList<>();
    private boolean isLoading = false;

    // Header data
    private CharSequence headerSearch = "";
    private CharSequence headerLang   = "";
    private CharSequence headerCount  = "";

    public EbookResultRVAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    // --- Header API ---
    public void setHeader(CharSequence search, CharSequence lang) {
        this.headerSearch = search != null ? search : "";
        this.headerLang   = lang != null ? lang : "";
        notifyItemChanged(0); // header
    }

    public void setHeaderCount(CharSequence count) {
        this.headerCount = count != null ? count : "";
        notifyItemChanged(0);
    }

    // --- Items API ---
    public void setItems(List<EbookItem> newItems) {
        items = newItems != null ? newItems : new ArrayList<>();
        isLoading = false;
        notifyDataSetChanged();
    }

    public void addItems(List<EbookItem> newItems) {
        if (newItems == null || newItems.isEmpty()) {
            setLoading(false);
            return;
        }
        
        boolean hadLoadingFooter = isLoading;
        int startPosition = items.size() + 1; // +1 for header
        
        if (hadLoadingFooter) {
            isLoading = false;
            notifyItemRemoved(getItemCount() - 1); // Remove loading footer
        }
        
        items.addAll(newItems);
        notifyItemRangeInserted(startPosition, newItems.size());
    }

    public void setLoading(boolean loading) {
        if (isLoading == loading) return;
        
        if (loading) {
            // Adding loading footer
            isLoading = true;
            int position = getItemCount(); // This is items.size() + 1 (header + items)
            notifyItemInserted(position);
        } else {
            // Removing loading footer
            int position = getItemCount() - 1; // This is items.size() + 1 (header + items + loading - 1)
            isLoading = false;
            notifyItemRemoved(position);
        }
    }

    public boolean isLoading() {
        return isLoading;
    }

    public int getItemCountExcludingHeader() {
        return items.size();
    }

    public List<EbookItem> getItems() {
        return new ArrayList<>(items); // Return a copy to prevent external modification
    }

    // --- ViewHolders ---
    public static class HeaderVH extends RecyclerView.ViewHolder {
        final TextView tvSearch, tvLang, tvCount;
        HeaderVH(@NonNull View v) {
            super(v);
            tvSearch = v.findViewById(R.id.tvSearchTerms);
            tvLang   = v.findViewById(R.id.tvLanguage);
            tvCount  = v.findViewById(R.id.tvResultsCount);
        }
    }

    static class ItemVH extends RecyclerView.ViewHolder {
        TextView title, authors, info;
        ImageView image;
        ImageView ivImported;

        ItemVH(@NonNull View itemView) {
            super(itemView);
            title      = itemView.findViewById(R.id.ebook_title);
            authors    = itemView.findViewById(R.id.ebook_authors);
            info       = itemView.findViewById(R.id.ebook_info);
            image      = itemView.findViewById(R.id.ebook_image);
            ivImported = itemView.findViewById(R.id.ivImported);
        }
    }

    static class LoadingVH extends RecyclerView.ViewHolder {
        ProgressBar progressBar;
        LoadingVH(@NonNull View v) {
            super(v);
            progressBar = v.findViewById(R.id.progressBar);
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) return VT_HEADER;
        if (position == getItemCount() - 1 && isLoading) return VT_LOADING;
        return VT_ITEM;
    }

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == VT_HEADER) {
            return new HeaderVH(inf.inflate(R.layout.recyclerview_ebook_header, parent, false));
        } else if (viewType == VT_LOADING) {
            return new LoadingVH(inf.inflate(R.layout.recyclerview_loading_footer, parent, false));
        } else {
            return new ItemVH(inf.inflate(R.layout.recyclerview_ebook_result, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder vh, int position) {
        if (getItemViewType(position) == VT_HEADER) {
            HeaderVH h = (HeaderVH) vh;
            h.tvSearch.setText(headerSearch);
            h.tvLang.setText(headerLang);
            h.tvCount.setText(headerCount);
        } else if (getItemViewType(position) == VT_LOADING) {
            // Loading footer - nothing to bind
        } else {
            int idx = position - 1;
            EbookItem item = items.get(idx);
            ItemVH holder = (ItemVH) vh;

            Context viewContext = holder.image.getContext();

            holder.title.setText(item.title != null ? item.title : "");
            holder.authors.setText(item.authors != null ? item.authors : "");

            String infoText = "";
            if (item.language != null && !item.language.isEmpty()) {
                infoText = item.language;
            }
            if (item.downloadCount > 0) {
                if (!infoText.isEmpty()) infoText += " · ";
                infoText += Tonio.addThousandSeparator(item.downloadCount) + " " +
                        viewContext.getString(R.string.downloads); // you may need this string
            }
            holder.info.setText(infoText);

            holder.itemView.setOnClickListener(v -> {
                int p = holder.getBindingAdapterPosition();
                if (p == RecyclerView.NO_POSITION) return;
                listener.onItemClick(item);
            });

            // Tag image view to avoid race conditions
            holder.image.setTag(item.gutendexId);

            if (item.coverUrl != null && !item.coverUrl.isEmpty()) {
                Glide.with(viewContext.getApplicationContext())
                        .load(item.coverUrl)
                        .placeholder(R.drawable.placeholder_cover)
                        .error(R.drawable.placeholder_cover)
                        .into(holder.image);
            } else {
                holder.image.setImageResource(R.drawable.placeholder_cover);
            }

            holder.ivImported.setVisibility(item.isImported() ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size() + 1 + (isLoading ? 1 : 0); // header + items + loading footer (if loading)
    }
}
