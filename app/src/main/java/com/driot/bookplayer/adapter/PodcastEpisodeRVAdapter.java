package com.driot.bookplayer.adapter;

import static androidx.core.content.ContextCompat.startActivity;
import static com.driot.bookplayer.utils.PodcastHelper.buildPodcastEpisodeName;
import static com.driot.bookplayer.utils.PodcastHelper.buildPodcastPath;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.LoadOptionsActivity;
import com.driot.bookplayer.activities.PlayActivity;
import com.driot.bookplayer.activities.PodcastEpisodeViewModel;
import com.driot.bookplayer.activities.ZikFileActivity;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.objects.PlayList;
import com.driot.bookplayer.objects.PodcastEpisode;
import com.driot.bookplayer.utils.PodcastDownloadManager;
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
    private final long podcastFeedId;
    private final PodcastEpisodeViewModel viewModel;
    private final LifecycleOwner lifecycleOwner;

    public PodcastEpisodeRVAdapter(Context context, String podcastTitle, long podcastFeedId, PodcastEpisodeViewModel viewModel) {
        this.context = context;
        this.podcastTitle = podcastTitle;
        this.podcastFeedId = podcastFeedId;
        this.viewModel = viewModel;
        this.lifecycleOwner = (LifecycleOwner) context; // Assumes context is a LifecycleOwner (e.g., Activity)
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
            myLog("------------ USER CLICKS EPISODE --------------  [" + episodeFileName + "]");
            myLogD(episode.toString());
            /*
            if (episode.enclosureUrl != null && !episode.enclosureUrl.isEmpty()) {
                //showDownloadOptionsDialog(context, episode.enclosureUrl, episode.title);
            } else {
                myToastE("No audio URL available");
            }
             */
            clickOnEpisode(holder, episode);
        });

// Check if in physical folder : reserved sd card or reserved smartphone storage
        boolean isDownloaded = false;
        File folderPodcastEpisode = buildPodcastPath(context, podcastTitle, false);
        File file = new File(folderPodcastEpisode, episodeFileName);
        isDownloaded = file.exists();
        if (!isDownloaded) {
            folderPodcastEpisode = buildPodcastPath(context, podcastTitle, true);
            file = new File(folderPodcastEpisode, episodeFileName);
            isDownloaded = file.exists();
        }
        //myLogW(podcastTitle + " - " + episodeFileName);
        LiveData<ZikFile> liveZikFile = viewModel.getZikFileLive(podcastTitle, episodeFileName); //changed from full path to just folder name, to deal with multiple locations
        liveZikFile.removeObservers(lifecycleOwner); //not sure it is usefull
        holder.icon_1.setTag(episodeFileName); // ---- avoid stop flickers on another completion -- Sometimes the LiveData callback gets called even after the view has been recycled
        boolean finalIsDownloaded = isDownloaded;
        liveZikFile.observe(lifecycleOwner, zikFile -> {
            if (!holder.icon_1.getTag().equals(episodeFileName)) return; // ---- avoid stop flickers on another completion --
            if (zikFile != null) {
                if (holder.flickerRunning && holder.flickerAnim != null) {
                    holder.flickerRunning = false;
                    holder.flickerAnim.cancel();
                    holder.flickerAnim = null;
                    holder.icon_1.setScaleX(1f);
                    holder.icon_1.setScaleY(1f);
                }
                holder.zikFile = zikFile;
                String percentDone = String.format(Locale.US, "%.0f", zikFile.getPercentdone());
                String lastAdded = "Added : " + android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", zikFile.date_added);
                String stats2 = percentDone + "% listened\n" + lastAdded;
                holder.tvEpisodeDBStats.setText(stats2);
                holder.icon_1.setVisibility(View.VISIBLE);
                holder.icon_1.setColorFilter(ContextCompat.getColor(context, R.color.green_300));
            } else if (finalIsDownloaded) {
                holder.tvEpisodeDBStats.setText("");
                holder.icon_1.setVisibility(View.VISIBLE);
                holder.icon_1.setColorFilter(ContextCompat.getColor(context, R.color.orange_500));
            } else {
                holder.tvEpisodeDBStats.setText("");
                holder.icon_1.setVisibility(View.VISIBLE);
                holder.icon_1.setColorFilter(ContextCompat.getColor(context, R.color.pastel_blue_300));
                holder.icon_1.setOnClickListener(v -> {
                    myLog("---- USER CLICKS ----- Downloading single episode: " + episode.title);
                    if (holder.flickerAnim == null) {
                        holder.flickerRunning = true;
                        holder.flickerAnim = createFlickerAnimation(holder.icon_1,holder);
                        holder.flickerAnim.start();
                    }
                    File targetFolder = buildPodcastPath(context, podcastTitle);
                    if (!targetFolder.exists()) targetFolder.mkdirs();

                    List<PodcastEpisode> singleList = new ArrayList<>();
                    singleList.add(episode);

                    PodcastDownloadManager.enqueueDownloads(context, podcastFeedId, singleList, targetFolder, null);
                });
            }
        });
