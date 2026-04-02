package com.driot.bookplayer.activities;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.ebooks.gutendex.GutendexApiService;
import com.driot.bookplayer.ebooks.gutendex.GutendexBook;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class EbookDetailViewModel extends LoggingAndroidViewModel {

    private final MutableLiveData<GutendexBook> bookData = new MutableLiveData<>();
    private boolean fetched = false;
    private Call<GutendexBook> pendingCall;

    public EbookDetailViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<GutendexBook> getBookData() {
        return bookData;
    }

    public void fetchBookIfNeeded(int gutendexId) {
        if (fetched) return;
        if (!NetworkHelper.isConnected(getApplication())) return;

        fetched = true;

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(this::myLog);
        logging.setLevel(Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL);
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(Var.GUTENDEX_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(Var.GUTENDEX_READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Option.getGutenbergBaseUrl())
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        pendingCall = retrofit.create(GutendexApiService.class).getBook(gutendexId);
        pendingCall.enqueue(new Callback<GutendexBook>() {
            @Override
            public void onResponse(Call<GutendexBook> call, Response<GutendexBook> response) {
                if (response.isSuccessful() && response.body() != null) {
                    bookData.postValue(response.body());
                } else {
                    myLogD("fetchBookIfNeeded: no valid response for id=" + gutendexId);
                    fetched = false; // allow retry on next open
                }
            }

            @Override
            public void onFailure(Call<GutendexBook> call, Throwable t) {
                if (!call.isCanceled()) {
                    myLogD("fetchBookIfNeeded: failed for id=" + gutendexId + " - " + t.getMessage());
                    fetched = false; // allow retry on next open
                }
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (pendingCall != null) pendingCall.cancel();
    }
}
