package com.driot.bookplayer.activities;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.driot.bookplayer.objects.AppViewModelStoreOwner;

import java.lang.ref.WeakReference;

public class OngoingTaskViewModelBridge {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Object LOCK = new Object();
    private static WeakReference<OngoingTaskViewModel> sVmRef = new WeakReference<>(null);

    /** Must be called on main thread */
    private static OngoingTaskViewModel getOrCreateVmOnMain(Application app) {
        OngoingTaskViewModel cached = sVmRef.get();
        if (cached != null) return cached;

        ViewModelProvider.Factory factory =
                AndroidViewModel.class.isAssignableFrom(OngoingTaskViewModel.class)
                        ? ViewModelProvider.AndroidViewModelFactory.getInstance(app)
                        : new ViewModelProvider.NewInstanceFactory();

        OngoingTaskViewModel vm = new ViewModelProvider(
                AppViewModelStoreOwner.getInstance(),
                factory
        ).get(OngoingTaskViewModel.class);

        synchronized (LOCK) {
            sVmRef = new WeakReference<>(vm);
        }
        return vm;
    }

    /** Run on main, then invoke */
    private static void post(Context context, Invoker invoker) {
        final Application app = (Application) context.getApplicationContext();
        MAIN.post(() -> {
            OngoingTaskViewModel vm = getOrCreateVmOnMain(app);
            if (vm != null) invoker.call(vm);
        });
    }

    // ---- Public API (thread-safe): always posts to main before touching VM ----

    public static void tellStart(Context context) {
        post(context, OngoingTaskViewModel::tellStart);
    }

    public static void tellEnd(Context context) {
        post(context, OngoingTaskViewModel::tellEnd);
    }

    public static void tellCurrentOperation(Context context, String currentOperation) {
        post(context, vm -> vm.tellCurrentOperation(currentOperation));
    }

    public static void tellProgress(Context context, int percent, String text) {
        post(context, vm -> vm.tellProgress(text, percent));
    }

    public static void tellProgressText(Context context, String text) {
        post(context, vm -> vm.tellProgressText(text));
    }

    public static void updateProgressText(Context context, String text) {
        tellProgressText(context, text);
    }

    public static void tellPause(Context context) {
        post(context, OngoingTaskViewModel::tellPause);
    }

    public static void tellWarning(Context context, String text) {
        post(context, vm -> vm.tellWarning(text));
    }

    public static void tellError(Context context, String errorText) {
        post(context, vm -> vm.tellError(errorText));
    }

    public static void removePauseCapability(Context context) {
        post(context, OngoingTaskViewModel::removePauseCapability);
    }

    // Small functional interface to avoid repeated Runnable boilerplate
    private interface Invoker {
        void call(OngoingTaskViewModel vm);
    }
}
