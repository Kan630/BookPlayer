package com.driot.bookplayer.adapter;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.PodcastEpisodeViewModel;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.objects.DisplayableEpisode;
import com.driot.bookplayer.objects.PodcastFeed;
import com.driot.bookplayer.helpers.PodcastHelper;
import com.driot.bookplayer.utils.TextOptions;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingRVAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PodcastEpisodeRVAdapter extends LoggingRVAdapter<PodcastEpisodeRVAdapter.ViewHolder> {

    private List<DisplayableEpisode> items = new ArrayList<>();
    public DisplayableEpisode getItem(int position) {
        return (items != null && position >= 0 && position < items.size()) ? items.get(position) : null;
    }
    public int indexOfEpisodeId(long idEpisode) {
        if (items == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).idEpisode == idEpisode) return i;
        }
        return -1;
    }
    public int getCount() { return items != null ? items.size() : 0; }

    private boolean showDescriptions = false;
    public void setShowDescriptions(boolean show) {
        if (this.showDescriptions != show) {
            this.showDescriptions = show;
            notifyDataSetChanged();
        }
    }

    private Long lastListenedZikFileId = null;

    private final Context context;
    private final PodcastFeed podcastFeed;
    private final PodcastEpisodeViewModel viewModel;
    private final LifecycleOwner lifecycleOwner;
    private Long currentlyPlayingEpisodeId = null;

    public interface EpisodeClickHandler {
        void onPlayEpisode(DisplayableEpisode episode);
        void onOpenLocalEpisode(ZikFile zikFile);
        void onDownloadEpisode(DisplayableEpisode episode);
    }

    private final EpisodeClickHandler handler;


    public PodcastEpisodeRVAdapter(Context context, PodcastFeed podcastFeed, PodcastEpisodeViewModel viewModel, EpisodeClickHandler handler) {
        this.context = context;
        this.podcastFeed = podcastFeed;
        this.viewModel = viewModel;
        this.lifecycleOwner = (LifecycleOwner) context; // Assumes context is a LifecycleOwner (e.g., Activity)
        this.handler = handler;
        if (podcastFeed==null) {
            myLogEE(null, "podcastFeed == null");
        }
        // Observe once: last listened ZikFile for this feed
        viewModel.getLastListenedZikFileForPodcast(podcastFeed.id /* or .feedId */)
                .observe(lifecycleOwner, zf -> {
                    lastListenedZikFileId = (zf != null) ? (long) zf.getId() : null;
                    notifyDataSetChanged(); // refresh highlights
                });
    }

    public void setItems(List<DisplayableEpisode> episodes) {
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
        DisplayableEpisode episode = items.get(position);
        //myLog(episode.toString().replace(",","\n"));
        holder.tvTitle.setText(episode.title);
        holder.tvDate.setText(episode.datePublishedPretty != null ? episode.datePublishedPretty : "");
        String stats = Tonio.formatTime(episode.duration*1000) + (episode.enclosureLength != 0 ? " (" + Tonio.getReadableSize(episode.enclosureLength) + ")" : "");
        holder.tvEpisodeStats.setText(stats);
        holder.zikFile = null;

        if (holder.tvEpisodeDesc != null) {
            if (showDescriptions && episode.description != null) {
                // If your descriptions are HTML-ish, reuse your helper to strip/format if needed
                holder.tvEpisodeDesc.setText(TextOptions.parseMaybeHtml(episode.description));
                holder.tvEpisodeDesc.setVisibility(View.VISIBLE);
            } else {
                holder.tvEpisodeDesc.setVisibility(View.GONE);
            }
        }


        String episodeFileName = PodcastHelper.buildPodcastEpisodeFileName(episode);
        String episodeName = PodcastHelper.buildPodcastEpisodeName(episode);

        //default color = surface
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true);
        int colorSurface = typedValue.data;
        holder.llMain.setBackgroundColor(colorSurface);

        if (currentlyPlayingEpisodeId == null) {
            if (lastListenedZikFileId != null && lastListenedZikFileId.equals(episode.idZikFile)) {
                holder.llMain.setBackgroundColor(ContextCompat.getColor(context, R.color.highlight_last_listened));
            }
        } else {
            if (currentlyPlayingEpisodeId.equals(episode.idEpisode)) {
                holder.llMain.setBackgroundColor(ContextCompat.getColor(context, R.color.highlight_current));
            }
        }

        holder.itemView.setOnClickListener(v -> {
            myLogI("------------ USER CLICKS EPISODE --------------  [" + episodeName + "] - [" + episodeFileName + "]");
            myLogD(episode.toString());
            clickOnEpisode(holder, episode);
        });

