package com.driot.bookplayer.imports;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.driot.bookplayer.R;
import com.driot.bookplayer.objects.AppViewModelStoreOwner;
import com.driot.bookplayer.utils.log.LoggingFragment;

public class OngoingTaskFragment extends LoggingFragment {

    private static final String ARG_ONCLICK_INTENT = "onClickIntent";

    private static final int SUCCESS_AUTO_HIDE_MS = 2_000;
    private static final int WARNING_AUTO_HIDE_MS  = 15_000;
    private static final int ERROR_AUTO_HIDE_MS  = 1*60*60_000;

    @Nullable private Long hideDeadlineUptimeMs = null;
    @Nullable private Runnable hideRunnable = null;
    private final Handler mainH = new Handler(android.os.Looper.getMainLooper());
    private enum HideMode { NONE, SUCCESS, WARNING, ERROR }
    private HideMode currentMode = HideMode.NONE;
    private boolean didAutoHide = false;

    private TextView tvProgressText;
    private ProgressBar progressBar;
    private TextView tvTitle;

    public static OngoingTaskFragment newInstance(@Nullable Intent onClickIntent) {
        OngoingTaskFragment f = new OngoingTaskFragment();
        Bundle b = new Bundle();
        b.putParcelable(ARG_ONCLICK_INTENT, onClickIntent);
        f.setArguments(b);
        return f;
    }

    public OngoingTaskFragment() {}

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_ongoing_task, container, false);
        tvTitle = v.findViewById(R.id.tvOngoingTitle);
        tvProgressText = v.findViewById(R.id.tvOngoingProgress);
        progressBar = v.findViewById(R.id.pbOngoing);
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        OngoingTaskViewModel vm = new ViewModelProvider(
                AppViewModelStoreOwner.getInstance(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().getApplication())
        ).get(OngoingTaskViewModel.class);

        View root = view; // control visibility on the whole fragment

        vm.getUi().observe(getViewLifecycleOwner(), ui -> {
            // visibility
            root.setVisibility(ui.showToUser ? View.VISIBLE : View.GONE);

            // text
            tvTitle.setText(ui.title.isEmpty() ? getString(R.string.Import_in_progress) : ui.title);
            tvProgressText.setText(ui.progressText.isEmpty() ? "---" : ui.progressText);
            progressBar.setProgress(ui.progressPercent);

            boolean hasWarnings = ui.warningText != null && !ui.warningText.trim().isEmpty();
            boolean finishedSuccess = ui.result == TaskUiState.Result.SUCCEEDED;
            boolean finishedFailed  = ui.result == TaskUiState.Result.FAILED;
            if (ui.showToUser) {
                tvProgressText.setTextColor(
                        com.google.android.material.color.MaterialColors.getColor(tvProgressText,
                                com.google.android.material.R.attr.colorOnSurfaceVariant)
                );
                if (finishedSuccess) {
                    if (hasWarnings) {
                        tvProgressText.setTextColor(
                                ContextCompat.getColor(requireContext(), R.color.orange)
                        );
                        startOrUpdateHideTimer(HideMode.WARNING, WARNING_AUTO_HIDE_MS);
                    } else {
                        tvProgressText.setTextColor(
                                ContextCompat.getColor(requireContext(), R.color.green_500)
                        );
                        startOrUpdateHideTimer(HideMode.SUCCESS, SUCCESS_AUTO_HIDE_MS);
                    }
                } else if (finishedFailed) {
                    tvProgressText.setTextColor(
                            ContextCompat.getColor(requireContext(), R.color.red)
                    );
                    //startOrUpdateHideTimer(HideMode.ERROR, ERROR_AUTO_HIDE_MS);
                }
            } else {
                cancelHideTimer();
            }
        });

        // Injected navigation --> opens AddResourceActivity
        View container = view.findViewById(R.id.ongoing_task_container);
        Intent onClick = getArguments() != null ? getArguments().getParcelable(ARG_ONCLICK_INTENT) : null;
        if (container != null && onClick != null) {
            container.setOnClickListener(v -> {
                myLogI("--- user CLICKS ON-GOING BANNER ---");
                onClick.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(onClick);
            });
        } else if (container != null) {
            container.setOnClickListener(null);
        }
    }

    private void requestMainScrollToTopIfHostedByMain() {
        if (!isAdded()) return;
        if (!(requireActivity() instanceof com.driot.bookplayer.activities.MainActivity)) return;
        try {
            com.driot.bookplayer.activities.MainViewModel vm =
                    new androidx.lifecycle.ViewModelProvider(requireActivity())
                            .get(com.driot.bookplayer.activities.MainViewModel.class);
            vm.requestScrollToTopNow(); // one-shot event you already expose
            myLog("Requested Main scrollToTop");
        } catch (Throwable t) {
            myLogEE(t, "requestMainScrollToTopIfHostedByMain");
        }
    }

    private void startOrUpdateHideTimer(@NonNull HideMode mode, int totalMs) {
        long now = android.os.SystemClock.uptimeMillis();

        if (currentMode != mode || hideDeadlineUptimeMs == null) {
            currentMode = mode;
            hideDeadlineUptimeMs = now + totalMs;          // set deadline
        }

        long remaining = Math.max(0, hideDeadlineUptimeMs - now);
        rescheduleHide(remaining, mode);
    }

    private void rescheduleHide(long remainingMs, @NonNull HideMode mode) {
        if (hideRunnable != null) mainH.removeCallbacks(hideRunnable);
        hideRunnable = () -> {
            if (!isAdded() || didAutoHide) return;
            didAutoHide = true;

            if (mode == HideMode.SUCCESS) {
                requestMainScrollToTopIfHostedByMain();
            }
            ImportHelper.setShowToUser(requireContext(), false);
            currentMode = HideMode.NONE;
            hideDeadlineUptimeMs = null;
        };
        mainH.postDelayed(hideRunnable, remainingMs);
        myLog("Auto-hide mode=" + mode + " remaining=" + remainingMs + "ms");
    }

    private void cancelHideTimer() {
        if (hideRunnable != null) mainH.removeCallbacks(hideRunnable);
        hideRunnable = null;
        hideDeadlineUptimeMs = null;
        if (currentMode != HideMode.NONE) {
            myLog("Auto-hide canceled (mode was " + currentMode + ")");
        }
        currentMode = HideMode.NONE;
        didAutoHide = false;
    }


}
