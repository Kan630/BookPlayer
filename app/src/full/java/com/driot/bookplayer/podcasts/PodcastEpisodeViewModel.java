package com.driot.bookplayer.podcasts;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Episode;
import com.driot.bookplayer.db.EpisodeDao;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.PodcastDao;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.db.ZikFileDao;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import java.util.ArrayList;
import java.util.List;

public class PodcastEpisodeViewModel extends LoggingAndroidViewModel {
    private final ZikFileDao zikFileDao;
    private final EpisodeDao episodeDao;
    private final PodcastDao podcastDao;

    private final MutableLiveData<List<DisplayableEpisode>> episodesLive = new MutableLiveData<>();
    private final MutableLiveData<String> searchQueryLive = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> searchInDescriptionLive = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isFetchingLive = new MutableLiveData<>(false);
    private final MutableLiveData<String> toastMessageLive = new MutableLiveData<>();
    private final MutableLiveData<String> apiErrorLive = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isExpandedLive = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> sortNewestFirstLive = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> showOnlyNeverDownloadedLive = new MutableLiveData<>(false);
    private final MutableLiveData<DisplayableEpisode> currentEpisodeLive = new MutableLiveData<>();
    private final java.util.Set<Long> enqueuedEpisodeIds = new java.util.HashSet<>();

    private Boolean last_sort_newest_top;

