package com.driot.bookplayer.testutil;

import android.util.Log;
import com.driot.bookplayer.utils.log.KanLogger;

public interface LogSupport {

    default String tag() {
        return getClass().getSimpleName();
    }

    default void myLog(String msg)    { KanLogger.myLog(tag(), msg); }
    default void myLogI(String msg)   { KanLogger.myLogI(tag(), msg); }
    default void myLogW(String msg)   { KanLogger.myLogW(tag(), msg); }
    default void myLogE(String msg)   { KanLogger.myLogE(tag(), msg); }
    default void myLogD(String msg)   { KanLogger.myLogD(tag(), msg); }
    //default void myLogD(String msg)   { Log.d("toto " + tag(), msg); }


    // For tests
    default void myLogD1(String msg)   { KanLogger.myLogD(tag(), msg); }
    default void myLogD2(String msg)   { Log.d(tag(), msg); }
    default void myLogD3(String msg)   { Log.d("toto", msg); }
    default void myLogD4(String msg)   { Log.d("toto " + tag(), msg); }
}
