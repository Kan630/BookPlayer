package com.driot.bookplayer.utils.log;

import androidx.recyclerview.widget.RecyclerView;

public abstract class LoggingRVAdapter<VH extends RecyclerView.ViewHolder>
        extends RecyclerView.Adapter<VH> {

    protected final LoggerHelper logger;

    public LoggingRVAdapter() {
        this.logger = new LoggerHelper(getClass());
    }

    protected void myLog(String str) {
        logger.myLog(str);
    }

    protected void myLogD(String str) {
        logger.myLogD(str);
    }

    protected void myLogI(String str) {
        logger.myLogI(str);
    }

    protected void myLogW(String str) {
        logger.myLogW(str);
    }

    protected void myLogE(String str) {
        logger.myLogE(str);
    }

    protected void myLogEE(Throwable t, String str) {
        logger.myLogEE(t, str);
    }

    protected void myLogInFile(String str) {
        logger.myLogInFile(str);
    }

    protected void myToast(String str) {
        logger.myToast(str);
    }

    protected void myToastE(String str) {
        logger.myToastE(str);
    }

    protected void myToastEE(Throwable t, String str) {
        logger.myToastEE(t, str);
    }

    protected void myLongToast(String str) {
        logger.myToastLong(str);
    }


}
