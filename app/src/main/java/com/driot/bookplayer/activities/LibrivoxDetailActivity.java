package com.driot.bookplayer.activities;


import static com.driot.bookplayer.global.Pref.setLoadBookTaskState;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.ItemMetadata;
import com.driot.bookplayer.db.LibrivoxApi;
import com.driot.bookplayer.db.LoadBookTaskState;
import com.driot.bookplayer.utils.AddResourceService;
import com.driot.bookplayer.utils.KanLogger;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

public class LibrivoxDetailActivity extends AppCompatActivity {

    private String identifier;
    private String title;

    private TextView titleView, idView, infoView;
    private ImageView coverView;
    private Button bGet;
    private TextView tvLinkArchive, tvLinkLibrivox;

    private LibrivoxApi api;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_librivox_detail);

        identifier = getIntent().getStringExtra("identifier");
        title = getIntent().getStringExtra("title");

        titleView = findViewById(R.id.textDetailTitle);
        idView = findViewById(R.id.textDetailIdentifier);
        infoView = findViewById(R.id.textDetailInfo);  // add this TextView in your layout
        coverView = findViewById(R.id.imageDetailCover);
        bGet = findViewById(R.id.bGet);
        tvLinkArchive = findViewById(R.id.tvLinkArchive);
        tvLinkLibrivox = findViewById(R.id.tvLinkLibrivox);

        titleView.setText(title);
        idView.setText("ID: " + identifier);
        infoView.setText("Loading details...");

        bGet.setEnabled(false); // disable until metadata loaded

        // Setup Retrofit with logging (optional)
        // Use your logging method
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(this::myLog);
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://archive.org/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        api = retrofit.create(LibrivoxApi.class);

        fetchMetadata();

        bGet.setOnClickListener(v -> {
            String downloadUrl = "https://archive.org/download/" + identifier + "/" + identifier + "_64kb_mp3.zip";
            startDownload(downloadUrl);
        });
    }

    private void fetchMetadata() {
        api.getItemMetadata(identifier).enqueue(new Callback<ItemMetadata>() {
            @Override
            public void onResponse(Call<ItemMetadata> call, Response<ItemMetadata> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ItemMetadata metadata = response.body();
                    showMetadata(metadata);
                    checkDownloadFile(identifier);
                } else {
                    infoView.setText("Failed to load details");
                }
            }

            @Override
            public void onFailure(Call<ItemMetadata> call, Throwable t) {
                infoView.setText("Error loading details: " + t.getMessage());
            }
        });
    }

    private void showMetadata(ItemMetadata metadata) {
        StringBuilder sb = new StringBuilder();
        if (metadata.metadata != null) {
            sb.append("Creator: ").append(metadata.metadata.creator).append("\n");
            sb.append("Date: ").append(metadata.metadata.date).append("\n");
        }

// Look for cover image file
        for (ItemMetadata.FileEntry file : metadata.files) {
            if (file.name.endsWith("_cover.jpg") || file.name.endsWith("cover.jpg") || file.name.endsWith(".jpg")) {
                String coverUrl = "https://archive.org/download/" + identifier + "/" + file.name;
                Glide.with(this).load(coverUrl).into(coverView);
                break;
            }
        }

        // Find 64kbps MP3 file info
        ItemMetadata.FileEntry targetFile = null;
        for (ItemMetadata.FileEntry file : metadata.files) {
            myLog("file: " + file.name + " - " + getReadableSizeInMB(file.size));
            if (file.format != null && file.format.toLowerCase().contains("mp3") && file.name.contains("64kb")) {
                targetFile = file;
                break;
            }
        }
/*
        if (targetFile != null) {
            sb.append("File: ").append(targetFile.name).append("\n");
            sb.append("Size: ").append(targetFile.getReadableSizeInMB()).append("\n");
            sb.append("Duration: ").append(targetFile.getReadableDuration()).append("\n");
        } else {
            sb.append("64kbps MP3 not found\n");
        }

 */

        infoView.setText(sb.toString());

        tvLinkArchive.setText("https://archive.org/details/" + identifier);

        String librivoxUrl = findLibrivoxUrl(metadata);
        if (librivoxUrl == null) {
            tvLinkLibrivox.setVisibility(View.GONE);
        } else {
            tvLinkLibrivox.setText(librivoxUrl);
        }


    }
    private String findLibrivoxUrl(ItemMetadata metadata) {
        if (metadata.metadata != null && metadata.metadata.identifier != null) {
            String id = metadata.metadata.identifier;
            if (id.startsWith("librivox-")) {
                return "https://librivox.org/" + id.replace("librivox-", "").replace('_', '-');
            }
        }
        return null;
    }

    private String getReadableSizeInMB(String size) {
        try {
            long bytes = Long.parseLong(size);
            double mb = bytes / (1024.0 * 1024.0);
            return String.format(Locale.US, "%.1f MB", mb);
        } catch (Exception e) {
            return "Unknown size";
        }
    }
    private String formatFileSize(long sizeBytes) {
        if (sizeBytes < 1024) return sizeBytes + " B";
        int z = (63 - Long.numberOfLeadingZeros(sizeBytes)) / 10;
        return String.format(Locale.US, "%.1f %sB", (double) sizeBytes / (1L << (z * 10)), " KMGTPE".charAt(z));
    }

    private void startDownload(String url) {
        // Your existing code here (unchanged)
        LoadBookTaskState state = new LoadBookTaskState();
        state.uri = Uri.parse(url);
        state.type = "File";
        state.title = title;
        state.split = false;
        state.copy = true;
        state.delete = false;

        setLoadBookTaskState(this, state);

        Intent intentService = new Intent(this, AddResourceService.class);
        intentService.putExtra("LoadBookTaskState", state);
        startService(intentService);

        Intent intentActivity = new Intent(this, AddResourceActivity.class);
        intentActivity.putExtra("LoadBookTaskState", state);
        startActivity(intentActivity);

        finish();
    }


    private void checkDownloadFile(String identifier) {
        //String url = "https://archive.org/download/" + identifier + "/" + identifier + "_64kb_mp3.zip";
        String url = "https://archive.org/compress/" + identifier + "/formats=64KBPS%20MP3&file=/" + identifier + ".zip";
        myLog("checking existence for [" + url + "]");

        new Thread(() -> {
            try {
                URL downloadUrl = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) downloadUrl.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Range", "bytes=0-0");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Chrome/114.0");
                conn.connect();

                int responseCode = conn.getResponseCode();
                String contentRange = conn.getHeaderField("Content-Range"); // e.g., "bytes 0-0/473887623"

                conn.disconnect();

                long fileSize = -1;
                if (contentRange != null && contentRange.contains("/")) {
                    String[] parts = contentRange.split("/");
                    fileSize = Long.parseLong(parts[1]);
                }

                myLog("Response Code = " + conn.getResponseCode());
                myLog("Headers: " + conn.getHeaderFields().toString());

                boolean exists = (responseCode == 206 && fileSize > 0);
                final long finalFileSize = fileSize;

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (exists) {
                        String sizeStr = formatFileSize(finalFileSize);
                        infoView.append("\n✅ ZIP-MP3 file available (" + sizeStr + ")");
                        bGet.setEnabled(true);
                    } else {
                        infoView.append("\n❌ ZIP-MP3 file not found.");
                        bGet.setEnabled(false);
                    }
                });

            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    infoView.append("\n⚠ Error checking file: " + e.getMessage());
                    myLogE("Error checking file: " + e.getMessage());
                    bGet.setEnabled(false);
                });
            }
        }).start();
    }



    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogI(String str) { KanLogger.myLogI(this.getClass().getName(), str); }
    private void myToast(String str) { KanLogger.myToast(this.getClass().getName(), str); }
    private void myToastE(String str) { KanLogger.myToastE(this.getClass().getName(), str); }
}
