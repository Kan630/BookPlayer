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

    public static void tellWarning(String text) {
        if (viewModel != null) {
            viewModel.tellWarning(text);
        }
    }
    public static void tellProgress(int progress, String progressText) {
        if (viewModel != null) {
            viewModel.updateProgressFull(progressText, progress);
        }
    }

    public static void tellError(String errorText) {
        if (viewModel != null) {
            viewModel.tellError(errorText);
        }
    }

    public static void tellEnd() {
        if (viewModel != null) {
            viewModel.tellEnd();
        }
    }

    public static void updateTitle(String title) {
        if (viewModel != null) {
            viewModel.updateTitle(title);
        }
    }

    public static void removePauseCapability() {
        if (viewModel != null) {
            viewModel.removePauseCapability();
        }
    }

}
