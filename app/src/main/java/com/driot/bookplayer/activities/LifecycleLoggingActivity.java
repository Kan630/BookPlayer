package com.driot.bookplayer.activities;

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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import static com.driot.bookplayer.global.Var.LOG_LIFECYCLE_TRACE;
import static com.driot.tonylib.KanLogger.myLog;

import com.driot.tonylib.KanLogger;


/**
 * This abstract class extends the Activity class and overrides
 * lifecycle callbacks for logging various lifecycle events.
 */
public abstract class LifecycleLoggingActivity 
       extends AppCompatActivity { //Activity

    private static final String LOG_TAG = "LifecycleLoggingActivity"; //this.getClass().getName()
    protected final String TAG = getClass().getSimpleName();
    //String TAG = this.getClass().getName().substring(this.getClass().getName().lastIndexOf(".")+1);

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
        // Always call super class for necessary
        // initialization/implementation.
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            // The activity is being re-created. Use the
            // savedInstanceState bundle for initializations either
            // during onCreate or onRestoreInstanceState().
            if (LOG_LIFECYCLE_TRACE) myLog(TAG + "onCreate(): activity re-created");

        } else {
            // Activity is being created anew. No prior saved
            // instance state information available in Bundle object.
            if (LOG_LIFECYCLE_TRACE) myLog(TAG + "onCreate(): activity created anew");
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
        if (LOG_LIFECYCLE_TRACE) myLog(TAG + "onStart() - the activity is about to become visible");
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
        if (LOG_LIFECYCLE_TRACE) myLog(TAG + "onResume() - the activity has become visible (it is now \"resumed\")");
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
        if (LOG_LIFECYCLE_TRACE) myLog(TAG + "onPause() - another activity is taking focus (this activity is about to be \"paused\")");
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
        if (LOG_LIFECYCLE_TRACE) myLog(TAG + "onStop() - the activity is no longer visible (it is now \"stopped\")");
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
        if (LOG_LIFECYCLE_TRACE) myLog(TAG + "onRestart() - the activity is about to be restarted()");
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
        if (LOG_LIFECYCLE_TRACE) myLog(TAG + "onDestroy() - the activity is about to be destroyed");
    }

    // Ajouts Tonio

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (LOG_LIFECYCLE_TRACE) myLog(TAG + "onSaveInstanceState()");
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (LOG_LIFECYCLE_TRACE) myLog(TAG + "onRestoreInstanceState()");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (LOG_LIFECYCLE_TRACE) myLog(TAG + "onActivityResult() - request code " + requestCode);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (LOG_LIFECYCLE_TRACE) myLog(TAG + "onNewIntent() + intent : " + intent.getAction());
    }


    //private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLog(String str) { KanLogger.myLog(LOG_TAG, str); }
    private void myLogE(String str) { KanLogger.myLogE(LOG_TAG, str); }
}
