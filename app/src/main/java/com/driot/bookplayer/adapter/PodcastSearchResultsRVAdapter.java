package com.driot.bookplayer.adapter;

import android.content.Context;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.objects.LanguageItem;
import com.driot.bookplayer.objects.PodcastFeed;
import com.driot.bookplayer.helpers.LanguageHelper;
import com.driot.bookplayer.utils.log.LoggingRVAdapter;

import java.util.ArrayList;
import java.util.List;

// Adapter for Podcast (API result)
public class PodcastSearchResultsRVAdapter extends LoggingRVAdapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_ITEM = 1;

    private List<PodcastFeed> items = new ArrayList<>();
    private final OnItemClickListener listener;


    private String headerQuery = "";
    private String headerLang = "";
    private int headerCount = 0;

    public interface OnItemClickListener {
        void onItemClick(PodcastFeed item);
    }


    public PodcastSearchResultsRVAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }


    public void setHeaderInfo(String query, String lang, int count) {
        this.headerQuery = query;
        this.headerLang = lang;
        this.headerCount = count;
        notifyItemChanged(0);
    }

    public void setItems(List<PodcastFeed> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.recyclerview_podcast_header, parent, false);
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
            ((HeaderViewHolder) holder).bind(headerQuery, headerLang, headerCount);
        } else {
            PodcastFeed item = items.get(position - 1); // subtract 1 because of header
            ((PodcastViewHolder) holder).bind(item, listener);
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
        private final TextView tvSearchTerms;
        private final TextView tvLanguage;
        private final TextView tvResultsCount;

        HeaderViewHolder(View v) {
            super(v);
            tvSearchTerms = v.findViewById(R.id.tvSearchTermsPodcast);
            tvLanguage = v.findViewById(R.id.tvLanguagePodcast);
            tvResultsCount = v.findViewById(R.id.tvResultsCountPodcast);
        }

        void bind(String query, String lang, int count) {
            Context context = itemView.getContext();
            String searchTerms = "Search: " + (query.isEmpty() ? context.getString(R.string.Trending) : query);
            tvSearchTerms.setText(searchTerms);
            LanguageItem langItem = LanguageHelper.getLanguageForPodcastsByCode(lang);
            String language = "Language: " + (langItem != null ? langItem.displayName : "");
            tvLanguage.setText(language);
            String resultsCount = "Results: " + count + (count == Var.PODCASTINDEXORG_API_MAX_RESULTS_FOR_PODCASTS ? " (" + context.getString(R.string.max_number_of_results_reached) + ")" : "");
            tvResultsCount.setText(resultsCount);
        }
    }

    static class PodcastViewHolder extends RecyclerView.ViewHolder {
        TextView title, desc, folderStats;
        ImageView image;

        PodcastViewHolder(View v) {
            super(v);
            title = v.findViewById(R.id.podcast_title);
            desc = v.findViewById(R.id.podcast_desc);
            image = v.findViewById(R.id.podcast_image);
            folderStats = v.findViewById(R.id.podcast_folder_stats);
        }

        void bind(PodcastFeed item, OnItemClickListener listener) {
            title.setText(item.title);
            folderStats.setVisibility(View.GONE);
            if (item.description != null) {
                desc.setText(Html.fromHtml(item.description, Html.FROM_HTML_MODE_LEGACY));
            }
            Glide.with(image.getContext()).load(item.image).into(image);
            itemView.setOnClickListener(v -> listener.onItemClick(item));
        }
    }
}
