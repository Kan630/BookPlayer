package com.driot.bookplayer.utils.log;

/**
 *  onCreate
 *  onStart
 *  onResume
 *
 *  //ecran noir        //ecran flip                //back button     launch another activity
 *  onPause             onPause                     onPause             on pause
 *  onStop              onStop                      onStop              on stop
 *  onSaveInstance      onSaveInstanceState         onDestroy
 *                      onDestroy
 *  onRestart           onCreate                    onCreate            onRestart
 *  onStart             onStart                     onStart             onStart
 *  onResume            onRestaureInstanceState     onResume            onResume
 *                      onResume
 *
 */

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import static com.driot.bookplayer.utils.log.KanLogger.LOG_LIFECYCLE_TRACE;

import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
//import com.driot.bookplayer.utils.log.KanLogger;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;


/**
 * This abstract class extends the Activity class and overrides
 * lifecycle callbacks for logging various lifecycle events.
 */
public abstract class LoggingActivity extends AppCompatActivity {

    private static final String LOG_TAG = "Lifecycle";
    protected final String TAG_FROM_BRACKET = "[" + getClass().getSimpleName() + "]: ";
    protected final String TAG_FROM = "." + getClass().getSimpleName();

    //protected final LoggerHelper logger = new LoggerHelper(getClass());


    /**
     * Hook method called when a new instance of Activity is created. One time
     * initialization code should go here e.g. UI layout, some class scope
     * variable initialization. if finish() is called from onCreate no other
     * lifecycle callbacks are called except for onDestroy().
     * 
     * @param savedInstanceState
     *            object that contains saved state information.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            setTheme(Option.getThemeColor());
            setTheme(Option.getThemeFontOverlay());
        } catch (Exception e) {
            myLifecycleLogEE(e, "Error setting theme : " + e.getMessage());
        }

        super.onCreate(savedInstanceState);

        String calledBy = CallerInspector.inferCaller(this, Intents.EXTRA_CALLER);

        if (savedInstanceState != null) {
            // The activity is being re-created. Use the
            // savedInstanceState bundle for initializations either
            // during onCreate or onRestoreInstanceState().
            if (LOG_LIFECYCLE_TRACE) myLifecycleLog(TAG_FROM_BRACKET + "onCreate(): activity re-created. - Called by [" + calledBy  + "]");

        } else {
            // Activity is being created anew. No prior saved
            // instance state information available in Bundle object.
            if (LOG_LIFECYCLE_TRACE) myLifecycleLog(TAG_FROM_BRACKET + "onCreate(): activity created anew. - Called by [" + calledBy  + "]");
        }

    }

    /**
     * Hook method called after onCreate() or after onRestart() (when the
     * activity is being restarted from stopped state). Should re-acquire
     * resources relinquished when activity was stopped (onStop()) or acquire
     * those resources for the first time after onCreate().
     */
    @Override
    protected void onStart() {
        // Always call super class for necessary
        // initialization/implementation.
        super.onStart();
        if (LOG_LIFECYCLE_TRACE) myLifecycleLog(TAG_FROM_BRACKET + "onStart() - the activity is about to become visible");
    }

    /**
     * Hook method called after onRestoreStateInstance(Bundle) only if there is
     * a prior saved instance state in Bundle object. onResume() is called
     * immediately after onStart(). onResume() is called when user resumes
     * activity from paused state (onPause()) User can begin interacting with
     * activity. Place to start animations, acquire exclusive resources, such as
     * the camera.
     */
    @Override
    protected void onResume() {
        // Always call super class for necessary
        // initialization/implementation and then log which lifecycle
        // hook method is being called.
        super.onResume();
        if (LOG_LIFECYCLE_TRACE) myLifecycleLog(TAG_FROM_BRACKET + "onResume() - the activity has become visible (it is now \"resumed\")");
    }

