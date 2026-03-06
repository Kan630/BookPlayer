package com.driot.bookplayer.activities;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.utils.Event;
import com.driot.bookplayer.utils.NoContent;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;


public class MainViewModel extends LoggingAndroidViewModel {

    private final FolderRepository repo;

    // Exposed to the Activity
    private final MediatorLiveData<List<Folder>> folders = new MediatorLiveData<>();
    private final MutableLiveData<Event<NoContent>> scrollToTop = new MutableLiveData<>();

    // UI state we keep across config changes
    private final SavedStateHandle state;
    private static final String K_LAST_SCROLLED_FOLDER_ID = "last_scrolled_folder_id";

    private boolean pendingScrollToTop = false;
    private long pendingFolderIdForScroll = -1;

    public MainViewModel(@NonNull Application app, @NonNull SavedStateHandle handle) {
        super(app);
        this.state = handle;
        this.repo = new FolderRepository(app);

        // Defaults
        if (!state.contains(K_LAST_SCROLLED_FOLDER_ID)) state.set(K_LAST_SCROLLED_FOLDER_ID, -1);

        LiveData<List<Folder>> source = repo.observeAll();
        folders.addSource(source, rawList -> {
            if (rawList == null) {
                folders.setValue(null);
                return;
            }

            List<Folder> sorted = sortFolders(rawList);
            folders.setValue(sorted);

            if (pendingScrollToTop && !sorted.isEmpty()) {
                pendingScrollToTop = false;
                scrollToTop.setValue(new Event<>(NoContent.INSTANCE));
                state.set(K_LAST_SCROLLED_FOLDER_ID, pendingFolderIdForScroll);
            }
        });
    }

    private List<Folder> sortFolders(List<Folder> folders) {
        if (folders == null || folders.isEmpty()) {
            return folders;
        }

        List<Folder> list = new ArrayList<>(folders);

        String mode = Option.getSortMode();
        String dir  = Option.getSortDirection();
        boolean descending = "desc".equals(dir);

        Comparator<Folder> comparator;

        switch (mode) {
            case "alpha":
            case "alphabetical":
                comparator = Comparator.comparing(
                        f -> f.getName() != null ? f.getName().trim().toLowerCase(Locale.getDefault()) : ""
                );
                break;

            case "added":
            case "last_added":
                comparator = Comparator.comparingLong(
                        f -> f.date_added
                );
                break;

            case "last_played":
            default:
                comparator = Comparator.comparingLong(
                        f -> f.lLastAccess
                );
                break;
        }

        if (descending) {
            comparator = comparator.reversed();
        }

        list.sort(comparator);
        return list;
    }



    // region Public API for Activity

    public LiveData<List<Folder>> getFolders()            { return folders; }
    public LiveData<Event<NoContent>> getScrollToTopEvent() { return scrollToTop; }

    public void requestScrollToTopNow() {
        scrollToTop.setValue(new Event<>(NoContent.INSTANCE));
    }

    /** When playback changes, ask the grid to scroll the current folder to top (one-shot). */
    public void requestScrollToTopForFolder(long folderId) {
        Long last = state.get(K_LAST_SCROLLED_FOLDER_ID);
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

    public void forceRefresh() {
        List<Folder> current = folders.getValue();
        if (current != null) {
            folders.setValue(sortFolders(current));
        }
    }

    /** Call when returning from ModifyFolderActivity after a rename/edit so folder list LiveData re-emits. */
    public void notifyFolderChanged(long folderId) {
        AppDatabase.databaseWriteExecutor.execute(() -> repo.invalidateFolder(folderId));
    }

    /** Call when the folder list may have changed (e.g. folder deleted). */
    public void notifyFoldersListChanged() {
        AppDatabase.databaseWriteExecutor.execute(repo::invalidateFoldersList);
    }

}
