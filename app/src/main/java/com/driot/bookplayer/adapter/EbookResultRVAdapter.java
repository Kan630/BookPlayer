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
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingRVAdapter;

import java.util.ArrayList;
import java.util.List;

public class EbookResultRVAdapter extends LoggingRVAdapter<RecyclerView.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(EbookItem item);
    }

    private final OnItemClickListener listener;
    private final Context appContext;

    private static final int VT_HEADER = 0;
    private static final int VT_ITEM = 1;
    private static final int VT_LOADING = 2;

    private List<EbookItem> items = new ArrayList<>();
    private boolean isLoading = false;

    // Header data
    private String headerSearch = "";
    private String headerLang = "";
    private String headerCount = "";

    public EbookResultRVAdapter(Context context, OnItemClickListener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
    }

    // --- Header API ---
    public void setHeader(String search, String lang) {
        this.headerSearch = search != null ? search : "";
        this.headerLang = lang != null ? lang : "";
        notifyItemChanged(0);
    }

    public void setHeaderCount(String count) {
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
        if (isLoading == loading)
            return;

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
        final TextView tvSearch, tvLang, tvCount, tvCountryTag;

        HeaderVH(@NonNull View v) {
            super(v);
            tvSearch = v.findViewById(R.id.tvSearchTerms);
            tvLang = v.findViewById(R.id.tvLanguage);
            tvCount = v.findViewById(R.id.tvResultsCount);
            tvCountryTag = v.findViewById(R.id.tvCountryTag);
        }
    }

    static class ItemVH extends RecyclerView.ViewHolder {
        TextView title, authors, info;
        ImageView image;
        ImageView ivImported;

        ItemVH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.ebook_title);
            authors = itemView.findViewById(R.id.ebook_authors);
            info = itemView.findViewById(R.id.ebook_info);
            image = itemView.findViewById(R.id.ebook_image);
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
        if (position == 0)
            return VT_HEADER;
        if (position == getItemCount() - 1 && isLoading)
            return VT_LOADING;
        return VT_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == VT_HEADER) {
            return new HeaderVH(inf.inflate(R.layout.recyclerview_search_header, parent, false));
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
            h.tvLang.setVisibility(headerLang.isEmpty() ? View.GONE : View.VISIBLE);

            h.tvCount.setText(headerCount);
            h.tvCount.setVisibility(headerCount.isEmpty() ? View.GONE : View.VISIBLE);

            h.tvCountryTag.setVisibility(View.GONE);
        } else if (getItemViewType(position) == VT_LOADING) {
            // Loading footer - nothing to bind
        } else {
            int idx = position - 1;
            EbookItem item = items.get(idx);
            //myLog(item.toStringCrlf());
            ItemVH holder = (ItemVH) vh;

            Context viewContext = holder.image.getContext();

            holder.title.setText(item.title != null ? item.title : "");
            holder.authors.setText(item.authors != null ? item.authors : "");

            String infoText = "";
            if (item.language != null && !item.language.isEmpty()) {
                infoText = item.language;
            }
            if (item.downloadCount > 0) {
                if (!infoText.isEmpty())
                    infoText += " · ";
                infoText += Tonio.addThousandSeparator(item.downloadCount) + " " +
                        viewContext.getString(R.string.downloads); // you may need this string
            }
            holder.info.setText(infoText);

            holder.itemView.setOnClickListener(v -> {
                int p = holder.getBindingAdapterPosition();
                if (p == RecyclerView.NO_POSITION)
                    return;
                listener.onItemClick(item);
            });

            // Tag image view to avoid race conditions
            holder.image.setTag(item.gutendexId);

            if (item.coverUrl != null && !item.coverUrl.isEmpty()) {
                java.io.File imageFile = ImageHelper.getGutendexImageFile(appContext, item.gutendexId);

                if (imageFile.exists()) {
                    Glide.with(appContext)
                            .load(imageFile)
                            .placeholder(R.drawable.placeholder_cover)
                            .into(holder.image);
                } else {
                    holder.image.setImageResource(R.drawable.placeholder_cover);

                    final String coverUrl = item.coverUrl;
                    final int gutendexId = item.gutendexId;
                    new Thread(() -> {
                        String localPath = ImageHelper.getOrDownloadGutendexImage(appContext, gutendexId, coverUrl);
                        if (localPath != null) {
                            holder.image.post(() -> {
                                Object tag = holder.image.getTag();
                                if (tag instanceof Integer && (int) tag == gutendexId) {
                                    try {
                                        Glide.with(appContext)
                                                .load(new java.io.File(localPath))
                                                .placeholder(R.drawable.placeholder_cover)
                                                .error(R.drawable.placeholder_cover)
                                                .into(holder.image);
                                    } catch (Exception e) {
                                        myLogEE(e, "glide error for gutendex cover");
                                    }
                                }
                            });
                        }
                    }).start();
                }
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
