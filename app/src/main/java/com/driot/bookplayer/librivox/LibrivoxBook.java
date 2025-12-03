package com.driot.bookplayer.librivox;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class LibrivoxBook {

    /** LibriVox internal numeric id, e.g. 11591 */
    @SerializedName("id")
    public String id; // API gives strings; you can wrap with int getter if you want

    public String title;

    /** Optional if you don’t request it in &fields= */
    public String description;

    public String language;

    @SerializedName("copyright_year")
    public String copyrightYear;

    @SerializedName("num_sections")
    public String numSections;

    @SerializedName("url_text_source")
    public String urlTextSource;

    @SerializedName("url_rss")
    public String urlRss;

    @SerializedName("url_zip_file")
    public String urlZipFile;

    @SerializedName("url_project")
    public String urlProject;

    @SerializedName("url_librivox")
    public String urlLibrivox;

    @SerializedName("url_iarchive")
    public String urlIarchive;

    @SerializedName("url_other")
    public String urlOther;

    @SerializedName("totaltime")
    public String totalTime;

    @SerializedName("totaltimesecs")
    public int totalTimeSecs;

    /** Sub-objects (only present if you request them via &fields or extended=1) */
    public List<LibrivoxAuthor> authors;
    public List<LibrivoxGenre>  genres;
    public List<LibrivoxSection> sections;
    public List<LibrivoxAuthor> translators;

    // --- Optional: app-specific flags, same idea as ArchiveItem ---
    public boolean is_favorite;
    public Long idFolder;   // null if not imported

    public boolean isImported() {
        return idFolder != null && idFolder > 0;
    }
}