/*

// Check if in physical folder
        File folderPodcastEpisode = buildPodcastPath(context, podcastTitle);
        File file = new File(folderPodcastEpisode, episodeFileName);
        boolean isDownloaded = file.exists();

// Check if in DB (e.g., ZikFile table)
        if (isDownloaded) {
            //myLog("downloaded" + episode.title);
            AppDatabase.databaseWriteExecutor.execute(() -> {
                ZikFile zf = AppDatabase.getDatabase(context)
                        .ZikFileDao()
                        .getZikFileFromFullPath(folderPodcastEpisode.getAbsolutePath(), episodeFileName);  // You may use URL, title, or unique hash
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (zf != null) {
                        //myLog("DB ok" + episode.title);
                        String percentDone = String.format(Locale.US, "%.0f", zf.getPercentdone());
                        String lastAdded = "Added : " + android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", zf.date_added);
                        String stats2 = percentDone + "% listened"
                                + "\n" + lastAdded;
                        holder.tvEpisodeDBStats.setText(stats2);
                        holder.icon_1.setVisibility(View.VISIBLE);
                        holder.icon_1.setColorFilter(ContextCompat.getColor(context, R.color.green_300));
                    } else {
                        //myLog("DB not ok" + episode.title);
                        holder.tvEpisodeDBStats.setText("");
                        holder.icon_1.setVisibility(View.VISIBLE);
                        holder.icon_1.setColorFilter(ContextCompat.getColor(context, R.color.orange_500));
                        myLogEE(null, "in FileFolder but not in DB..." + folderPodcastEpisode.getAbsolutePath() + "/" + episodeFileName + " feedID = " + podcastFeedId);
                    }
                });
            });
        } else {
            //myLog("not downloaded " + episode.title);
            holder.tvEpisodeDBStats.setText("");
            holder.icon_1.setVisibility(View.VISIBLE);
            holder.icon_1.setColorFilter(ContextCompat.getColor(context, R.color.pastel_blue_300));
            holder.icon_1.setOnClickListener(v -> {
                myLog("---- USER CLICKS ----- Downloading single episode: " + episode.title);

                File targetFolder = buildPodcastPath(context, podcastTitle);
                if (!targetFolder.exists()) targetFolder.mkdirs();

                List<PodcastEpisode> singleList = new ArrayList<>();
                singleList.add(episode);

                PodcastDownloadManager.enqueueDownloads(context, podcastFeedId, singleList, targetFolder, null);
            });
        }
 */
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDate, tvEpisodeStats, tvEpisodeDBStats;
        ImageView icon_1;
        AnimatorSet flickerAnim;
        boolean flickerRunning = false;
        ZikFile zikFile;

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
    private void flickerIcon(ImageView icon) {
        float maxSize = 1.6f;
        int animTime = 300;

        ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(icon, "scaleX", 1f, maxSize);
        ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(icon, "scaleY", 1f, maxSize);
        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(icon, "scaleX", maxSize, 1f);
        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(icon, "scaleY", maxSize, 1f);

        AnimatorSet scaleUp = new AnimatorSet();
        scaleUp.playTogether(scaleUpX, scaleUpY);

        AnimatorSet scaleDown = new AnimatorSet();
        scaleDown.playTogether(scaleDownX, scaleDownY);

        AnimatorSet flicker = new AnimatorSet();
        flicker.playSequentially(scaleUp, scaleDown);
        flicker.setDuration(animTime);
        flicker.start();
    }
    private AnimatorSet createFlickerAnimation(View view, ViewHolder holder) {
        float maxSize = 1.4f;
        int animTime = 300;

        ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(view, "scaleX", 1f, maxSize);
        ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(view, "scaleY", 1f, maxSize);
        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(view, "scaleX", maxSize, 1f);
        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(view, "scaleY", maxSize, 1f);

        AnimatorSet scaleUp = new AnimatorSet();
        scaleUp.playTogether(scaleUpX, scaleUpY);

        AnimatorSet scaleDown = new AnimatorSet();
        scaleDown.playTogether(scaleDownX, scaleDownY);

        AnimatorSet flicker = new AnimatorSet();
        flicker.playSequentially(scaleUp, scaleDown);
        flicker.setDuration(animTime);

        flicker.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (holder.flickerRunning) {
                    flicker.start();  // loop again
                }
            }
        });

        return flicker;
    }

    private void clickOnEpisode(ViewHolder holder, PodcastEpisode episode) {
        playThatShit(holder);
    }

    private void playThatShit(ViewHolder holder) {
        new Thread(() -> {
            try {
                ZikFile zikFile = holder.zikFile;
                List<ZikFile> zikFilesList = AppDatabase.getDatabase(context).ZikFileDao().getZikFiles(zikFile.getIdFolder());
                PlayList.create(context, zikFilesList);
                int rankZikFile = getZikFileRankInFolderSync(zikFilesList, zikFile.getName());
                myLog("rankZikFile = " + rankZikFile);
                if (rankZikFile >= 0 ) {
                    PlayList.getInstance().setNumZikFile(rankZikFile);
                    context.startActivity(new Intent(this.context, PlayActivity.class).putExtra("ZikFile", zikFile));
                }
            } catch (Exception e) {
                myLogEE(e, "clickOnEpisode - playThatShit");
            }
        }).start();
    }
    public int getZikFileRankInFolderSync(List<ZikFile> files, String fileName) {
        for (int i = 0; i < files.size(); i++) {
            if (files.get(i).getName().equals(fileName)) {
                return i ;
            }
        }
        return -1; // not found
    }

}
