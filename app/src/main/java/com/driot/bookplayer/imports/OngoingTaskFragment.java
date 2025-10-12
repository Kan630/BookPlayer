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

    private static final int DELAY_END_WAIT_NO_ERROR = 1_000;

    private static final int SUCCESS_AUTO_HIDE_MS = 1000;
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
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        OngoingTaskViewModel vm = new ViewModelProvider(
                AppViewModelStoreOwner.getInstance(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().getApplication())
        ).get(OngoingTaskViewModel.class);

        View root = view; // control visibility on the whole fragment
        /*
        vm.isTaskRunning().observe(getViewLifecycleOwner(), running -> {
                    String errorText = vm.getErrorText().getValue();
                    boolean hasErrorText = errorText != null && !errorText.isEmpty();
                    myLog("observe isTaskRunning = " + running + (hasErrorText ? " - errorText=" + errorText : ""));
                    if (Boolean.TRUE.equals(running) || (hasErrorText && !errorText.equals("Cancelled"))) {
                        root.setVisibility(View.VISIBLE);
                    } else {
                        //TODO add the timeout of isFinished
                        root.setVisibility(View.GONE);
                    }
                }
        );

         */
        vm.getShowToUser().observe(getViewLifecycleOwner(), showToUser -> {
            myLog("observe showToUser = " + showToUser);
            if (Boolean.TRUE.equals(showToUser)) {
                root.setVisibility(View.VISIBLE);
            } else {
                root.setVisibility(View.GONE);
            }
        });

        vm.getErrorText().observe(getViewLifecycleOwner(), errorText -> {
            myLog("errorText = " + errorText);
                    if (errorText != null && !errorText.isEmpty() && !errorText.equals("Cancelled")) {
                        tvProgressText.setText(errorText);
                        tvProgressText.setTextColor(ContextCompat.getColor(requireContext(), R.color.red));
                    }
                });

        vm.isFinished().observe(getViewLifecycleOwner(), finished -> {
            myLog("observe isFinished = " + finished);
            if (!Boolean.TRUE.equals(finished)) {
                // reset gating flags if job flips back
                successFirstSeenUptimeMs = null;
                return;
            }
            // ignore errors (we don't auto-hide)
            String err = vm.getErrorText().getValue();
            boolean hasError = err != null && !err.isEmpty() && !"Cancelled".equals(err);
            if (hasError) return;
            // record the first time we saw success
            if (successFirstSeenUptimeMs == null) {
                myLog("hide countdown started");
                successFirstSeenUptimeMs = android.os.SystemClock.uptimeMillis();
            }
            maybeScheduleHide();
        });


/*
        vm.isFinished().observe(getViewLifecycleOwner(), success -> {
            myLog("observe isFinished = " + success);
                    if (Boolean.TRUE.equals(success)) {
                        root.setVisibility(View.VISIBLE);
                        myToast(getString(R.string.Import_Success) + "\n" + tvTitle.getText());
                        Handler delayedFinishHandler = new Handler();
                        Runnable delayedFinishRunnable = () -> {
                            root.setVisibility(View.GONE);
                        };
                        myLog("Let's wait some " + DELAY_END_WAIT_NO_ERROR + " ms. to display finish...");
                        delayedFinishHandler.postDelayed(delayedFinishRunnable, DELAY_END_WAIT_NO_ERROR);
                    }
                }
        );

 */

        vm.getTaskTitle().observe(getViewLifecycleOwner(), title ->
                tvTitle.setText((title == null || title.isEmpty())
                        ? getString(R.string.Import_in_progress) : title));

        vm.getProgressText().observe(getViewLifecycleOwner(), text -> {
            if (text == null || text.isEmpty()) {
                tvProgressText.setText("---");
            } else {
                tvProgressText.setText(text);
            }
        });

        vm.getProgressPercent().observe(getViewLifecycleOwner(), progressBar::setProgress);

        // Optional: show warnings/errors (toast, banner, etc.) — left for your UI choice.

        // Injected navigation
        View container = view.findViewById(R.id.ongoing_task_container);
        Intent onClick = getArguments() != null ? getArguments().getParcelable(ARG_ONCLICK_INTENT) : null;
        if (container != null && onClick != null) {
            container.setOnClickListener(v -> {
                onClick.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(onClick);
            });
        } else if (container != null) {
            container.setOnClickListener(null);
        }
    }

    private void maybeScheduleHide() {
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
                ImportHelper.setShowToUser(requireContext(), false);
            }
        };
        mainH.postDelayed(hideRunnable, remaining);
    }

}
