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

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import static com.driot.bookplayer.utils.log.KanLogger.LOG_LIFECYCLE_TRACE;

import com.driot.bookplayer.activities.MainActivity;
import com.driot.bookplayer.fragments.LiveLogFragment;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.helpers.LocaleHelper;
import com.driot.bookplayer.nav.BaseBottomNavActivity;


/**
 * This abstract class extends the Activity class and overrides
 * lifecycle callbacks for logging various lifecycle events.
 */
public abstract class BaseActivity extends AppCompatActivity {

    private static final String LOG_TAG = "Lifecycle";

    protected final String TAG_FROM_BRACKET = "[" + getClass().getSimpleName() + "]: ";
    protected final String TAG_FROM = "." + getClass().getSimpleName();

    private LiveLogFragment liveLogFragment;
    private FrameLayout liveLogContainer;

    private OnBackPressedCallback backCallback = null;

    // WRAPPING OF THE CONTEXT - DO NOT DO IN MYAPP, or you will fix the context
    // forever, and then device system changes like darkmode are just ignored by the
    // app... here : any conf change trigger an activity recreation, so context will
    // be wrapped again
    // It's supposed to allow easier App Language change for old devices
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrapContextWithAppLocale(newBase));
    }

    /**
     * Override to declare the "section parent" of this activity.
     * - null (default): this activity IS the section root → back goes to MainActivity.
     * - non-null: back navigates to that activity (which must itself be IS_SECTION_ROOT).
     *
     * Example chain: RadioFavoritesActivity(parent=GetRadioActivity) → GetRadioActivity(root) → MainActivity
     */
    @Nullable
    protected Class<? extends BaseBottomNavActivity> getSectionParent() { return null; }

    protected boolean isSectionRoot() {
        return false; //true for each AppNavBar section first activity
    }

    protected int getNavSectionId() {
        return -1;
    }

    /**
     * Hook method called when a new instance of Activity is created. One time
     * initialization code should go here e.g. UI layout, some class scope
     * variable initialization. if finish() is called from onCreate no other
     * lifecycle callbacks are called except for onDestroy().
     * 
     * @param savedInstanceState
     *                           object that contains saved state information.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            setTheme(Option.getThemeColor());
            setTheme(Option.getThemeFontOverlay());
        } catch (Exception e) {
            myLifecycleLogEE(e, "Error setting theme : " + e.getMessage());
        }

        if (Option.getAppOrientationLock()) {
            String mode = Option.getAppOrientationMode();
            if ("PORTRAIT".equals(mode)) {
                setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            } else {
                setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
        }

        registerBackCallback();

        super.onCreate(savedInstanceState);

        String calledBy = CallerInspector.inferCaller(this, Intents.EXTRA_CALLER);

        if (savedInstanceState != null) {
            // The activity is being re-created. Use the
            // savedInstanceState bundle for initializations either
            // during onCreate or onRestoreInstanceState().
            if (LOG_LIFECYCLE_TRACE)
                myLifecycleLog(TAG_FROM_BRACKET + "onCreate(): activity re-created. - Called by [" + calledBy + "]");

        } else {
            // Activity is being created anew. No prior saved
            // instance state information available in Bundle object.
            if (LOG_LIFECYCLE_TRACE)
                myLifecycleLog(TAG_FROM_BRACKET + "onCreate(): activity created anew. - Called by [" + calledBy + "]");
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
        if (LOG_LIFECYCLE_TRACE)
            myLifecycleLog(TAG_FROM_BRACKET + "onStart() - the activity is about to become visible");
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
        if (LOG_LIFECYCLE_TRACE)
            myLifecycleLog(TAG_FROM_BRACKET + "onResume() - the activity has become visible (it is now \"resumed\")");
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
        if (LOG_LIFECYCLE_TRACE)
            myLifecycleLog(TAG_FROM_BRACKET
                    + "onPause() - another activity is taking focus (this activity is about to be \"paused\")");
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
        if (LOG_LIFECYCLE_TRACE)
            myLifecycleLog(TAG_FROM_BRACKET + "onStop() - the activity is no longer visible (it is now \"stopped\")");
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
        if (LOG_LIFECYCLE_TRACE)
            myLifecycleLog(TAG_FROM_BRACKET + "onRestart() - the activity is about to be restarted()");
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
        if (LOG_LIFECYCLE_TRACE)
            myLifecycleLog(TAG_FROM_BRACKET + "onDestroy() - the activity is about to be destroyed");
    }

    // Ajouts Tonio

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (LOG_LIFECYCLE_TRACE)
            myLifecycleLog(TAG_FROM_BRACKET + "onSaveInstanceState()");
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (LOG_LIFECYCLE_TRACE)
            myLifecycleLog(TAG_FROM_BRACKET + "onRestoreInstanceState()");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (LOG_LIFECYCLE_TRACE)
            myLifecycleLog(TAG_FROM_BRACKET + "onActivityResult() - request code " + requestCode);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (LOG_LIFECYCLE_TRACE)
            myLifecycleLog(TAG_FROM_BRACKET + "onNewIntent() + intent : " + intent.getAction());
        setIntent(intent); // keep getIntent() up-to-date
        // Activity reused via REORDER_TO_FRONT: register callback if now acting as section root
        registerBackCallback();
    }

    // Pour le changement de Locale (Language) + NightMode ? Does not seem really
    // useful... since should be automatic, maybe do not temper !
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // if (LOG_LIFECYCLE_TRACE)
        myLifecycleLog(TAG_FROM_BRACKET + "onConfigurationChanged() newConfig=" + newConfig.toString());
    }

    /// ///////////////////////////////////////////////////////////////////
    /// LOGGER (Extend Activity)
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

    protected void myLogIntentExtras(Intent intent, String optionalTag) { KanLogger.myLogIntentExtras(TAG_FROM, intent, optionalTag); }

    private void myLifecycleLog(String str) {
        KanLogger.myLogD(LOG_TAG, str);
    }

    private void myLifecycleLogE(String str) {
        KanLogger.myLogE(LOG_TAG, str);
    }

    private void myLifecycleLogEE(Throwable t, String str) {
        KanLogger.myLogEE(t, LOG_TAG, str);
    }



    /// ///////////////////////////////////////////////////////////////////
    /// LIVE LOG FRAGMENT SUPPORT
    /// ///////////////////////////////////////////////////////////////////

    @Override
    public void setContentView(int layoutResID) {
        // Check if live logs are enabled
        boolean showLiveLogs = Pref.getShowLiveLogs();

        // Exclude LogTextActivity and LogListActivity from showing live logs
        // (redundant)
        String className = getClass().getSimpleName();
        if ("LogTextActivity".equals(className) || "LogListActivity".equals(className)) {
            showLiveLogs = false;
        }

        if (showLiveLogs) {
            // Get height percentage from preferences
            int heightPercentage = Pref.getLiveLogsSavedHeight();

            // Calculate weights: if fragment is 50%, content is 50%, so weights are equal
            // (1.0f each)
            // If fragment is 25%, content is 75%, so content weight = 3.0f, fragment weight
            // = 1.0f
            float fragmentWeight = heightPercentage / 100.0f;
            float contentWeight = 1.0f - fragmentWeight;

            // Create wrapper layout
            LinearLayout wrapper = new LinearLayout(this);
            wrapper.setOrientation(LinearLayout.VERTICAL);
            wrapper.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            // Inflate original content into a container
            View originalContent = getLayoutInflater().inflate(layoutResID, null);
            LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    contentWeight);
            wrapper.addView(originalContent, contentParams);

            // Add fragment container
            liveLogContainer = new FrameLayout(this);
            liveLogContainer.setId(View.generateViewId());
            LinearLayout.LayoutParams fragmentParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    fragmentWeight);
            wrapper.addView(liveLogContainer, fragmentParams);

            super.setContentView(wrapper);

            // Initialize fragment after container is created
            initializeLiveLogFragment();
        } else {
            super.setContentView(layoutResID);
        }
    }

    private void initializeLiveLogFragment() {
        if (liveLogContainer != null) {
            try {
                liveLogFragment = LiveLogFragment.newInstance();
                getSupportFragmentManager().beginTransaction()
                        .replace(liveLogContainer.getId(), liveLogFragment, "live_log")
                        .commit();
                // myLogD("Live log fragment initialized");
            } catch (Exception e) {
                myLogEE(e, "initializeLiveLogFragment");
            }
        }
    }

    private void registerBackCallback() {
        if (backCallback != null) return; // already registered

        //Do we have a hard coded parent ?
        Class<? extends BaseBottomNavActivity> parent = getSectionParent();
        String parentName = (parent != null ? parent.getSimpleName() : "no");
        int navSectionId = getNavSectionId();
        myLogD("registerBackCallback() - isRoot=[" + isSectionRoot() + "] - parent=[" + parentName + "] - navSection=[" + navSectionId + "]");

        backCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (LOG_LIFECYCLE_TRACE)
                    myLifecycleLog(TAG_FROM_BRACKET + "onBackPressed() - user pressed BACK");
                if (parent != null) {
                    // Has a declared parent → navigate to that declared parent
                    myLogI("--- user press BACK --- with declared parent → " + parent.getSimpleName());
                    Intent intent = new Intent(BaseActivity.this, parent);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                } else if (isSectionRoot()) {
                    // Root section → back to MainActivity
                    myLogI("--- user press BACK --- from section root → MainActivity");
                    Intent intent = new Intent(BaseActivity.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                } else if (navSectionId > 0) {
                    myLogI("--- user press BACK --- navSection=");
                    //TODO here we need to go to the parent of that same section
                    // Intent intent = new Intent(BaseActivity.this, );
                    // intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    // startActivity(intent);
                } else {
                    myLogI("--- user press BACK ---");
                    setEnabled(false); // VERY IMPORTANT
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backCallback);
    }



}
