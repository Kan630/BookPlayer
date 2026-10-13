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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        List<Podcast> next = newItems != null ? newItems : new ArrayList<>();
        // This list is fed by a Room query over Podcast + Folder + Episode, and playback writes to
        // those tables every second, so it re-emits constantly. Rebuilding every row each time is
        // what made the list flicker - and reload every cover - while scrolling, so an emission
        // that changes nothing is taken but not announced.
        if (isHistoryMode == this.historyMode && sameOrder(this.items, next)) {
            this.items = next;
            return;
        }
        this.items = next;
        this.historyMode = isHistoryMode;
        notifyDataSetChanged();
    }

    private static boolean sameOrder(List<Podcast> a, List<Podcast> b) {
        if (a == null || b == null || a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).getId() != b.get(i).getId()) {
                return false;
            }
        }
        return true;
    }

    /** The stats line of a row, kept so scrolling back does not query the database again - and
     *  does not blank the line while it waits. Short-lived: those numbers do move. */
    private static final class CachedStats {
        final CharSequence text;
        final long at;

        CachedStats(CharSequence text) {
            this.text = text;
            this.at = System.currentTimeMillis();
        }
    }

    private static final long STATS_CACHE_MS = 60_000;
    // static because the row binding lives in a static ViewHolder; keyed by podcast id and
    // short-lived, so sharing it between instances of this screen is a benefit, not a risk.
    private static final Map<Long, CachedStats> statsCache = new HashMap<>();

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
            // podcast.image is usually a locally cached file: if it's gone, fall back to the feed URL
            Glide.with(image.getContext()).load(podcast.image)
                    .error(Glide.with(image.getContext()).load(podcast.imageOriginalUrl))
                    .into(image);

            // Read-only indicator, not a button: no click listener, so a tap on it falls through
            // to itemView's own click (same as tapping anywhere else on the card).
            autoDownloadContainer.setVisibility(podcast.autoDownload ? View.VISIBLE : View.GONE);

            /// STATS
            final long boundPodcastId = podcast.getId();
            itemView.setTag(boundPodcastId);
            final android.content.Context appContext = itemView.getContext().getApplicationContext();

            CachedStats cached = statsCache.get(boundPodcastId);
            if (cached != null && System.currentTimeMillis() - cached.at < STATS_CACHE_MS) {
                folderStats.setText(cached.text); // straight away: no blank, nothing to flicker
                itemView.setOnClickListener(v -> listener.onItemClick(podcast));
                return;
            }

            // A read, so it belongs on the read executor: on the write one it queued behind the
            // once-a-second playback writes, which is why the line took so long to appear.
            AppDatabase.databaseReadExecutor.execute(() -> {
                Folder folder = (podcast.idFolder != null && podcast.idFolder > 0)
                        ? AppDatabase.getDatabase(appContext).folderDao().getById(podcast.idFolder)
                        : null;
                long timeListenedSec = AppDatabase.getDatabase(appContext).podcastDao()
                        .getTotalTimeListenedForPodcast(podcast.getId());

                new Handler(Looper.getMainLooper()).post(() -> {
                    // the row was reused for another podcast while the stats were read
                    if (!Long.valueOf(boundPodcastId).equals(itemView.getTag()))
                        return;
                    String listenedFor = timeListenedSec > 0
                            ? itemView.getContext().getString(R.string.podcast_listened_for,
                                    Tonio.formatTime(timeListenedSec * 1000))
                            : null;

                    if (folder != null) {
                        String nbFile = itemView.getResources().getQuantityString(R.plurals.tracks_count,
                                (int) folder.nbZikFile, (int) folder.nbZikFile);
                        String duration = Tonio.formatTime(folder.getDuration());
                        String percentDone = String.format(Locale.US, "%.0f", folder.getPercentdone());
                        String line1 = nbFile + " · " + duration + " · " + percentDone + "% "
                                + itemView.getContext().getString(R.string.listened);
                        setStatsText(folderStats, line1, listenedFor);
                        statsCache.put(boundPodcastId, new CachedStats(folderStats.getText()));
                    } else if (listenedFor != null) {
                        setStatsText(folderStats, null, listenedFor);
                        statsCache.put(boundPodcastId, new CachedStats(folderStats.getText()));
                    } else {
                        folderStats.setText(com.driot.bookplayer.R.string.no_episode_downloaded);
                        statsCache.put(boundPodcastId, new CachedStats(folderStats.getText()));
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
