package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.TaskUiManager;

public class OngoingTaskFragment extends Fragment {

    private TextView tvProgressText;
    private ProgressBar progressBar;
    private TextView tvTitle;

    public OngoingTaskFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_ongoing_task, container, false);
        tvTitle = v.findViewById(R.id.tvOngoingTitle);
        tvProgressText = v.findViewById(R.id.tvOngoingProgress);
        progressBar = v.findViewById(R.id.pbOngoing);

        updateUI(); // initial state
        TaskUiManager.getInstance().setUiCallback(this::updateUI);
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View container = view.findViewById(R.id.ongoing_task_container);
        if (container != null) {
            container.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), AddResourceActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Re-register the UI update callback each time fragment resumes
        TaskUiManager.getInstance().setUiCallback(this::updateUI);
        updateUI(); // Immediate refresh in case state changed while paused
    }

    @Override
    public void onPause() {
        super.onPause();
        // Prevent memory leaks
        TaskUiManager.getInstance().clearUiCallback();
    }

    private void updateUI() {
        if (tvTitle != null) tvTitle.setText(TaskUiManager.getInstance().getTaskTitle());
        if (tvProgressText != null) tvProgressText.setText(TaskUiManager.getInstance().getProgressText());
        if (progressBar != null) progressBar.setProgress(TaskUiManager.getInstance().getProgressPercent());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        TaskUiManager.getInstance().clearUiCallback();
    }
}
