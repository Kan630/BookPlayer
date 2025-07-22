package com.driot.bookplayer.adapter;

import static com.driot.bookplayer.utils.PodcastIndexHelper.buildPodcastEpisodeName;
import static com.driot.bookplayer.utils.PodcastIndexHelper.buildPodcastPath;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.LoadOptionsActivity;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.objects.PodcastEpisode;
import com.driot.bookplayer.services.DownloadJobService;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingRVAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PodcastEpisodeRVAdapter extends LoggingRVAdapter<PodcastEpisodeRVAdapter.ViewHolder> {

    private List<PodcastEpisode> items = new ArrayList<>();
    private final Context context;
    private final String podcastTitle;

    public PodcastEpisodeRVAdapter(Context context, String podcastTitle) {
        this.context = context;
        this.podcastTitle = podcastTitle;
    }

    public void setItems(List<PodcastEpisode> episodes) {
        this.items = episodes;
    }

    @NonNull
    @Override
    public PodcastEpisodeRVAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.recyclerview_podcast_episode, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PodcastEpisodeRVAdapter.ViewHolder holder, int position) {
        PodcastEpisode episode = items.get(position);
        holder.tvTitle.setText(episode.title);
        holder.tvDate.setText(episode.datePublishedPretty != null ? episode.datePublishedPretty : "");
        String stats = Tonio.formatTime(episode.duration*1000) + " (" + Tonio.getReadableSize(episode.enclosureLength) + ")";
        holder.tvEpisodeStats.setText(stats);

        String episodeFileName = buildPodcastEpisodeName(episode);

        holder.itemView.setOnClickListener(v -> {
            myLog("Episode clicked: " + episodeFileName);
            if (episode.enclosureUrl != null && !episode.enclosureUrl.isEmpty()) {
                myLogD("URL : " + episode.enclosureUrl);
                //showDownloadOptionsDialog(context, episode.enclosureUrl, episode.title);
            } else {
                myToast("No audio URL available");
            }
        });

// Check if in physical folder
        File folderPodcastEpisode = buildPodcastPath(context, podcastTitle);
        File file = new File(folderPodcastEpisode, episodeFileName);
        boolean isDownloaded = file.exists();

// Check if in DB (e.g., ZikFile table)
        AppDatabase.databaseWriteExecutor.execute(() -> {
            ZikFile zf = AppDatabase.getDatabase(context)
                    .ZikFileDao()
                    .getZikFileFromFullPath(folderPodcastEpisode.getAbsolutePath(), episodeFileName);  // You may use URL, title, or unique hash

            new Handler(Looper.getMainLooper()).post(() -> {
                if (zf != null) {
                    String percentDone = String.format(Locale.US, "%.0f", zf.getPercentdone());
                    String lastAdded = "Added : " + android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", zf.date_added);
                    String stats2 = percentDone + "% done"
                            + "\n" + lastAdded;
                    holder.tvEpisodeDBStats.setText(stats2);
                    holder.icon_1.setVisibility(View.VISIBLE);
                    holder.icon_1.setColorFilter(R.color.green_300);
                } else if (isDownloaded) {
                    holder.tvEpisodeDBStats.setText("");
                    holder.icon_1.setVisibility(View.VISIBLE);
                    holder.icon_1.setColorFilter(R.color.orange_500);
                } else {
                    holder.tvEpisodeDBStats.setText("");
                    holder.icon_1.setVisibility(View.GONE);
                }

            });
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDate, tvEpisodeStats, tvEpisodeDBStats;
        ImageView icon_1;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvEpisodeTitle);
            tvDate = itemView.findViewById(R.id.tvEpisodeDate);
            tvEpisodeStats = itemView.findViewById(R.id.tvEpisodeStats);
            tvEpisodeDBStats = itemView.findViewById(R.id.tvEpisodeDBstats);
            icon_1 = itemView.findViewById(R.id.icon_1);
        }
    }


    private void showDownloadOptionsDialog(Context context, String fileUrl, String episodeTitle) {
        String[] options = {
                "Download to Downloads folder",
                "Download to SD card",
                "Download to BookPlayer internal storage",
                "Just play (no download)",
                "add to Bookplayer",
        };

        new AlertDialog.Builder(context)
                .setTitle("Choose an action")
                .setItems(options, (dialog, which) -> {
                    String destinationFolder = null;

                    switch (which) {
                        case 0: // Downloads
                            destinationFolder = Environment.getExternalStoragePublicDirectory(
                                    Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
                            break;

                        case 1: // SD Card
                            File[] externalDirs = context.getExternalFilesDirs(null);
                            if (externalDirs.length > 1 && externalDirs[1] != null) {
                                destinationFolder = externalDirs[1].getAbsolutePath();
                            } else {
                                Toast.makeText(context, "No SD card found", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            break;

                        case 2: // Internal storage
                            File internal = new File(context.getFilesDir(), "bookplayer_downloads");
                            if (!internal.exists()) internal.mkdirs();
                            destinationFolder = internal.getAbsolutePath();
                            break;

                        case 3: // Play directly
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setDataAndType(Uri.parse(fileUrl), "audio/*");
                            context.startActivity(Intent.createChooser(intent, "Play episode"));
                            return; // Skip download

                        case 4: // For Test purposes
                            Intent intentLOA = new Intent(this.context, LoadOptionsActivity.class);
                            intentLOA.putExtra(LoadOptionsActivity.EXTRA_URI, Uri.parse(fileUrl));
                            intentLOA.putExtra(LoadOptionsActivity.EXTRA_TYPE, "Podcast");
                            context.startActivity(intentLOA);
                            return;
                    }

                    // Schedule the download job
                    myLog("downloading to : " + destinationFolder);
                    startDownloadJob(context, fileUrl, destinationFolder, episodeTitle, episodeTitle);
                })
                .show();
    }
    private void startDownloadJob(Context context, String fileUrl, String destinationFolder, String episodeTitle, String audioBookTitle) {
        myLog("************************************** startDownloadJob");
        myLog("fileUrl = " + fileUrl);
        myLog("destinationFolder = " + destinationFolder);
        myLog("audioBookTitle = " + audioBookTitle);
        myLog("episodeTitle = " + episodeTitle);
        myLog("**************************************");
        /*
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString("fileUrl", fileUrl);
        bundle.putString("destinationFolder", destinationFolder);
        bundle.putString("audioBookTitle", audioBookTitle);

        JobInfo jobInfo = new JobInfo.Builder(123, new ComponentName(context, DownloadJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setExtras(bundle)
                .setOverrideDeadline(5000) // Run soon
                .build();

        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        scheduler.schedule(jobInfo);

         */
    }



}
