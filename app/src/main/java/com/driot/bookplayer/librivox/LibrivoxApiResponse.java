package com.driot.bookplayer.librivox;

import java.util.List;

public class LibrivoxApiResponse {
    public ResponseData response;
    public static class ResponseData {
        public List<ArchiveItem> docs;
    }
}