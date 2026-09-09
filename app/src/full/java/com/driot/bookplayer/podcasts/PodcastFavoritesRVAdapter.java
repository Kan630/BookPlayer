package com.driot.bookplayer.podcasts;

import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.KanLogger;
import com.driot.bookplayer.utils.log.LoggingRVAdapter;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Adapter for Podcast (Room entity)
public class PodcastFavoritesRVAdapter extends LoggingRVAdapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_ITEM = 1;

    private List<Podcast> items = new ArrayList<>();
    private final OnActionListener listener;
    private boolean historyMode;

    public interface OnActionListener {
        void onItemClick(Podcast podcast);

        void onToggleFavorites();

        void onToggleHistory();
    }

    public PodcastFavoritesRVAdapter(OnActionListener listener) {
        this(listener, false);
    }

    public PodcastFavoritesRVAdapter(OnActionListener listener, boolean initialHistoryMode) {
        this.listener = listener;
        this.historyMode = initialHistoryMode;
    }

    /** Items and mode always arrive together, so the header (toggle + count) and the list below
     * it never show a mismatched pairing while a mode switch is loading. */
    public void setItems(List<Podcast> newItems, boolean isHistoryMode) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        this.historyMode = isHistoryMode;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.recyclerview_podcast_favorites_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.recyclerview_podcast_result, parent, false);
            return new PodcastViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            HeaderViewHolder h = (HeaderViewHolder) holder;
            String resultsCount = items.size() + " "
                    + (historyMode ? h.itemView.getContext().getString(R.string.in_history)
                            : h.itemView.getContext().getString(R.string.favorites));
            h.tvResultsCount.setText(resultsCount);

            h.group.clearOnButtonCheckedListeners();
            h.group.check(historyMode ? R.id.btnPodcastHistory : R.id.btnPodcastFavorites);
            h.group.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) return;
                if (checkedId == R.id.btnPodcastFavorites) {
                    listener.onToggleFavorites();
                } else if (checkedId == R.id.btnPodcastHistory) {
                    listener.onToggleHistory();
                }
            });
        } else {
            Podcast podcast = items.get(position - 1);
            ((PodcastViewHolder) holder).bind(podcast, listener);
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
        final TextView tvResultsCount;
        final MaterialButtonToggleGroup group;

        HeaderViewHolder(View v) {
            super(v);
            tvResultsCount = v.findViewById(R.id.tvResultsCount);
            group = v.findViewById(R.id.groupPodcastFavoriteVsHistory);
        }
    }

    static class PodcastViewHolder extends RecyclerView.ViewHolder {
        TextView title, desc, folderStats;
        ImageView image;
        View autoDownloadContainer;

        PodcastViewHolder(View v) {
            super(v);
            title = v.findViewById(R.id.podcast_title);
            desc = v.findViewById(R.id.podcast_desc);
            image = v.findViewById(R.id.podcast_image);
            autoDownloadContainer = v.findViewById(R.id.podcast_autodownload_container);
            folderStats = v.findViewById(R.id.podcast_folder_stats);
        }

        void bind(Podcast podcast, OnActionListener listener) {

            title.setText(podcast.title);
            // desc.setText(podcast.language); // placeholder (you could fetch/show `feedId`
            // or something better)
            desc.setVisibility(View.GONE);
            //Glide.with(image.getContext()).load(StorageHelper.checkAndCleanImagePath(image.getContext(), podcast.image)).into(image);
            KanLogger.myLog(podcast.image);
            Glide.with(image.getContext()).load(podcast.image).into(image);

            // Read-only indicator, not a button: no click listener, so a tap on it falls through
            // to itemView's own click (same as tapping anywhere else on the card).
            autoDownloadContainer.setVisibility(podcast.autoDownload ? View.VISIBLE : View.GONE);

            /// STATS
            AppDatabase.databaseWriteExecutor.execute(() -> {
                Folder folder = (podcast.idFolder != null && podcast.idFolder > 0)
                        ? AppDatabase.getDatabase(itemView.getContext()).folderDao().getById(podcast.idFolder)
                        : null;
                long timeListenedSec = AppDatabase.getDatabase(itemView.getContext()).podcastDao()
                        .getTotalTimeListenedForPodcast(podcast.getId());

                new Handler(Looper.getMainLooper()).post(() -> {
                    String listenedFor = timeListenedSec > 0
                            ? itemView.getContext().getString(R.string.podcast_listened_for,
                                    Tonio.formatTime(timeListenedSec * 1000))
                            : null;

                    if (folder != null) {
                        String nbFile = folder.nbZikFile + " tracks";
                        String duration = Tonio.formatTime(folder.getDuration());
                        String percentDone = String.format(Locale.US, "%.0f", folder.getPercentdone());
                        String line1 = nbFile + " · " + duration + " · " + percentDone + "% done";
                        setStatsText(folderStats, line1, listenedFor);
                    } else if (listenedFor != null) {
                        setStatsText(folderStats, null, listenedFor);
                    } else {
                        folderStats.setText(com.driot.bookplayer.R.string.no_episode_downloaded);
                    }
                });
            });

            itemView.setOnClickListener(v -> listener.onItemClick(podcast));
        }

        /** Combines an optional plain first line with an optional "Listened for..." second line,
         * italicizing only that second line. */
        private static void setStatsText(TextView tv, @Nullable String line1, @Nullable String listenedForLine) {
            if (line1 == null) {
                if (listenedForLine == null) {
                    tv.setText("");
                    return;
                }
                SpannableString s = new SpannableString(listenedForLine);
                s.setSpan(new StyleSpan(Typeface.ITALIC), 0, listenedForLine.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                tv.setText(s);
                return;
            }
            if (listenedForLine == null) {
                tv.setText(line1);
                return;
            }
            String full = line1 + "\n" + listenedForLine;
            SpannableString s = new SpannableString(full);
            int start = line1.length() + 1;
            s.setSpan(new StyleSpan(Typeface.ITALIC), start, full.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            tv.setText(s);
        }
    }
}
