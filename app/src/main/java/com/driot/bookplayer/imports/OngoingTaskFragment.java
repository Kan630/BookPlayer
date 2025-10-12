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
import com.google.android.material.color.MaterialColors;

public class OngoingTaskFragment extends LoggingFragment {

    private static final String ARG_ONCLICK_INTENT = "onClickIntent";

    private static final int SUCCESS_AUTO_HIDE_MS = 2_000;
    private boolean didAutoHide = false;
    @Nullable private Long successFirstSeenUptimeMs = null;
    private final Handler mainH = new Handler(android.os.Looper.getMainLooper());
    @Nullable private Runnable hideRunnable = null;

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

            // colors
            boolean hasError = (ui.errorText != null && !ui.errorText.isEmpty() && ui.result != TaskUiState.Result.CANCELLED);
            if (hasError) {
                tvProgressText.setText(ui.errorText);
                tvProgressText.setTextColor(
                                ContextCompat.getColor(requireContext(), R.color.red)
                );
            } else {
                tvProgressText.setTextColor(
                        com.google.android.material.color.MaterialColors.getColor(tvProgressText,
                                com.google.android.material.R.attr.colorOnSurfaceVariant)
                );
            }

            // success auto-hide timing (your debounce code can now use ui.result/ui.isFinished())
            if (ui.result == TaskUiState.Result.SUCCEEDED) {
                // start your countdown once; then ImportHelper.setShowToUser(false)
                hideRunnable = () -> {
                    myLog("Auto-hiding fragment after ~" + SUCCESS_AUTO_HIDE_MS + " ms since success first seen");
                    requestMainScrollToTopIfHostedByMain();
                    ImportHelper.setShowToUser(requireContext(), false);
                };
                mainH.postDelayed(hideRunnable, SUCCESS_AUTO_HIDE_MS);
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

    private void maybeScheduleHide() {
        myLog("maybeScheduleHide");
        if (didAutoHide) return;
        if (successFirstSeenUptimeMs == null) return;

        long elapsed = android.os.SystemClock.uptimeMillis() - successFirstSeenUptimeMs;
        long remaining = Math.max(0L, SUCCESS_AUTO_HIDE_MS - elapsed);

        if (hideRunnable != null) {
            mainH.removeCallbacks(hideRunnable);
        }
        hideRunnable = () -> {
            if (isAdded() && !didAutoHide) {
                didAutoHide = true;
                myLog("Auto-hiding fragment after ~" + SUCCESS_AUTO_HIDE_MS + " ms since success first seen");
                requestMainScrollToTopIfHostedByMain();
                ImportHelper.setShowToUser(requireContext(), false);
            }
        };
        mainH.postDelayed(hideRunnable, remaining);
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

}
