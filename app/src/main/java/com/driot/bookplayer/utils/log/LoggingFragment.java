package com.driot.bookplayer.utils.log;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;

import static com.driot.bookplayer.utils.log.KanLogger.LOG_FRAGMENT_LIFECYCLE_TRACE;

public abstract class LoggingFragment extends Fragment {

    protected final String TAG_FROM_BRACKET = "[" + getClass().getSimpleName() + "]: ";
    protected final String TAG_FROM = "." + getClass().getSimpleName();

    public LoggingFragment() {
        myInsideLogD("Constructor");
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    // PUBLIC LOGGER HELPERS
    //////////////////////////////////////////////////////////////////////////////////////////

    protected void myLog(String str) {
        KanLogger.myLog(TAG_FROM, str);
    }

    protected void myLogD(String str) {
        KanLogger.myLogD(TAG_FROM, str);
    }

    protected void myLogI(String str) {
        KanLogger.myLogI(TAG_FROM, str);
    }

    protected void myLogW(String str) {
        KanLogger.myLogW(TAG_FROM, str);
    }

    protected void myLogE(String str) {
        KanLogger.myLogE(TAG_FROM, str);
    }

    protected void myLogEE(Throwable t, String str) {
        KanLogger.myLogEE(t, TAG_FROM, str);
    }

    protected void myLogInFile(String str) {
        KanLogger.myLogInFile(TAG_FROM, str);
    }

    protected void myToast(String str) {
        KanLogger.myToast(TAG_FROM, str);
    }

    protected void myToastE(String str) {
        KanLogger.myToastE(TAG_FROM, str);
    }

    protected void myToastEE(Throwable t, String str) {
        KanLogger.myToastEE(t, TAG_FROM, str);
    }

    protected void myLongToast(String str) {
        KanLogger.myToastLong(TAG_FROM, str);
    }

    protected void myKeyFirebase(String strKey, String strValue) {
        FirebaseAnalyticsHelper.setCustomKeyCrashlytics(strKey, strValue);
    }

    protected void myLogFirebase(String strLog) {
        FirebaseAnalyticsHelper.logCrashlytics(strLog);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    // LIFECYCLE LOGGING
    //////////////////////////////////////////////////////////////////////////////////////////

    private void myInsideLog(String str) {
        if (LOG_FRAGMENT_LIFECYCLE_TRACE)
            KanLogger.myLog("Lifecycle", TAG_FROM_BRACKET + str);
    }

    private void myInsideLogD(String str) {
        if (LOG_FRAGMENT_LIFECYCLE_TRACE)
            KanLogger.myLogD("Lifecycle", TAG_FROM_BRACKET + str);
    }

    private void myInsideLogDW(String str) {
        if (LOG_FRAGMENT_LIFECYCLE_TRACE)
            KanLogger.myLogW("Lifecycle", TAG_FROM_BRACKET + str);
    }

    private void myInsideLogDE(String str) {
        if (LOG_FRAGMENT_LIFECYCLE_TRACE)
            KanLogger.myLogE("Lifecycle", TAG_FROM_BRACKET + str);
    }

    private void myInsideLogDEE(Throwable t, String str) {
        if (LOG_FRAGMENT_LIFECYCLE_TRACE)
            KanLogger.myLogEE(t, "Lifecycle", TAG_FROM_BRACKET + str);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myInsideLogD("onCreate()");
    }

    @Override
    public void onViewCreated(@NonNull android.view.View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        myInsideLogD("onViewCreated()");
    }

    @Override
    public void onStart() {
        super.onStart();
        myInsideLogD("onStart()");
    }

    @Override
    public void onResume() {
        super.onResume();
        myInsideLogD("onResume()");
    }

    @Override
    public void onPause() {
        super.onPause();
        myInsideLogD("onPause()");
    }

    @Override
    public void onStop() {
        super.onStop();
        myInsideLogD("onStop()");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        myInsideLogD("onDestroyView()");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        myInsideLogD("onDestroy()");
    }
}
