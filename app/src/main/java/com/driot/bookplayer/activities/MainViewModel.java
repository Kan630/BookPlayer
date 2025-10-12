package com.driot.bookplayer.activities;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.utils.Event;
import com.driot.bookplayer.utils.NoContent;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import java.util.List;


public class MainViewModel extends LoggingAndroidViewModel {

    private final FolderRepository repo;

    // Exposed to the Activity
    private final MediatorLiveData<List<Folder>> folders = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> showEmptyPrompt = new MutableLiveData<>(false);
    private final MutableLiveData<Event<NoContent>> scrollToTop = new MutableLiveData<>();

    // UI state we keep across config changes
    private final SavedStateHandle state;
    private static final String K_PROMPTED = "prompted_open_file";
    private static final String K_LAST_SCROLLED_FOLDER_ID = "last_scrolled_folder_id";

    private boolean pendingScrollToTop = false;
    private int pendingFolderIdForScroll = -1;

    public MainViewModel(@NonNull Application app, @NonNull SavedStateHandle handle) {
        super(app);
        this.state = handle;
        this.repo = new FolderRepository(app);

        // Defaults
        if (!state.contains(K_PROMPTED)) state.set(K_PROMPTED, false);
        if (!state.contains(K_LAST_SCROLLED_FOLDER_ID)) state.set(K_LAST_SCROLLED_FOLDER_ID, -1);

        LiveData<List<Folder>> source = repo.observeAll();
        folders.addSource(source, list -> {
            folders.setValue(list);
            boolean empty = (list == null || list.isEmpty());
            boolean alreadyPrompted = Boolean.TRUE.equals(state.get(K_PROMPTED));
            showEmptyPrompt.setValue(empty && !alreadyPrompted);

            // If we requested a scroll because playback changed, fire it once
            if (pendingScrollToTop && list != null && !list.isEmpty()) {
                pendingScrollToTop = false;
                scrollToTop.setValue(new Event<>(NoContent.INSTANCE));
                // mark that we scrolled to this folder once
                state.set(K_LAST_SCROLLED_FOLDER_ID, pendingFolderIdForScroll);
            }
        });
    }

    // region Public API for Activity

    public LiveData<List<Folder>> getFolders()            { return folders; }
    public LiveData<Boolean> getShowEmptyPrompt()         { return showEmptyPrompt; }
    public LiveData<Event<NoContent>> getScrollToTopEvent() { return scrollToTop; }

    /** Call once when you actually navigate to GetActivity so we don’t spam the prompt. */
    public void markEmptyPromptShown() { state.set(K_PROMPTED, true); }

    /** When playback changes, ask the grid to scroll the current folder to top (one-shot). */
    public void requestScrollToTopForFolder(int folderId) {
        Integer last = state.get(K_LAST_SCROLLED_FOLDER_ID);
        if (last != null && last == folderId) {
            // same folder still playing → no auto scroll
            pendingScrollToTop = false;
            return;
        }
        pendingFolderIdForScroll = folderId;
        pendingScrollToTop = true;

        // If data is already loaded, emit immediately; otherwise the observer above will emit.
        List<Folder> current = folders.getValue();
        if (current != null && !current.isEmpty()) {
            pendingScrollToTop = false;
            scrollToTop.setValue(new Event<>(NoContent.INSTANCE));
            state.set(K_LAST_SCROLLED_FOLDER_ID, folderId);
        }
    }

    /** Optional: reset the “last scrolled” marker (e.g., when leaving the screen). */
    public void resetLastScrolledFolder() {
        state.set(K_LAST_SCROLLED_FOLDER_ID, -1);
    }

    /** If you pass an intent extra like forceRefresh, you can expose this no-op. Room auto-updates anyway. */
    public void forceRefresh() {
        // No-op with Room; kept for symmetry/testing hooks.
        List<Folder> cur = folders.getValue();
        if (cur != null) folders.setValue(cur); // poke observers
    }

}
