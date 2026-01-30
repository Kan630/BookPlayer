package com.driot.bookplayer.librivox;

import androidx.annotation.Keep;

import java.util.List;

@Keep
public class ArchiveApiResponse {
    public ResponseData response;
    @Keep
    public static class ResponseData {
        public List<ArchiveItem> docs;
        /** Total number of matching items (archive.org). -1 if not provided. */
        public long numFound = -1;
    }
}