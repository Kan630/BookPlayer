package com.driot.bookplayer.podcasts;

import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.content.res.ColorStateList;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.log.LoggingRVAdapter;

import com.driot.bookplayer.db.Podcast;

import java.util.ArrayList;
import java.util.List;

// Adapter for Podcast (API result)
public class PodcastSearchResultsRVAdapter extends LoggingRVAdapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_ITEM = 1;

    private List<PodcastFeed> items = new ArrayList<>();
    private List<Podcast> favorites = null;
    private final OnItemClickListener listener;

    private String headerQuery = "";
    private String headerLang = "";
    private String headerCount = "";

    public interface OnItemClickListener {
        void onItemClick(PodcastFeed item);
    }

    public PodcastSearchResultsRVAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setHeaderInfo(String query, String lang, String count) {
        this.headerQuery = query;
        this.headerLang = lang;
        this.headerCount = count;
        notifyItemChanged(0);
    }

    public void setItems(List<PodcastFeed> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    public void setFavorites(List<Podcast> favorites) {
        this.favorites = favorites;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.recyclerview_search_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.recyclerview_podcast_result, parent, false);
            return new PodcastViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            HeaderViewHolder h = (HeaderViewHolder) holder;
            h.tvSearchTerms.setText(headerQuery);
            h.tvSearchTerms.setVisibility(headerQuery.isEmpty() ? View.GONE : View.VISIBLE);

            h.tvLanguage.setText(headerLang);
            h.tvLanguage.setVisibility(headerLang.isEmpty() ? View.GONE : View.VISIBLE);

            h.tvResultsCount.setText(headerCount);
            h.tvResultsCount.setVisibility(headerCount.isEmpty() ? View.GONE : View.VISIBLE);

            h.tvCountryTag.setVisibility(View.GONE);
        } else {
            PodcastFeed item = items.get(position - 1); // subtract 1 because of header
            ((PodcastViewHolder) holder).bind(item, listener, favorites);
        }
    }

    @Override
    public int getItemCount() {
        return items.size() + 1; // +1 for header
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? VIEW_TYPE_HEADER : VIEW_TYPE_ITEM;
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        final TextView tvSearchTerms, tvLanguage, tvResultsCount, tvCountryTag;

        HeaderViewHolder(View v) {
            super(v);
            tvSearchTerms = v.findViewById(R.id.tvSearchTerms);
            tvLanguage = v.findViewById(R.id.tvLanguage);
            tvResultsCount = v.findViewById(R.id.tvResultsCount);
            tvCountryTag = v.findViewById(R.id.tvCountryTag);
        }
    }

    static class PodcastViewHolder extends RecyclerView.ViewHolder {
        TextView title, desc, folderStats;
        ImageView image;
        ImageView autoDownload;

        PodcastViewHolder(View v) {
            super(v);
            title = v.findViewById(R.id.podcast_title);
            desc = v.findViewById(R.id.podcast_desc);
            image = v.findViewById(R.id.podcast_image);
            folderStats = v.findViewById(R.id.podcast_folder_stats);
            autoDownload = v.findViewById(R.id.podcast_autodownload);
        }

        void bind(PodcastFeed item, OnItemClickListener listener, List<Podcast> favorites) {
            title.setText(item.title);
            folderStats.setVisibility(View.GONE);
            if (item.description != null) {
                desc.setText(Html.fromHtml(item.description, Html.FROM_HTML_MODE_LEGACY).toString().trim());
            }
            Glide.with(image.getContext()).load(item.image).into(image);

            boolean isFavorite = false;
            boolean isAutoDownload = false;
            if (favorites != null) {
                for (Podcast p : favorites) {
                    if (p.feedId == item.id) {
                        isFavorite = true;
                        isAutoDownload = p.autoDownload;
                        break;
                    }
                }
            }

            if (isFavorite) {
                autoDownload.setVisibility(View.VISIBLE);
                if (isAutoDownload) {
                    autoDownload.setImageResource(R.drawable.ic_download_action_24);
                    autoDownload.setImageTintList(ColorStateList.valueOf(
                            ContextCompat.getColor(autoDownload.getContext(), android.R.color.holo_green_dark)));
                } else {
                    autoDownload.setImageResource(R.drawable.ic_favorite);
                    autoDownload.setImageTintList(ColorStateList.valueOf(
                            ContextCompat.getColor(autoDownload.getContext(), android.R.color.holo_red_dark)));
                }
            } else {
                autoDownload.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> listener.onItemClick(item));
        }
    }
}
