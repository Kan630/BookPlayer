package com.driot.bookplayer.objects;

import java.util.List;

public class LibrivoxApiResponse {
    public ResponseData response;
    public static class ResponseData {
        public List<LibrivoxItem> docs;
    }
}