package com.driot.bookplayer.librivox;

import androidx.annotation.Nullable;

//  MAPPER BETWEEN "Internet Archive Item" (archive.org)  AND  "Librivox book" (librivox.org)

public final class LibrivoxMapper {

    private LibrivoxMapper() {}

    @Nullable
    public static String extractArchiveIdentifier(@Nullable String urlIarchive) {
        if (urlIarchive == null || urlIarchive.isEmpty()) return null;
        int lastSlash = urlIarchive.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == urlIarchive.length() - 1) {
            return urlIarchive; // already looks like an identifier
        }
        return urlIarchive.substring(lastSlash + 1);
    }

    /** Build a minimal ArchiveItem from a LibrivoxBook if you need one. */
    public static ArchiveItem toArchiveItem(LibrivoxBook book) {
        ArchiveItem ai = new ArchiveItem();
        ai.identifier  = extractArchiveIdentifier(book.urlIarchive);
        ai.title       = book.title;

        // date / rating / reviews are not in the LibriVox API;
        // they'll be filled later when/if you hit archive.org
        ai.date        = null;
        ai.avg_rating  = 0f;
        ai.num_reviews = 0;

        // keep app-specific flags separate or copy if you want
        ai.is_favorite = book.is_favorite;
        ai.idFolder    = book.idFolder;

        return ai;
    }
}
