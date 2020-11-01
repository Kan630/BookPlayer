package com.driot.bookplayer.activities;

import android.os.Binder;
import android.util.Log;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 01/11/20
 */

public class PlayServiceBinder extends Binder {

    private IPlayService service = null;

    public PlayServiceBinder(IPlayService service) {
        super();
        this.service = service;
        Log.d("toto" + this.getClass().getName(), "PlayServiceBinder");
    }

    public IPlayService getService(){
        Log.d("toto" + this.getClass().getName(), "getService");
        return service;
    }

}