package com.driot.bookplayer.activities;

import android.app.Application;
import android.content.Context;

import androidx.lifecycle.ViewModelProvider;

import com.driot.bookplayer.objects.AppViewModelStoreOwner;


public class OngoingTaskViewModelBridge {

    private static OngoingTaskViewModel getViewModel(Context context) {
        Application app = (Application) context.getApplicationContext();

        return new ViewModelProvider(
                AppViewModelStoreOwner.getInstance(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        ).get(OngoingTaskViewModel.class);
    }

    public static void updateProgressFull(Context context, String text, int percent) {
        getViewModel(context).tellProgress(text, percent);
    }

    public static void updateProgressText(Context context, String text) {
        getViewModel(context).tellProgressText(text);
    }

    public static void tellWarning(Context context, String text) {
        getViewModel(context).tellWarning(text);
    }

    public static void tellProgress(Context context, int percent, String text) {
        getViewModel(context).tellProgress(text, percent);
    }

    public static void tellProgressText(Context context, String text) {
        getViewModel(context).tellProgressText(text);
    }

    public static void tellError(Context context, String errorText) {
        getViewModel(context).tellError(errorText);
    }

    public static void tellEnd(Context context) {
        getViewModel(context).tellEnd();
    }

    public static void tellStart(Context context) {
        getViewModel(context).tellStart();
    }
    public static void tellCurrentOperation(Context context, String currentOperation) {
        getViewModel(context).tellCurrentOperation(currentOperation);
    }

    public static void removePauseCapability(Context context) {
        getViewModel(context).removePauseCapability();
    }
}
