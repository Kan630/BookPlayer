package com.driot.bookplayer.activities;

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
import androidx.lifecycle.ViewModelProvider;

import com.driot.bookplayer.R;
import com.driot.bookplayer.objects.AppViewModelStoreOwner;
import com.driot.bookplayer.utils.log.LoggingFragment;

public class OngoingTaskFragment extends LoggingFragment {

    private static final String ARG_ONCLICK_INTENT = "onClickIntent";

    private static final int DELAY_END_WAIT_NO_ERROR = 1_000;

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
        vm.isTaskRunning().observe(getViewLifecycleOwner(), running -> {
            myLog("observe isTaskRunning = " + running);
                    if (Boolean.TRUE.equals(running)) {
                        root.setVisibility(View.VISIBLE);
                    } else {
                        //TODO add the timeout of isFinished
                        root.setVisibility(View.GONE);
                    }
                }
        );

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
}
