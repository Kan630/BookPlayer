package com.driot.bookplayer.activities;


import static com.driot.bookplayer.global.Pref.setLoadBookTaskState;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.ItemMetadata;
import com.driot.bookplayer.db.LibrivoxApi;
import com.driot.bookplayer.db.LoadBookTaskState;
import com.driot.bookplayer.utils.AddResourceService;
import com.driot.bookplayer.utils.KanLogger;

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

    private TextView titleView, idView, infoView;  // infoView to show extra metadata
    private Button bGet;

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
        bGet = findViewById(R.id.bGet);

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
                    bGet.setEnabled(true);
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

        // Find 64kbps MP3 file info
        ItemMetadata.FileEntry targetFile = null;
        for (ItemMetadata.FileEntry file : metadata.files) {
            if (file.format != null && file.format.toLowerCase().contains("mp3") && file.name.contains("64kb")) {
                targetFile = file;
                break;
            }
        }

        if (targetFile != null) {
            sb.append("File: ").append(targetFile.name).append("\n");
            sb.append("Size: ").append(formatFileSize(targetFile.size)).append("\n");
            sb.append("Duration: ").append(formatDuration(targetFile.length)).append("\n");
        } else {
            sb.append("64kbps MP3 not found\n");
        }

        infoView.setText(sb.toString());
    }

    private String formatFileSize(long sizeBytes) {
        if (sizeBytes < 1024) return sizeBytes + " B";
        int z = (63 - Long.numberOfLeadingZeros(sizeBytes)) / 10;
        return String.format("%.1f %sB", (double) sizeBytes / (1L << (z * 10)), " KMGTPE".charAt(z));
    }

    private String formatDuration(String lengthSecondsStr) {
        try {
            double seconds = Double.parseDouble(lengthSecondsStr);
            int h = (int) (seconds / 3600);
            int m = (int) ((seconds % 3600) / 60);
            int s = (int) (seconds % 60);
            if (h > 0) {
                return String.format("%d:%02d:%02d", h, m, s);
            } else {
                return String.format("%02d:%02d", m, s);
            }
        } catch (Exception e) {
            return "Unknown";
        }
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
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogI(String str) { KanLogger.myLogI(this.getClass().getName(), str); }
    private void myToast(String str) { KanLogger.myToast(this.getClass().getName(), str); }
    private void myToastE(String str) { KanLogger.myToastE(this.getClass().getName(), str); }
}
