// src/androidTest/java/com/driot/bookplayer/testutil/LogSupport.java
package com.driot.bookplayer.testutil;

import android.util.Log;
import com.driot.bookplayer.utils.KanLogger;

public interface LogSupport {

    default String tag() {
        return getClass().getSimpleName();
    }

    default void myLog(String msg)    { KanLogger.myLog(tag(), msg); }
    default void myLogI(String msg)   { KanLogger.myLogI(tag(), msg); }
    default void myLogW(String msg)   { KanLogger.myLogW(tag(), msg); }
    default void myLogE(String msg)   { KanLogger.myLogE(tag(), msg); }
    default void myLogD(String msg)   { Log.d(tag(), msg); }
}
