package com.driot.bookplayer.activities;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.BookSource;
import com.driot.bookplayer.db.BookSourceDao;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.objects.LibrivoxItem;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class LibrivoxResultsViewModel extends LoggingAndroidViewModel {

    private final MutableLiveData<List<LibrivoxItem>> results = new MutableLiveData<>();
    private final MutableLiveData<Boolean> shouldFinish = new MutableLiveData<>(false);

    private LiveData<List<LibrivoxItem>> favoritesLive;

    private String lastQuery = null;
    private String lastLang = null;

    public LibrivoxResultsViewModel(@NonNull Application application) { super(application); }

    public LiveData<List<LibrivoxItem>> getResults() { return results; }
    public LiveData<Boolean> getShouldFinish() { return shouldFinish; }
    public void requestFinish() { shouldFinish.setValue(true); }
    public String getLastQuery() { return lastQuery; }
    public void setLastQuery(String lastQuery) { this.lastQuery = lastQuery; }
    public String getLastLang() { return lastLang; }
    public void setLastLang(String lastLang) { this.lastLang = lastLang; }

    public LiveData<List<LibrivoxItem>> getFavoriteLibrivoxsLive() {
        if (favoritesLive == null) {
            AppDatabase db = AppDatabase.getDatabase(getApplication());
            favoritesLive = db.bookSourceDao()
                    .getFavoriteLibrivoxItems(Var.REPO_TYPE_AUDIOBOOK, Var.REPO_NAME_LIBRIVOX); // lowercase per your convention
        }
        return favoritesLive;
    }

    public void enrichWithLocalState(List<LibrivoxItem> apiItems) {
        if (apiItems == null || apiItems.isEmpty()) { results.setValue(apiItems); return; }

        AppDatabase.databaseWriteExecutor.execute(() -> {
            BookSourceDao dao = AppDatabase.getDatabase(getApplication()).bookSourceDao();
            List<String> ids = new ArrayList<>(apiItems.size());
            for (LibrivoxItem it : apiItems) ids.add(it.identifier);

            List<BookSourceDao.RepoStateRow> rows = dao.getStateFor(Var.REPO_TYPE_AUDIOBOOK, Var.REPO_NAME_LIBRIVOX, ids);
            HashMap<String, BookSourceDao.RepoStateRow> map = new HashMap<>();
            for (BookSourceDao.RepoStateRow r : rows) map.put(r.repoId, r);

            for (LibrivoxItem it : apiItems) {
                BookSourceDao.RepoStateRow st = map.get(it.identifier);
                if (st != null) {
                    it.is_favorite = st.is_favorite;
                    it.idFolder   = st.idFolder;
                } else {
                    it.is_favorite = false;
                    it.idFolder   = null;
                }
            }
            results.postValue(apiItems);
        });
    }


    // --- Toggle favorite ---
    public void toggleFavorite(LibrivoxItem item) {
        if (item == null) return;
        boolean newFav = !item.is_favorite;
        AppDatabase.databaseWriteExecutor.execute(() -> {
            long now = System.currentTimeMillis();
            BookSourceDao dao = AppDatabase.getDatabase(getApplication()).bookSourceDao();

            int updated = dao.updateFavoriteFlag(Var.REPO_TYPE_AUDIOBOOK, Var.REPO_NAME_LIBRIVOX, item.identifier, newFav, now);
            if (updated == 0 && newFav) {
                // Create row if user stars something that has no BookSource entry yet
                String url = "https://archive.org/details/" + item.identifier;
                BookSource bs = new BookSource(
                        item.title != null ? item.title : "",
                        url,
                        Var.REPO_TYPE_AUDIOBOOK,
                        Var.REPO_NAME_LIBRIVOX,
                        item.identifier,
                        null
                );
                bs.is_favorite = true;
                bs.date_add = now;
                bs.date_maj = now;
                AppDatabase.getDatabase(getApplication()).bookSourceDao().upsert(bs);
            }

            // update current list for snappy UI
            List<LibrivoxItem> cur = results.getValue();
            if (cur != null) {
                for (LibrivoxItem it : cur) {
                    if (it.identifier.equals(item.identifier)) {
                        it.is_favorite = newFav;
                        break;
                    }
                }
                results.postValue(new ArrayList<>(cur));
            }
        });
    }
}
