package com.driot.bookplayer.adapter;

import static com.driot.bookplayer.global.Var.PODCASTINDEXORG_SINCE_DEBUG;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.utils.PodcastHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingRVAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Adapter for Podcast (Room entity)
public class PodcastFavoritesRVAdapter extends LoggingRVAdapter<PodcastFavoritesRVAdapter.PodcastViewHolder> {

    private List<Podcast> items = new ArrayList<>();
    private final OnItemClickListener listener;
    private final OnAutoDownloadToggleListener autoDownloadToggleListener;

    public interface OnItemClickListener {
        void onItemClick(Podcast podcast);
    }

    public interface OnAutoDownloadToggleListener {
        void onToggle(Podcast podcast, boolean newState);
    }

    public PodcastFavoritesRVAdapter(OnItemClickListener listener, OnAutoDownloadToggleListener autoDownloadToggleListener) {
        this.listener = listener;
        this.autoDownloadToggleListener = autoDownloadToggleListener;
    }

    public void setItems(List<Podcast> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PodcastViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.recyclerview_podcast_result, parent, false);
        return new PodcastViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull PodcastViewHolder holder, int position) {
        Podcast podcast = items.get(position);
        holder.bind(podcast, listener, autoDownloadToggleListener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class PodcastViewHolder extends RecyclerView.ViewHolder {
        TextView title, desc, folderStats;
        ImageView image, autoDownload;

        PodcastViewHolder(View v) {
            super(v);
            title = v.findViewById(R.id.podcast_title);
            desc = v.findViewById(R.id.podcast_desc);
            image = v.findViewById(R.id.podcast_image);
            autoDownload = v.findViewById(R.id.podcast_autodownload);
            folderStats = v.findViewById(R.id.podcast_folder_stats);
        }

        void bind(Podcast podcast,
                  OnItemClickListener listener,
                  OnAutoDownloadToggleListener autoDownloadToggleListener) {

            title.setText(podcast.title);
            //desc.setText(podcast.language); // placeholder (you could fetch/show `feedId` or something better)
            desc.setVisibility(View.GONE);
            Glide.with(image.getContext()).load(podcast.image).into(image);


            ///  AUTO DOWNLOAD BUTTON
            autoDownload.setVisibility(View.VISIBLE);

            int colorRes = podcast.autoDownload ? R.color.green_500 : R.color.gray_500;
            int tint = itemView.getContext().getColor(colorRes);
            autoDownload.setColorFilter(tint);

            autoDownload.setOnClickListener(v -> {
                boolean newState = !podcast.autoDownload;
                podcast.autoDownload = newState;

                // Update tint immediately
                int newTint = itemView.getContext().getColor(newState ? R.color.green_500 : R.color.gray_500);
                autoDownload.setColorFilter(newTint);

                // Callback to update DB
                if (autoDownloadToggleListener != null) {
                    autoDownloadToggleListener.onToggle(podcast, newState);
                }

                // ⬇ Trigger download if enabled
                if (newState) {
                    PodcastHelper.checkForNewEpisodesToAutoDownload(itemView.getContext(), podcast, PODCASTINDEXORG_SINCE_DEBUG);
                }
            });

            ///  STATS
            if (podcast.idFolder != null && podcast.idFolder > 0) {
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    Folder folder = AppDatabase.getDatabase(itemView.getContext()).FolderDao().getById(podcast.idFolder);
                    if (folder != null) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            String nbFile = folder.nbZikFile + " tracks";
                            String duration = Tonio.formatTime(folder.getDuration());
                            String percentDone = String.format(Locale.US, "%.0f", folder.getPercentdone());
                            String lastAdded = "Updated : " + android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", folder.date_last_zikfile_added);
                            String stats = nbFile + " · " + duration + " · " + percentDone + "% done"
                                    + "\n" + lastAdded;
                            folderStats.setText(stats);
                        });
                    } else {
                        folderStats.setText("No episode downloaded");
                    }
                });
            } else {
                folderStats.setText("No episode downloaded");
            }

            itemView.setOnClickListener(v -> listener.onItemClick(podcast));
        }
    }
}
