package com.driot.bookplayer.activities;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 01/11/20
 */
    public interface IPlayService {
        public void addListener(IPlayServiceListener listener);
        public void removeListener(IPlayServiceListener listener);
    }