    /**
     * Hook method called when an Activity loses focus but is still visible in
     * background. May be followed by onStop() or onResume(). Delegate more CPU
     * intensive operation to onStop for seamless transition to next activity.
     * Save persistent state (onSaveInstanceState()) in case app is killed.
     * Often used to release exclusive resources.
     */
    @Override
    protected void onPause() {
        // Always call super class for necessary
        // initialization/implementation and then log which lifecycle
        // hook method is being called.
        super.onPause();
        if (LOG_LIFECYCLE_TRACE) myLifecycleLog(TAG_FROM_BRACKET + "onPause() - another activity is taking focus (this activity is about to be \"paused\")");
    }

    /**
     * Called when Activity is no longer visible. Release resources that may
     * cause memory leak. Save instance state (onSaveInstanceState()) in case
     * activity is killed.
     */
    @Override
    protected void onStop() {
        // Always call super class for necessary
        // initialization/implementation and then log which lifecycle
        // hook method is being called.
        super.onStop();
        if (LOG_LIFECYCLE_TRACE) myLifecycleLog(TAG_FROM_BRACKET + "onStop() - the activity is no longer visible (it is now \"stopped\")");
    }

    /**
     * Hook method called when user restarts a stopped activity. Is followed by
     * a call to onStart() and onResume().
     */
    @Override
    protected void onRestart() {
        // Always call super class for necessary
        // initialization/implementation and then log which lifecycle
        // hook method is being called.
        super.onRestart();
        if (LOG_LIFECYCLE_TRACE) myLifecycleLog(TAG_FROM_BRACKET + "onRestart() - the activity is about to be restarted()");
    }

    /**
     * Hook method that gives a final chance to release resources and stop
     * spawned threads. onDestroy() may not always be called-when system kills
     * hosting process
     */
    @Override
    protected void onDestroy() {
        // Always call super class for necessary
        // initialization/implementation and then log which lifecycle
        // hook method is being called.
        super.onDestroy();
        if (LOG_LIFECYCLE_TRACE) myLifecycleLog(TAG_FROM_BRACKET + "onDestroy() - the activity is about to be destroyed");
    }

    // Ajouts Tonio

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (LOG_LIFECYCLE_TRACE) myLifecycleLog(TAG_FROM_BRACKET + "onSaveInstanceState()");
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (LOG_LIFECYCLE_TRACE) myLifecycleLog(TAG_FROM_BRACKET + "onRestoreInstanceState()");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (LOG_LIFECYCLE_TRACE) myLifecycleLog(TAG_FROM_BRACKET + "onActivityResult() - request code " + requestCode);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (LOG_LIFECYCLE_TRACE) myLifecycleLog(TAG_FROM_BRACKET + "onNewIntent() + intent : " + intent.getAction());
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) { //Notamment le changement de Locale
        super.onConfigurationChanged(newConfig);
        if (LOG_LIFECYCLE_TRACE) myLifecycleLog(TAG_FROM_BRACKET + "onConfigurationChanged() newConfig=" + newConfig.toString());
    }


    /// ///////////////////////////////////////////////////////////////////
    ///            LOGGER                  (Extend Activity)
    /// ///////////////////////////////////////////////////////////////////

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

    protected void myToastW(String str) {
        KanLogger.myToastW(TAG_FROM, str);
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


    /// ///////////////////////////////////////////////////////////////////
    ///            LOGGER        (For this specific Helper Class)
    /// ///////////////////////////////////////////////////////////////////
/*
    private void myLifecycleLog(String str) { KanLogger.myLogD(LOG_TAG, str); }
    private void myLifecycleLogE(String str) { KanLogger.myLogE(LOG_TAG, str); }
    private void myLifecycleLogEE(Throwable t, String str) { KanLogger.myLogEE(t, LOG_TAG, str); }
    
 */
    private void myLifecycleLog(String str) { KanLogger.myLogD(LOG_TAG, str); }
    private void myLifecycleLogE(String str) { KanLogger.myLogE(LOG_TAG, str); }
    private void myLifecycleLogEE(Throwable t, String str) { KanLogger.myLogEE(t, LOG_TAG, str); }
    
}
