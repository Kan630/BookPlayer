package com.driot.bookplayer.helpers;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public class CallerHelper {

    public static String getCaller() {
        return getCaller(4);
    }

    public static void printFullStack() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (int i = 2; i < stack.length; i++) { // skip getStackTrace + this method
            StackTraceElement e = stack[i];
            myLog("  at " + e.getClassName() + "." + e.getMethodName()
                    + "(" + e.getFileName() + ":" + e.getLineNumber() + ")");
        }
    }



    public static String getCaller(int level) {
        try {
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            // 0=getStackTrace, 1=getCaller, 2=this method, 3=the real caller
            if (stack.length > 4) {
                StackTraceElement caller = stack[4];
                return caller.getClassName() + "." + caller.getMethodName() + " (line " + caller.getLineNumber() + ")";
            } else {
                return "unknown";
            }
        } catch (Exception e) {
            myLogEE(e, "getCaller error");
            return "error in getCaller()";
        }
    }
}
