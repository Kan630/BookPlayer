package com.driot.bookplayer.objects;

import com.driot.bookplayer.db.Episode;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class DisplayableEpisode {

    public boolean comesFromDb = false;
    public boolean comesFromApi = false;

    // Shared fields
    public long idEpisode;
    public String title;
    public String description;
    public long duration;
    public String image;
    public String guid;
    public String podcastGuid;
    public String enclosureUrl;
    public String datePublished;
    public String datePublishedPretty;
    public long enclosureLength;

    // DB-only fields
    public Long id; // Room @PrimaryKey
    public Long idZikFile;
    public Long date_import;
    public Long date_delete;
    public Long lastAccess;

    public static DisplayableEpisode fromApiEpisode(PodcastEpisode pe) {
        DisplayableEpisode de = new DisplayableEpisode();
        de.comesFromApi = true;

        de.idEpisode = pe.id;
        de.title = pe.title;
        de.description = pe.description;
        de.duration = pe.duration;
        de.image = pe.image;
        de.guid = pe.guid;
        //de.podcastGuid = pe.podcastGuid;
        de.enclosureUrl = pe.enclosureUrl;
        de.datePublished = pe.datePublished;
        de.datePublishedPretty = pe.datePublishedPretty;
        de.enclosureLength = pe.enclosureLength;

        return de;
    }

    public static DisplayableEpisode fromDatabaseEpisode(Episode ep) {
        DisplayableEpisode de = new DisplayableEpisode();
        de.comesFromDb = true;

        de.id = ep.id;
        de.idZikFile = ep.idZikFile;
        de.date_import = ep.date_import;
        de.date_delete = ep.date_delete;
        de.lastAccess = ep.lastAccess;

        de.idEpisode = ep.idEpisode;
        de.title = ep.title;
        de.description = ep.description;
        de.duration = ep.duration;
        de.image = ep.image;
        de.guid = ep.guid;
        de.podcastGuid = ep.podcastGuid;
        de.enclosureUrl = ep.enclosureUrl;
        de.datePublished = ep.datePublished;
        de.datePublishedPretty = prettyPrintDate(ep.datePublished);

        return de;
    }

    public static String prettyPrintDate(String datePublished) {
        if (datePublished == null) return "";
        try {
            long millis = Long.parseLong(datePublished) * 1000L;
            Date parsedDate = new Date(millis);
            SimpleDateFormat prettyFormat = new SimpleDateFormat("MMMM dd, yyyy h:mma", Locale.US);
            prettyFormat.setTimeZone(TimeZone.getDefault());
            return prettyFormat.format(parsedDate).toLowerCase();
        } catch (Exception ex) {
            return "";
        }
    }

    public static PodcastEpisode toPodcastEpisode(DisplayableEpisode de) {
        PodcastEpisode pe = new PodcastEpisode();
        pe.id = de.idEpisode;
        pe.title = de.title;
        pe.description = de.description;
        pe.duration = (int) de.duration;
        pe.image = de.image;
        pe.guid = de.guid;
        //pe.podcastGuid = de.podcastGuid;
        pe.enclosureUrl = de.enclosureUrl;
        pe.datePublished = de.datePublished;
        pe.datePublishedPretty = de.datePublishedPretty;
        return pe;
    }
    public PodcastEpisode toPodcastEpisode() {
        return DisplayableEpisode.toPodcastEpisode(this);
    }


    public static List<DisplayableEpisode> mergeDisplayableEpisodes(
            List<PodcastEpisode> apiEpisodes,
            List<Episode> dbEpisodes
    ) {
        Map<Long, DisplayableEpisode> resultMap = new LinkedHashMap<>();

        // First: add DB episodes (they may or may not have an API counterpart)
        for (Episode ep : dbEpisodes) {
            DisplayableEpisode de = DisplayableEpisode.fromDatabaseEpisode(ep);
            resultMap.put(ep.idEpisode, de);
        }

        // Then: merge API episodes
        for (PodcastEpisode pe : apiEpisodes) {
            DisplayableEpisode existing = resultMap.get(pe.id);
            if (existing != null) {
                // Merge API into existing DB-derived entry
                existing.comesFromApi = true;

                // Overwrite or add API fields
                existing.title = pe.title;
                existing.description = pe.description;
                existing.duration = pe.duration;
                existing.image = pe.image;
                existing.guid = pe.guid;
                existing.enclosureUrl = pe.enclosureUrl;
                existing.datePublished = pe.datePublished;
                existing.datePublishedPretty = pe.datePublishedPretty;
                existing.enclosureLength = pe.enclosureLength;

            } else {
                // No match: purely from API
                DisplayableEpisode fromApi = DisplayableEpisode.fromApiEpisode(pe);
                resultMap.put(pe.id, fromApi);
            }
        }

        return new ArrayList<>(resultMap.values());
    }


    public static List<DisplayableEpisode> fromEpisodeList(List<Episode> dbEpisodes) {
        List<DisplayableEpisode> result = new ArrayList<>();
        if (dbEpisodes == null) return result;

        for (Episode ep : dbEpisodes) {
            DisplayableEpisode de = DisplayableEpisode.fromDatabaseEpisode(ep);
            result.add(de);
        }

        return result;
    }


    @Override
    public String toString() {
        return "DisplayableEpisode{" +
                "comesFromDb=" + comesFromDb +
                ", comesFromApi=" + comesFromApi +
                ", idEpisode=" + idEpisode +
                ", title='" + title + '\'' +
                ", id=" + id +
                ", idZikFile=" + idZikFile +
                ", duration=" + duration +
                ", image='" + image + '\'' +
                ", guid='" + guid + '\'' +
                ", podcastGuid='" + podcastGuid + '\'' +
                ", enclosureUrl='" + enclosureUrl + '\'' +
                ", datePublished='" + datePublished + '\'' +
                ", datePublishedPretty='" + datePublishedPretty + '\'' +
                ", enclosureLength=" + enclosureLength +
                ", date_import=" + date_import +
                ", date_delete=" + date_delete +
                ", lastAccess=" + lastAccess +
                ", description=[" + description + ']' +
                '}';
    }
}
