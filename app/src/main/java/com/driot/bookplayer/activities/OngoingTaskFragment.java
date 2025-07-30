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
import androidx.lifecycle.ViewModelProvider;

import com.driot.bookplayer.R;

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

        TaskViewModel viewModel = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);
        TaskViewModelBridge.bind(viewModel);
        viewModel.getTaskTitle().observe(getViewLifecycleOwner(), title -> tvTitle.setText(title));
        viewModel.getProgressText().observe(getViewLifecycleOwner(), text -> tvProgressText.setText(text));
        viewModel.getProgressPercent().observe(getViewLifecycleOwner(), percent -> progressBar.setProgress(percent));

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
    public void onDestroyView() {
        super.onDestroyView();
        TaskViewModelBridge.unbind();
    }
}
