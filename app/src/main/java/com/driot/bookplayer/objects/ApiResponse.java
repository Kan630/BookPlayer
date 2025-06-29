package com.driot.bookplayer.objects;

import java.util.List;

public class ApiResponse {
    public ResponseData response;
    public static class ResponseData {
        public List<LibrivoxItem> docs;
    }
}