// Check if in physical folder : reserved sd card or reserved smartphone storage
        File downloadedFile = PodcastHelper.findPodcastEpisodeFileIfExists(context, podcastFeed.title, episodeFileName);
        boolean isDownloaded = (downloadedFile != null);
        boolean isDeleted = episode.date_delete != null;
        boolean isOnlyFromDb = episode.comesFromDb && !episode.comesFromApi;

        LiveData<ZikFile> liveZikFile = viewModel.getZikFileLive(FileHelper.sanitizeFilename(podcastFeed.title), episodeFileName);
        liveZikFile.removeObservers(lifecycleOwner);

        holder.icon_download.setTag(episodeFileName);
        holder.icon_download.setVisibility(View.GONE);

        liveZikFile.observe(lifecycleOwner, zikFile -> {
            if (!holder.icon_download.getTag().equals(episodeFileName)) return; // ---- avoid stop flickers on another completion --

            if (zikFile != null) {
                if (holder.flickerRunning && holder.flickerAnim != null) {
                    holder.flickerRunning = false;
                    holder.flickerAnim.cancel();
                    holder.flickerAnim = null;
                    holder.icon_download.setScaleX(1f);
                    holder.icon_download.setScaleY(1f);
                }

                holder.zikFile = zikFile;
                String percentDone = String.format(Locale.US, "%.0f", zikFile.getPercentdone());
                String lastAdded = "" + android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", zikFile.date_added);
                String stats2 = lastAdded + "\n" + percentDone + "% " + ContextCompat.getString(context, R.string.listened);
                holder.tvEpisodeDBStats.setText(stats2);
                holder.icon_download.setVisibility(View.VISIBLE);
                holder.icon_download.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_download_done_24));
                holder.icon_download.setColorFilter(ContextCompat.getColor(context, R.color.green_300));
                holder.icon_download.setOnClickListener(null);
            } else if (isDownloaded) {
                holder.tvEpisodeDBStats.setText("");
                holder.icon_download.setVisibility(View.VISIBLE);
                holder.icon_download.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_download_done_24));
                holder.icon_download.setColorFilter(ContextCompat.getColor(context, R.color.orange_500));
                holder.icon_download.setOnClickListener(null);
            } else {
                holder.icon_download.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_download_action_24));
                holder.icon_download.setVisibility(View.VISIBLE);
                if (isDeleted) {
                    holder.icon_download.setColorFilter(ContextCompat.getColor(context, R.color.pink_500));
                    String strDelete = context.getString(R.string.added_on) + " " + android.text.format.DateFormat.format("yyyy-MM-dd", episode.date_import)
                        + "\n" + context.getString(R.string.deleted_on) + " " + android.text.format.DateFormat.format("yyyy-MM-dd", episode.date_delete);
                    holder.tvEpisodeDBStats.setText(strDelete);
                    if (isOnlyFromDb) {
                        holder.icon_download.setColorFilter(ContextCompat.getColor(context, R.color.brown_500));
                    }
                } else if (isOnlyFromDb) {
                    holder.icon_download.setColorFilter(ContextCompat.getColor(context, android.R.color.holo_blue_dark));
                    holder.tvEpisodeDBStats.setText("");
                } else {
                    holder.icon_download.setColorFilter(ContextCompat.getColor(context, android.R.color.holo_blue_bright));
                    holder.tvEpisodeDBStats.setText("");
                }
                holder.icon_download.setOnClickListener(v -> {
                    myLogI("---- USER CLICKS - Downloading single episode -----  " + episode.title);
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        PodcastHelper.addPodcastToDB(this.context, podcastFeed);
                    });
                    NetworkHelper.logCurrentNetworkState(this.context);
                    if (Option.getNetworkPolicyManualDownload().equals(NetworkHelper.NetworkPolicyManual.NETWORK_POLICY_UNMETERED) && !NetworkHelper.isUnmeteredConnected(context)) {
                        new AlertDialog.Builder(context)
                                .setTitle(R.string.download_warning_title_unmetered)
                                .setMessage(R.string.download_warning_message_unmetered)
                                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                                    handler.onDownloadEpisode(episode);
                                    if (holder.flickerAnim == null) {
                                        holder.flickerRunning = true;
                                        holder.flickerAnim = createFlickerAnimation(holder.icon_download,holder);
                                        holder.flickerAnim.start();
                                    }
                                })
                                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                                    myLogD("User cancelled download (Network state popup)");
                                })
                                .show();
                    } else {
                        handler.onDownloadEpisode(episode);
                        if (holder.flickerAnim == null) {
                            holder.flickerRunning = true;
                            holder.flickerAnim = createFlickerAnimation(holder.icon_download,holder);
                            holder.flickerAnim.start();
                        }
                    }
                });
            }
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDate, tvEpisodeStats, tvEpisodeDBStats, tvEpisodeDesc;
        ImageButton icon_download;
        AnimatorSet flickerAnim;
        boolean flickerRunning = false;
        ZikFile zikFile;
        LinearLayout llMain;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEpisodeDesc = itemView.findViewById(R.id.tvEpisodeDesc);
            tvTitle = itemView.findViewById(R.id.tvEpisodeTitle);
            tvDate = itemView.findViewById(R.id.tvEpisodeDate);
            tvEpisodeStats = itemView.findViewById(R.id.tvEpisodeStats);
            tvEpisodeDBStats = itemView.findViewById(R.id.tvEpisodeDBstats);
            icon_download = itemView.findViewById(R.id.icon_download);
            llMain = itemView.findViewById(R.id.llMain);
        }
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

    private void clickOnEpisode(ViewHolder holder, DisplayableEpisode episode) {
        ZikFile zikFile = holder.zikFile;
        if (zikFile == null) {
            myLogD("clickOnEpisode, zikfile null → call handler.onPlayEpisode now for " + episode.title);
            handler.onPlayEpisode(episode); // ← play stream directly
            return;
        }
        // Local file exists
        handler.onOpenLocalEpisode(zikFile);

    }
    public void setCurrentlyPlayingEpisodeId(Long id) {
        this.currentlyPlayingEpisodeId = id;
        notifyDataSetChanged();
    }




}
