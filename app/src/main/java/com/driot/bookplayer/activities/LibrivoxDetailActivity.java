package com.driot.bookplayer.activities;

import static com.driot.bookplayer.global.Pref.setLoadBookTaskState;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.objects.ItemMetadata;
import com.driot.bookplayer.objects.LibrivoxApi;
import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.services.AddResourceService;
import com.driot.bookplayer.utils.WorkFlow;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class LibrivoxDetailActivity extends LoggingActivity {

    private LibrivoxDetailViewModel viewModel;
    private LibrivoxApi api;

    private TextView titleView, idView, infoView;
    private ImageView coverView;
    private Button bGet;
    private TextView tvLinkArchive, tvLinkLibrivox, tvOtherInfo, tvDownloadLink;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_librivox_detail);

        titleView = findViewById(R.id.textDetailTitle);
        idView = findViewById(R.id.textDetailIdentifier);
        infoView = findViewById(R.id.textDetailInfo);
        coverView = findViewById(R.id.imageDetailCover);
        bGet = findViewById(R.id.bGet);
        tvLinkArchive = findViewById(R.id.tvLinkArchive);
        tvLinkLibrivox = findViewById(R.id.tvLinkLibrivox);
        tvDownloadLink = findViewById(R.id.tvDownloadLink);
        tvOtherInfo = findViewById(R.id.tvOtherInfo);
        tvOtherInfo.setVisibility(View.GONE); // for later
        bGet.setEnabled(false);

        // Init ViewModel
        viewModel = new ViewModelProvider(this).get(LibrivoxDetailViewModel.class);
        viewModel.identifier = getIntent().getStringExtra("identifier");
        viewModel.title = getIntent().getStringExtra("title");

        titleView.setText(viewModel.title);
        idView.setText("ID: " + viewModel.identifier);
        infoView.setText(getString(R.string.loading_details));

        // Setup Retrofit
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(this::myLog);
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(logging).build();

        api = new Retrofit.Builder()
                .baseUrl("https://archive.org/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(LibrivoxApi.class);

        // Observers
        viewModel.metadata.observe(this, this::showMetadata);
        viewModel.zipExists.observe(this, exists -> {
            if (exists != null) {
                if (exists) {
                    long size = viewModel.zipFileSizeBytes.getValue() != null ? viewModel.zipFileSizeBytes.getValue() : 0;
                    tvDownloadLink.setText("\n✅ " + getString(R.string.librivox_zip_mp3_available) + " (" + formatFileSize(size) + ")");
                    if (WorkFlow.isSomeWorkFlowRunning(this)) {
                        tvDownloadLink.append("\n❌ " + getString(R.string.librivox_wait_integration));
                    } else {
                        bGet.setEnabled(true);
                    }
                } else {
                    tvDownloadLink.setText("\n❌ " + getString(R.string.librivox_zip_mp3_not_found));
                }
            }
        });

        // Trigger both in parallel
        if (viewModel.metadata.getValue() == null) fetchMetadata();
        if (viewModel.zipExists.getValue() == null) checkDownloadFile();

        bGet.setOnClickListener(v -> {
            String downloadUrl = "https://archive.org/download/" + viewModel.identifier + "/" + viewModel.identifier + "_64kb_mp3.zip";
            startDownload(downloadUrl);
        });
    }

    private void fetchMetadata() {
        api.getItemMetadata(viewModel.identifier).enqueue(new Callback<ItemMetadata>() {
            @Override
            public void onResponse(Call<ItemMetadata> call, Response<ItemMetadata> response) {
                if (response.isSuccessful() && response.body() != null) {
                    viewModel.metadata.setValue(response.body());
                } else {
                    infoView.setText(getString(R.string.loading_detail_failed));
                }
            }

            @Override
            public void onFailure(Call<ItemMetadata> call, Throwable t) {
                infoView.setText(getString(R.string.loading_detail_failed) + ": " + t.getMessage());
            }
        });
    }

    private void showMetadata(ItemMetadata metadata) {
        StringBuilder sb = new StringBuilder();
        if (metadata.metadata != null) {
            sb.append(getString(R.string.Creator) + ": ").append(metadata.metadata.creator).append("\n");
            sb.append(getString(R.string.Available_since) + ": ").append(metadata.metadata.date).append("\n");
        }

        for (ItemMetadata.FileEntry file : metadata.files) {
            if (file.name.endsWith("_cover.jpg") || file.name.endsWith("cover.jpg") || file.name.endsWith(".jpg")) {
                String coverUrl = "https://archive.org/download/" + viewModel.identifier + "/" + file.name;
                Glide.with(this).load(coverUrl).into(coverView);
                break;
            }
        }

        Map<String, Integer> countMap = new HashMap<>();
        Map<String, Long> sizeMap = new HashMap<>();

        for (ItemMetadata.FileEntry file : metadata.files) {
            myLogD("file: " + file.name + " - format:[" + file.format + "] - " + getReadableSize(file.size));

            if (file.format != null && file.size != null) {
                String format = file.format.toLowerCase();
                String name = file.name.toLowerCase();

                String type = null;
                if (name.endsWith(".mp3")) type = "mp3";
                else if (name.endsWith(".m4b")) type = "m4b";

                if (type != null) {
                    String bitrate;
                    if (name.contains("32kb") || format.contains("32kb")) bitrate = "32";
                    else if (name.contains("64kb") || format.contains("46kb")) bitrate = "64";
                    else if (name.contains("128kb") || format.contains("128kb")) bitrate = "128";
                    else bitrate = "other";

                    String key = type + "_" + bitrate;

                    try {
                        long sizeBytes = Long.parseLong(file.size);

                        countMap.put(key, countMap.getOrDefault(key, 0) + 1);
                        sizeMap.put(key, sizeMap.getOrDefault(key, 0L) + sizeBytes);
                    } catch (Exception e) {
                        myLog("Invalid file size: " + file.size + " for file: " + file.name);
                    }
                }
            }
        }

// Summary Log
        String[] formats = {"mp3", "m4b"};
        String[] bitrates = {"32", "64", "128", "other"};

        for (String format : formats) {
            for (String bitrate : bitrates) {
                String key = format + "_" + bitrate;
                int count = countMap.getOrDefault(key, 0);
                long size = sizeMap.getOrDefault(key, 0L);
                if (count > 0) {
                    myLogI(format.toUpperCase() + " -- " + bitrate + "kbps: " + count + " file(s), " + getReadableSize(size));
                }
            }
        }



        infoView.setText(sb.toString());
        tvLinkArchive.setText("https://archive.org/details/" + viewModel.identifier);

        String librivoxUrl = findLibrivoxUrl(metadata);
        if (librivoxUrl != null) {
            tvLinkLibrivox.setText(librivoxUrl);
        } else {
            tvLinkLibrivox.setVisibility(View.GONE);
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

    private void checkDownloadFile() {
        new Thread(() -> {
            try {
                String url = "https://archive.org/download/" + viewModel.identifier + "/" + viewModel.identifier + "_64kb_mp3.zip";
                myLog("checking existence for [" + url + "]");

                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Range", "bytes=0-0");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)");
                conn.connect();

                int responseCode = conn.getResponseCode();
                String contentRange = conn.getHeaderField("Content-Range");
                conn.disconnect();

                long fileSize;
                if (contentRange != null && contentRange.contains("/")) {
                    fileSize = Long.parseLong(contentRange.split("/")[1]);
                } else {
                    fileSize = -1;
                }

                boolean exists = (responseCode == 206 && fileSize > 0);
                runOnUiThread(() -> {
                    viewModel.zipFileSizeBytes.setValue(fileSize);
                    viewModel.zipExists.setValue(exists);
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    infoView.append("\n⚠ " + getString(R.string.Error_checking_file) + ": " + e.getMessage());
                    myLogEE(e,"Error checking file: " + e.getMessage());
                    viewModel.zipExists.setValue(false);
                });
            }
        }).start();
    }

    private void startDownload(String url) {
        LoadBookTaskState state = new LoadBookTaskState();
        state.uri = Uri.parse(url);
        state.type = "File";
        state.title = viewModel.title;
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

    private String getReadableSize(String size) {
        try {
            long bytes = Long.parseLong(size);
            return getReadableSize(bytes);
        } catch (Exception e) {
            return "Unknown size";
        }
    }

    private String getReadableSize(long sizeBytes) {
        if (sizeBytes <= 0) return "0 B";
        if (sizeBytes < 1024) return sizeBytes + " B";
        if (sizeBytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", sizeBytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0));
    }


    private String formatFileSize(long sizeBytes) {
        if (sizeBytes < 1024) return sizeBytes + " B";
        int z = (63 - Long.numberOfLeadingZeros(sizeBytes)) / 10;
        return String.format(Locale.US, "%.1f %sB", (double) sizeBytes / (1L << (z * 10)), " KMGTPE".charAt(z));
    }
}