    public PodcastEpisodeViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getDatabase(application);
        zikFileDao = db.zikFileDao();
        episodeDao = db.episodeDao();
        podcastDao = db.podcastDao();
    }

    // ---------------------------------
    // DB
    // ---------------------------------

    // insert episodes gotten from api to db
    private void insertEpisodesInDBSync(List<PodcastEpisode> podcastEpisodes, long podcastFeedId) {
        int podcastId = podcastDao.getPodcastByFeedId(podcastFeedId).getId();
        List<Episode> toSave = PodcastHelper.convertToEpisodes(podcastEpisodes, podcastId);
        episodeDao.insertAll(toSave);
    }

    public List<Episode> toggleSortAndGetEpisodesFromDB(int podcastId) {
        Podcast podcast = podcastDao.getById(podcastId);
        boolean sort_newest_top;
        if (podcast != null) {
            sort_newest_top = !podcast.sort_newest_top;
            podcast.sort_newest_top = sort_newest_top;
            podcastDao.update(podcast);
            myLogD("toggle sort, changed in DB,  sort_newest_top: " + sort_newest_top);
        } else {
            if (last_sort_newest_top == null) {
                sort_newest_top = Option.getPodcastEpisodesSortOrder();
                myLogD("toggle sort, Option tells sort_newest_top: " + sort_newest_top);
            } else {
                sort_newest_top = !last_sort_newest_top;
                last_sort_newest_top = sort_newest_top;
                myLogD("toggle sort, lastValue tells sort_newest_top: " + sort_newest_top);
            }
        }
        if (sort_newest_top) {
            return episodeDao.getAllEpisodesForPodcastNewestFirst(podcastId);
        } else {
            return episodeDao.getAllEpisodesForPodcastOldestFirst(podcastId);
        }
    }

    public List<Episode> getEpisodesFromDB(int podcastId) {
        Podcast podcast = podcastDao.getById(podcastId);
        boolean sort_newest_top;
        if (podcast != null) {
            sort_newest_top = podcast.sort_newest_top;
            myLogD("getEpisodesFromDB, DB tells sort_newest_top: " + sort_newest_top);
        } else {
            if (last_sort_newest_top == null) {
                sort_newest_top = Option.getPodcastEpisodesSortOrder();
                myLogD("getEpisodesFromDB, Option tells sort_newest_top: " + sort_newest_top);
            } else {
                sort_newest_top = !last_sort_newest_top;
                last_sort_newest_top = sort_newest_top;
                myLogD("getEpisodesFromDB, lastValue tells sort_newest_top: " + sort_newest_top);
            }
        }
        if (sort_newest_top) {
            return episodeDao.getAllEpisodesForPodcastNewestFirst(podcastId);
        } else {
            return episodeDao.getAllEpisodesForPodcastOldestFirst(podcastId);
        }
    }

    public LiveData<ZikFile> getZikFileLive(String folderName, String fileName) {
        return zikFileDao.getZikFileLive(folderName, fileName);
    }

    public LiveData<Podcast> getPodcastLiveByFeedId(long feedId) {
        return podcastDao.getPodcastLiveByFeedId(feedId);
    }

    public LiveData<ZikFile> getLastListenedZikFileForPodcast(long feedId) {

        LiveData<ZikFile> zf = zikFileDao.getLastListenedZikFileForPodcast(feedId);
        // myLogD("getLastListenedZikFileForPodcast : " + Objects.toString(zf.getValue()
        // != null ? zf.getValue().getName() : null));
        return zf;
    }

    public Long getLastPublishedForPodcastSync(long podcastId) {
        return episodeDao.getMaxDatePublishedForPodcast(podcastId);
    }

    // Runs once per podcast (guarded by the UNKNOWN->CHECKING claim below) - see
    // ImageHelper.determineEpisodeCoverStatus() for the actual hash-comparison logic.
    private void maybeDetermineEpisodeCoverStatus(android.content.Context context, Podcast podcast,
            List<PodcastEpisode> apiEpisodes) {
        Podcast dbPodcast = podcastDao.getPodcastByFeedId(podcast.feedId);
        if (dbPodcast == null) {
            // fetchEpisodes() runs on every podcast page open, whether or not the podcast has
            // ever been favorited/downloaded - create the row on demand here too (same as
            // toggleFavorite()/onDownloadEpisode() already do), otherwise there's nowhere to
            // persist the determination and this silently never runs.
            PodcastHelper.addPodcastToDB(context,
                    new PodcastFeed(podcast.feedId, podcast.title, podcast.image, podcast.description));
            dbPodcast = podcastDao.getPodcastByFeedId(podcast.feedId);
            if (dbPodcast == null) {
                myLogE("maybeDetermineEpisodeCoverStatus: could not create Podcast row for feedId=" + podcast.feedId);
                return;
            }
        }
        if (dbPodcast.episodeCoverStatus != Podcast.EPISODE_COVER_STATUS_UNKNOWN) {
            return; // already checking, or already concluded
        }
        podcastDao.updateEpisodeCoverStatus(podcast.feedId, Podcast.EPISODE_COVER_STATUS_CHECKING);

        // CHECKING blocks every future attempt (see the guard above), so if anything below
        // throws unexpectedly, fall back to UNKNOWN rather than leaving the podcast stuck
        // forever with no way to retry.
        int finalStatus = Podcast.EPISODE_COVER_STATUS_UNKNOWN;
        try {
            List<String> candidateUrls = new ArrayList<>();
            for (PodcastEpisode pe : apiEpisodes) {
                if (pe.image != null && !pe.image.isEmpty()) {
                    candidateUrls.add(pe.image);
                }
            }

            String podcastImageUrl = dbPodcast.imageOriginalUrl != null ? dbPodcast.imageOriginalUrl
                    : dbPodcast.image;
            myLogI("episode cover check for [" + dbPodcast.title + "]: podcastImageUrl=[" + podcastImageUrl + "] "
                    + candidateUrls.size() + "/" + apiEpisodes.size() + " episodes have a non-empty image");
            int status = ImageHelper.determineEpisodeCoverStatus(podcastImageUrl, candidateUrls);
            // ImageHelper is shared with the "pure" flavor and can't reference Podcast directly -
            // translate its flavor-neutral result here instead.
            if (status == ImageHelper.COVER_STATUS_DISTINCT) {
                finalStatus = Podcast.EPISODE_COVER_STATUS_DISTINCT;
            } else if (status == ImageHelper.COVER_STATUS_NOT_DISTINCT) {
                finalStatus = Podcast.EPISODE_COVER_STATUS_NOT_DISTINCT;
            } // else inconclusive - stays UNKNOWN, retried on a future fetch
        } catch (Throwable t) {
            myLogEE(t, "maybeDetermineEpisodeCoverStatus failed for [" + dbPodcast.title + "]");
        }
        podcastDao.updateEpisodeCoverStatus(podcast.feedId, finalStatus);
        myLogI("episode cover status for [" + dbPodcast.title + "] = " + finalStatus);
    }

    // ---------------------------------
    // API & Retrieval
    // ---------------------------------

    public LiveData<List<DisplayableEpisode>> getEpisodesLive() {
        return episodesLive;
    }

    public MutableLiveData<String> getSearchQueryLive() {
        return searchQueryLive;
    }

    public MutableLiveData<Boolean> getSearchInDescriptionLive() {
        return searchInDescriptionLive;
    }

    public LiveData<Boolean> getIsFetchingLive() {
        return isFetchingLive;
    }

    public LiveData<String> getToastMessageLive() {
        return toastMessageLive;
    }

    public LiveData<String> getApiErrorLive() {
        return apiErrorLive;
    }

    public MutableLiveData<Boolean> getIsExpandedLive() {
        return isExpandedLive;
    }

    public MutableLiveData<Boolean> getSortNewestFirstLive() {
        return sortNewestFirstLive;
    }

    public MutableLiveData<Boolean> getShowOnlyNeverDownloadedLive() {
        return showOnlyNeverDownloadedLive;
    }

    public MutableLiveData<DisplayableEpisode> getCurrentEpisodeLive() {
        return currentEpisodeLive;
    }

    public java.util.Set<Long> getEnqueuedEpisodeIds() {
        return enqueuedEpisodeIds;
    }

    public void fetchEpisodes(android.content.Context context, Podcast podcast, boolean forceRefresh) {
        myLogD("fetchEpisodes, forceRefresh=" + forceRefresh);
        if (Boolean.TRUE.equals(isFetchingLive.getValue())) {
            myLogD("already fetching, exit");
            return;
        }

        isFetchingLive.setValue(true);
        apiErrorLive.setValue(null);

        // 1) Load from DB immediately
        new Thread(() -> {
            List<Episode> dbEpisodes = getEpisodesFromDB(podcast.getId());
            myLogD("DB episodes count: " + dbEpisodes.size());
            List<DisplayableEpisode> initial = DisplayableEpisode.fromEpisodeList(dbEpisodes);
            episodesLive.postValue(initial);

            long lastCheckAgoMs = System.currentTimeMillis() - podcast.lastCheck;
            myLogD("last refresh was " + Tonio.formatTime(lastCheckAgoMs) + " ago...  Const time between checks = " + Var.PODCAST_INDEX_ORG_API_TIME_BETWEEN_PODCAST_CHECK_IN_MIN + " min");
            if (!forceRefresh && lastCheckAgoMs < 1000 * 60 * Var.PODCAST_INDEX_ORG_API_TIME_BETWEEN_PODCAST_CHECK_IN_MIN) {
                isFetchingLive.postValue(false);
                myLogD("last fetch too recent, exit");
                return;
            }

            // 2) Compute "since" from DB and then hit API
            long since;
            int maxEpisode;
            if (forceRefresh) {
                since = 0;
                maxEpisode = Var.PODCAST_INDEX_ORG_API_MAX_RESULTS_FOR_EPISODES_REFRESH_MODE;
            } else {
                maxEpisode = Var.PODCAST_INDEX_ORG_API_MAX_RESULTS_FOR_EPISODES_NORMAL_MODE;
                Long lastPublished = getLastPublishedForPodcastSync(podcast.getId());
                since = (lastPublished == null) ? 0L
                        : Math.max(0L, lastPublished
                                - 60 * 60 * 24 * Var.PODCAST_INDEX_ORG_API_MAX_DAYS_FOR_EPISODES_NORMAL_MODE);
            }

            PodcastHelper.getEpisodesByFeedId(
                    context,
                    podcast.feedId,
                    since,
                    maxEpisode,
                    true,
                    new PodcastHelper.EpisodeCallback() {
                        @Override
                        public void onSuccess(List<PodcastEpisode> apiEpisodes) {
                            myLogI("API CALL - returned episodes list size: " + apiEpisodes.size());
                            new Thread(() -> {
                                // 3) Persist new/updated from API (Synchronous on this thread)
                                insertEpisodesInDBSync(apiEpisodes, podcast.feedId);

                                // 4) Refresh DB and merge for display
                                List<Episode> updatedDbEpisodes = getEpisodesFromDB(podcast.getId());
                                List<DisplayableEpisode> fullList = DisplayableEpisode.mergeDisplayableEpisodes(
                                        apiEpisodes,
                                        updatedDbEpisodes);

                                int nbEpisodeFull = fullList.size();
                                myLog("Displayed episodes count: " + nbEpisodeFull);

                                if (forceRefresh) {
                                    String msg = nbEpisodeFull + " "
                                            + getApplication().getString(com.driot.bookplayer.R.string.episodes);
                                    toastMessageLive.postValue(msg);
                                }

                                episodesLive.postValue(fullList);
                                isFetchingLive.postValue(false);

                                // Fire-and-forget: needs up to 6 image downloads to hash, so it
                                // runs on its own thread rather than delaying the episode list.
                                new Thread(() -> maybeDetermineEpisodeCoverStatus(context, podcast, apiEpisodes))
                                        .start();
                            }).start();
                        }

                        @Override
                        public void onError(Exception e) {
                            myLogE("API CALL ERROR - " + e.getMessage());
                            isFetchingLive.postValue(false);
                            apiErrorLive.postValue(e.getMessage());
                        }
                    });
        }).start();
    }
}
