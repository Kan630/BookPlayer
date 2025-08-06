package com.driot.bookplayer.adapter;

import static com.driot.bookplayer.helpers.PodcastHelper.buildPodcastPath;
import static com.driot.bookplayer.helpers.PodcastHelper.findPodcastEpisodeFileIfExists;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.PlayActivity;
import com.driot.bookplayer.activities.PodcastEpisodeViewModel;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Episode;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.objects.PlayList;
import com.driot.bookplayer.objects.PodcastEpisode;
import com.driot.bookplayer.objects.PodcastFeed;
import com.driot.bookplayer.utils.NetworkUtils;
import com.driot.bookplayer.helpers.PodcastHelper;
import com.driot.bookplayer.helpers.ViewHelper;
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
    private final Podcast podcast;
    private final PodcastFeed podcastFeed;
    private final PodcastEpisodeViewModel viewModel;
    private final LifecycleOwner lifecycleOwner;

    public PodcastEpisodeRVAdapter(Context context, Podcast podcast, PodcastFeed podcastFeed, PodcastEpisodeViewModel viewModel) {
        this.context = context;
        this.podcast = podcast;
        this.podcastFeed = podcastFeed;
        this.viewModel = viewModel;
        this.lifecycleOwner = (LifecycleOwner) context; // Assumes context is a LifecycleOwner (e.g., Activity)
        if (podcast!=null) {
            podcastFeed.title = podcast.title;
        } else if (podcastFeed!=null) {
            podcastFeed.title = podcastFeed.title;
        } else {
            podcastFeed.title="error";
            myLogEE(null, "podcast and podcastFeed are null");
        }
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
        String stats = Tonio.formatTime(episode.duration*1000) + (episode.enclosureLength != 0 ? " (" + Tonio.getReadableSize(episode.enclosureLength) + ")" : "");
        holder.tvEpisodeStats.setText(stats);

        String episodeFileName = PodcastHelper.buildPodcastEpisodeFileName(episode);
        String episodeName = PodcastHelper.buildPodcastEpisodeFileName(episode);

        holder.itemView.setOnClickListener(v -> {
            myLog("------------ USER CLICKS EPISODE --------------  [" + episodeName + "] - [" + episodeFileName + "]");
            myLogD(episode.toString());
            clickOnEpisode(holder, episode);
        });

// Check if in physical folder : reserved sd card or reserved smartphone storage
        File downloadedFile = findPodcastEpisodeFileIfExists(context, podcastFeed.title, episodeFileName);
        boolean isDownloaded = (downloadedFile != null);
        boolean isDeleted = false; //TODO to be continued.... the Episode table should be populated as soon as the api is first call, and we need an episodeFeedID

        //myLogW(podcastFeed.title + " - " + episodeFileName);
        //TODO  //not good, if foldername changes, you loose the zikFile, (and if zikfile name changes...)  you need folderID, or feedID or whatever
        //TODO you need to match an episode with a ZikFile, (like the Folder with the Podcast).... maybe we should have feedID or episodeID in ZikFile table...., and a failback on checking names if id changes (should not happen but who knows)?
        LiveData<ZikFile> liveZikFile = viewModel.getZikFileLive(podcastFeed.title, episodeFileName); //changed from full path to just folder name, to deal with multiple locations
        liveZikFile.removeObservers(lifecycleOwner); //not sure it is usefull
        holder.icon_download_done.setTag(episodeFileName); // ---- avoid stop flickers on another completion -- Sometimes the LiveData callback gets called even after the view has been recycled
        liveZikFile.observe(lifecycleOwner, zikFile -> {
            if (!holder.icon_download_done.getTag().equals(episodeFileName)) return; // ---- avoid stop flickers on another completion --
            if (zikFile != null) {
                if (holder.flickerRunning && holder.flickerAnim != null) {
                    holder.flickerRunning = false;
                    holder.flickerAnim.cancel();
                    holder.flickerAnim = null;
                    holder.icon_download_done.setScaleX(1f);
                    holder.icon_download_done.setScaleY(1f);
                }
                holder.zikFile = zikFile;
                String percentDone = String.format(Locale.US, "%.0f", zikFile.getPercentdone());
                String lastAdded = "Added : " + android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", zikFile.date_added);
                String stats2 = percentDone + "% " + ContextCompat.getString(context, R.string.listened) + "\n" + lastAdded;
                holder.tvEpisodeDBStats.setText(stats2);
                holder.icon_download_action.setVisibility(View.GONE);
                holder.icon_download_done.setVisibility(View.VISIBLE);
                holder.icon_download_done.setColorFilter(ContextCompat.getColor(context, R.color.green_300));
                holder.icon_download_done.setOnClickListener(null);
            } else if (isDownloaded) {
                holder.tvEpisodeDBStats.setText("");
                holder.icon_download_action.setVisibility(View.GONE);
                holder.icon_download_done.setVisibility(View.VISIBLE);
                holder.icon_download_done.setColorFilter(ContextCompat.getColor(context, R.color.orange_500));
                holder.icon_download_done.setOnClickListener(null);
            } else if (isDeleted) {
                holder.tvEpisodeDBStats.setText("deleted");
                holder.icon_download_action.setVisibility(View.GONE);
                holder.icon_download_done.setVisibility(View.VISIBLE);
                holder.icon_download_done.setColorFilter(ContextCompat.getColor(context, R.color.brown_700));
                holder.icon_download_done.setOnClickListener(null);
            } else {
                holder.tvEpisodeDBStats.setText("");
                holder.icon_download_done.setVisibility(View.GONE);
                holder.icon_download_action.setVisibility(View.VISIBLE);
                holder.icon_download_action.setColorFilter(ContextCompat.getColor(context, android.R.color.holo_blue_bright));
                holder.icon_download_action.setOnClickListener(v -> {
                    myLogI("---- USER CLICKS - Downloading single episode -----  " + episode.title);
                    if (Option.getNetworkPolicyManualDownload().equals(NetworkUtils.NetworkPolicyManual.ASK_IF_NOT_UNMETERED) && !NetworkUtils.isUnmeteredConnected(context)) {
                        new AlertDialog.Builder(context)
                                .setTitle(R.string.download_warning_title_unmetered)
                                .setMessage(R.string.download_warning_message_unmetered)
                                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                                    proceedWithDownload(context, holder , podcastFeed.title, episode, podcastFeed.id);
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                    } else if (Option.getNetworkPolicyManualDownload().equals(NetworkUtils.NetworkPolicyManual.ASK_IF_NOT_WIFI) && !NetworkUtils.isWifiConnected(context)) {
                        new AlertDialog.Builder(context)
                                .setTitle(R.string.download_warning_title_wifi)
                                .setMessage(R.string.download_warning_message_wifi)
                                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                                    proceedWithDownload(context, holder, podcastFeed.title, episode, podcastFeed.id);
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                    } else {
                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            PodcastHelper.addPodcastToDB(this.context, podcastFeed);
                        });
                        proceedWithDownload(context, holder, podcastFeed.title, episode, podcastFeed.id);
                    }
                });
            }
        });
    }

    private void proceedWithDownload(Context context, ViewHolder holder, String futureFolderName, PodcastEpisode episode, long feedId) {
        if (holder.flickerAnim == null) {
            holder.flickerRunning = true;
            holder.flickerAnim = createFlickerAnimation(holder.icon_download_action,holder);
            holder.flickerAnim.start();
        }
        File targetFolder = buildPodcastPath(context, futureFolderName);
        if (!targetFolder.exists()) targetFolder.mkdirs();

        List<PodcastEpisode> singleList = new ArrayList<>();
        singleList.add(episode);

        PodcastDownloadManager.enqueueDownloads(context, feedId, singleList, targetFolder, null);
}

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDate, tvEpisodeStats, tvEpisodeDBStats;
        ImageView icon_download_done, icon_download_action;
        AnimatorSet flickerAnim;
        boolean flickerRunning = false;
        ZikFile zikFile;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvEpisodeTitle);
            tvDate = itemView.findViewById(R.id.tvEpisodeDate);
            tvEpisodeStats = itemView.findViewById(R.id.tvEpisodeStats);
            tvEpisodeDBStats = itemView.findViewById(R.id.tvEpisodeDBstats);
            icon_download_done = itemView.findViewById(R.id.icon_download_done);
            icon_download_action = itemView.findViewById(R.id.icon_download_action);
        }
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
        ZikFile zikFile = holder.zikFile;
        if (zikFile == null) {
            ViewHelper.showAlterDialogToDisplayText(this.context, episode.description, this.context.getString(R.string.Episode_description));
            return;
        }
        new Thread(() -> {
            try {
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
