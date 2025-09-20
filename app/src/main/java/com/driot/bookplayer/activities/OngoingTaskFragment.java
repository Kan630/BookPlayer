// com/driot/bookplayer/activities/OngoingTaskFragment.java
package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.driot.bookplayer.R;
import com.driot.bookplayer.objects.AppViewModelStoreOwner;
import com.driot.bookplayer.utils.log.LoggingFragment;

public class OngoingTaskFragment extends LoggingFragment {

    private static final String ARG_ONCLICK_INTENT = "onClickIntent";

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
        vm.isTaskRunning().observe(getViewLifecycleOwner(), running ->
                root.setVisibility(Boolean.TRUE.equals(running) ? View.VISIBLE : View.GONE));

        vm.getTaskTitle().observe(getViewLifecycleOwner(), title ->
                tvTitle.setText((title == null || title.isEmpty())
                        ? getString(R.string.Import_in_progress) : title));

        vm.getProgressText().observe(getViewLifecycleOwner(), text -> {
            if (text == null || text.isEmpty()) {
                //tvProgressText.setVisibility(View.GONE);
                tvProgressText.setText("---");
            } else {
                //tvProgressText.setVisibility(View.VISIBLE);
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
