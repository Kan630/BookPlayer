package com.driot.bookplayer.activities;

public class OngoingTaskViewModelBridge {
    private static OngoingTaskViewModel viewModel;

    public static void bind(OngoingTaskViewModel vm) {
        viewModel = vm;
    }

    public static void unbind() {
        viewModel = null;
    }

    public static void updateProgressFull(String text, int percent) {
        if (viewModel != null) {
            viewModel.updateProgressFull(text, percent);
        }
    }

    public static void updateProgressText(String text) {
        if (viewModel != null) {
            viewModel.updateProgressText(text);
        }
    }

    public static void updateTitle(String title) {
        if (viewModel != null) {
            viewModel.updateTitle(title);
        }
    }

    public static void endTask() {
        if (viewModel != null) {
            viewModel.endTask();
        }
    }
}
