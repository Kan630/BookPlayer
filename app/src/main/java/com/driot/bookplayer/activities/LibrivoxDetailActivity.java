package com.driot.bookplayer.activities;

import static com.driot.bookplayer.global.Pref.setLoadBookTaskState;
import static com.driot.bookplayer.global.Var.SOURCE_LOCATION_LIBRIVOX;
import static com.driot.bookplayer.helpers.StorageHelper.getUnzipFolder;
import static com.driot.bookplayer.utils.TextOptions.parseMaybeHtml;
import static com.driot.bookplayer.utils.Tonio.getReadableSize;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.signature.ObjectKey;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.objects.ItemMetadata;
import com.driot.bookplayer.objects.LibrivoxApi;
import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.objects.OngoingTaskHost;
import com.driot.bookplayer.objects.TaskStateManager;
import com.driot.bookplayer.services.BookLoadingWorkLauncher;
import com.driot.bookplayer.objects.WorkFlow;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class LibrivoxDetailActivity extends LoggingActivity {

    private static final double COVER_UPGRADE_THRESHOLD = 1.10;

    private LibrivoxDetailViewModel viewModel;
    private LibrivoxApi api;

    private TextView titleView, idView, infoView, synopsisView;
    private ImageView coverView;
    private Button bGet;
    private TextView tvLinkArchive, tvLinkLibrivox, tvOtherInfo, tvDownloadLink;

    // cover handling
    private long cachedPicSizeBytes;
    private String futureCoverPic; // path of the file we plan to use/show

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_librivox_detail);
        InsetHelper.apply(this);

        OngoingTaskHost.attach(
                this,
                R.id.topOverlayContainer,
                new Intent(this, AddResourceActivity.class) // tap => open details
        );

        titleView = findViewById(R.id.textDetailTitle);
        idView = findViewById(R.id.textDetailIdentifier);
        infoView = findViewById(R.id.textDetailInfo);
        synopsisView = findViewById(R.id.textDetailSynopsis);
        coverView = findViewById(R.id.imageDetailCover);
        bGet = findViewById(R.id.bGet);
        tvLinkArchive = findViewById(R.id.tvLinkArchive);
        tvLinkLibrivox = findViewById(R.id.tvLinkLibrivox);
        tvDownloadLink = findViewById(R.id.tvDownloadLink);
        tvOtherInfo = findViewById(R.id.tvOtherInfo);
        tvOtherInfo.setVisibility(View.GONE);

        // Init ViewModel
        viewModel = new ViewModelProvider(this).get(LibrivoxDetailViewModel.class);
        viewModel.identifier = getIntent().getStringExtra("identifier");
        viewModel.title = getIntent().getStringExtra("title");

        titleView.setText(viewModel.title);
        idView.setText("ID: " + viewModel.identifier);
        infoView.setText(getString(R.string.loading_details));

        // Load cached image if any
        File localImage = ImageHelper.getLibrivoxImageFile(this, viewModel.identifier);
        if (localImage.exists()) {
            cachedPicSizeBytes = localImage.length();
            futureCoverPic = localImage.getAbsolutePath();
            myLogD("local Image found: " + viewModel.identifier + " - " + getReadableSize(cachedPicSizeBytes));
            Glide.with(coverView.getContext()).load(localImage).into(coverView);
        } else {
            // Fallback low-res: archive.org/services/img
            new Thread(() -> {
                String fallbackUrl = "https://archive.org/services/img/" + viewModel.identifier;
                String localPath = ImageHelper.getOrDownloadLibrivoxImage(this, viewModel.identifier, fallbackUrl, false);
                if (localPath != null) {
                    futureCoverPic = localPath;
                    runOnUiThread(() ->
                            Glide.with(coverView.getContext())
                                    .load(new File(localPath))
                                    .placeholder(R.drawable.placeholder_cover)
                                    .into(coverView)
                    );
                }
            }).start();
        }

        // Retrofit
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(this::myLog);
        logging.setLevel(Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL);
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(logging).build();

        api = new Retrofit.Builder()
                .baseUrl("https://archive.org/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(LibrivoxApi.class);

        // Observers
        viewModel.metadata.observe(this, this::showMetadata);

        // Keep zipExists only for info display; do NOT link to enabling the button
        viewModel.zipExists.observe(this, exists -> {
            if (exists == null) return;
            if (exists) {
                long size = viewModel.zipFileSizeBytes.getValue() != null ? viewModel.zipFileSizeBytes.getValue() : 0;
                tvDownloadLink.setText("\n✅ " + getString(R.string.librivox_zip_mp3_available) + " (" + formatFileSize(size) + ")");
            } else {
                tvDownloadLink.setText("\n❌ " + getString(R.string.librivox_zip_mp3_not_found));
            }
            // Button state is controlled ONLY by workflow running state:
            updateGetButtonEnabled();
        });

        // Kick off both in parallel
        if (viewModel.metadata.getValue() == null) fetchMetadata();
        if (viewModel.zipExists.getValue() == null) checkDownloadFile();

        // Enable GET immediately unless a workflow is running
        updateGetButtonEnabled();

        bGet.setOnClickListener(v -> {
            // prevent double taps
            bGet.setEnabled(false);
            myLogI("------> USER CLICKS - GET -        LIBRIVOX BOOK");
            String downloadUrl = "https://archive.org/download/" + viewModel.identifier + "/" + viewModel.identifier + "_64kb_mp3.zip";
            checkThenDownload(downloadUrl);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateGetButtonEnabled();
    }

    private void updateGetButtonEnabled() {
        boolean running = WorkFlow.isSomeWorkFlowRunning(this);
        bGet.setEnabled(!running);
        if (running) {
            tvDownloadLink.append("\n❌ " + getString(R.string.librivox_wait_integration));
        }
    }

    // --- Networking ---

    private void fetchMetadata() {
        api.getItemMetadata(viewModel.identifier).enqueue(new Callback<ItemMetadata>() {
            @Override public void onResponse(Call<ItemMetadata> call, Response<ItemMetadata> response) {
                if (response.isSuccessful() && response.body() != null) {
                    viewModel.metadata.setValue(response.body());
                } else {
                    infoView.setText(getString(R.string.loading_detail_failed));
                }
            }

            @Override public void onFailure(Call<ItemMetadata> call, Throwable t) {
                infoView.setText(getString(R.string.loading_detail_failed) + ": " + t.getMessage());
            }
        });
    }

    /** Only populate a nicer high-res image + basic text; no more file/bitrate counting. */
    private void showMetadata(ItemMetadata metadata) {
        // Basic info
        StringBuilder sb = new StringBuilder();
        if (metadata.metadata != null) {
            if (metadata.metadata.creator != null) {
                sb.append(getString(R.string.Creator)).append(": ").append(metadata.metadata.creator).append("\n");
            }
            if (metadata.metadata.date != null) {
                sb.append(getString(R.string.Available_since)).append(": ").append(metadata.metadata.date).append("\n");
            }
        }
        infoView.setText(sb.toString());

        // Synopsis
        if (metadata.metadata != null && metadata.metadata.description != null) {
            synopsisView.setText(parseMaybeHtml(metadata.metadata.description.trim()));
            synopsisView.setVisibility(View.VISIBLE);
        } else {
            synopsisView.setVisibility(View.GONE);
        }

        // Links
        tvLinkArchive.setText("https://archive.org/details/" + viewModel.identifier);
        String librivoxUrl = findLibrivoxUrl(metadata);
        if (librivoxUrl != null) {
            tvLinkLibrivox.setText(librivoxUrl);
            tvLinkLibrivox.setVisibility(View.VISIBLE);
        } else {
            tvLinkLibrivox.setVisibility(View.GONE);
        }

        // --- Pick the best (largest) cover from files and upgrade if needed ---
        tryUpgradeCoverIfPossible(metadata);
    }

    /** Pick ONLY filenames that clearly look like a cover and upgrade if meaningfully larger. */
    private void tryUpgradeCoverIfPossible(ItemMetadata metadata) {
        if (metadata == null || metadata.files == null || metadata.files.isEmpty()) return;

        String bestName = null;
        long bestSize = -1;

        for (ItemMetadata.FileEntry file : metadata.files) {
            if (file == null || file.name == null || file.size == null) continue;

            if (!isLikelyCover(file.name, viewModel.identifier)) continue;

            long sizeBytes = parseSizeSafe(file.size);
            if (sizeBytes <= 0) continue;

            // choose the largest clear "cover" candidate
            if (sizeBytes > bestSize) {
                bestSize = sizeBytes;
                bestName = file.name;
            }
        }

        if (bestName == null) {
            // no valid cover-like file found; keep whatever is shown already (services/img fallback)
            return;
        }

        // Only upgrade if the candidate is meaningfully larger than our cached image
        boolean shouldUpgrade = cachedPicSizeBytes <= 0 || bestSize > (long) (cachedPicSizeBytes * COVER_UPGRADE_THRESHOLD);
        if (!shouldUpgrade) return;

        final String betterUrl = "https://archive.org/download/" + viewModel.identifier + "/" + bestName;
        final long bestSizeSnapshot = bestSize;

        new Thread(() -> {
            File improvedFile = ImageHelper.getLibrivoxImageFile(LibrivoxDetailActivity.this, viewModel.identifier);
            String localPath = ImageHelper.getOrDownloadLibrivoxImage(
                    LibrivoxDetailActivity.this, viewModel.identifier, betterUrl, true);

            if (localPath != null) {
                runOnUiThread(() -> {
                    try {
                        // keep all state mutations on UI thread
                        futureCoverPic = localPath;
                        cachedPicSizeBytes = improvedFile.exists() ? improvedFile.length() : bestSizeSnapshot;

                        Glide.with(LibrivoxDetailActivity.this)
                                .load(new File(localPath))
                                .signature(new ObjectKey(System.currentTimeMillis()))
                                .placeholder(R.drawable.placeholder_cover)
                                .into(coverView);

                        myLog("Glided better image: " + improvedFile.getName()
                                + " - " + getReadableSize(cachedPicSizeBytes));
                    } catch (Exception e) {
                        myLogEE(e, "Error loading better image");
                    }
                });
            }
        }).start();
    }

    private long parseSizeSafe(String size) {
        try {
            if (size == null) return -1;
            return Long.parseLong(size);
        } catch (Exception ignored) {
            return -1;
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

    /** Keep the light HEAD/Range check for info; no longer used to enable the button. */
    private void checkDownloadFile() {
        new Thread(() -> {
            boolean[] finalResult = {false};
            long[] finalSize = {0};

            Runnable check = () -> {
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

                    long fileSize = -1;
                    if (contentRange != null && contentRange.contains("/")) {
                        fileSize = Long.parseLong(contentRange.split("/")[1]);
                    }

                    finalResult[0] = (responseCode == 206 && fileSize > 0);
                    finalSize[0] = fileSize;

                } catch (Exception e) {
                    myLogEE(e, "Error checking file");
                    finalResult[0] = false;
                }
            };

            check.run();

            if (!finalResult[0]) {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                myLogW("Retrying file existence check...");
                check.run();
            }

            runOnUiThread(() -> {
                viewModel.zipFileSizeBytes.setValue(finalSize[0]);
                viewModel.zipExists.setValue(finalResult[0]);
            });
        }).start();
    }

    private void checkThenDownload(String url) {
        String futurePath = getUnzipFolder(this).getAbsolutePath() + "/" + viewModel.identifier;
        AppDatabase.databaseReadExecutor.execute(() -> {
            if (AppDatabase.getDatabase(this).FolderDao().folderAlreadyExist_checkFolderPath(futurePath) > 0) {
                runOnUiThread(() -> myToast(getString(R.string.error_media_already_loaded_samePath)));
            } else {
                NetworkHelper.logCurrentNetworkState(this);
                runOnUiThread(() -> {
                    if (Option.getNetworkPolicyManualDownload().equals(NetworkHelper.NetworkPolicyManual.NETWORK_POLICY_UNMETERED)
                            && !NetworkHelper.isUnmeteredConnected(this)) {
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.download_warning_title_unmetered)
                                .setMessage(R.string.download_warning_message_unmetered)
                                .setPositiveButton(android.R.string.ok, (dialog, which) -> proceedWithDownload(url, futurePath))
                                .setNegativeButton(android.R.string.cancel, (dialog, which) -> myLogD("User cancelled download (Network state popup)"))
                                .show();
                    } else {
                        proceedWithDownload(url, futurePath);
                    }
                });
            }
        });
    }

    private void proceedWithDownload(String url, String futurePath) {
        LoadBookTaskState state = new LoadBookTaskState();
        state.originalUri = Uri.parse(url);
        state.dynamicUri = Uri.parse(url);
        state.originalType = "ZIP";
        state.dynamicType = "ZIP";
        state.fileExtension = "zip";
        state.title = viewModel.title;
        state.optionSplit = false;
        state.optionCopy = true;
        state.optionDelete = false;
        state.imagePath = futureCoverPic;
        state.sourceLocation = SOURCE_LOCATION_LIBRIVOX;
        state.futureFolderName = viewModel.identifier;
        state.futureFolderPath = futurePath;
        state.onGoingLoading = true;
        state.progressText = getString(R.string.About_to_start_download);

        setLoadBookTaskState(state);

        TaskStateManager.tellStart();
        BookLoadingWorkLauncher.launch(this);
        FirebaseAnalyticsHelper.tellLibrivoxDownload(state.title);
        startActivity(new Intent(this, AddResourceActivity.class));
        finish();
    }

    private String formatFileSize(long sizeBytes) {
        if (sizeBytes < 1024) return sizeBytes + " B";
        int z = (63 - Long.numberOfLeadingZeros(sizeBytes)) / 10;
        return String.format(Locale.US, "%.1f %sB", (double) sizeBytes / (1L << (z * 10)), " KMGTPE".charAt(z));
    }
    /** Strict cover detector: allow only obvious "cover" filenames. */
    private boolean isLikelyCover(String name, String identifier) {
        String n = name.toLowerCase(Locale.US);
        if (!(n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png"))) return false;

        // Most common archive.org patterns
        String id = identifier != null ? identifier.toLowerCase(Locale.US) : "";
        if (n.equals(id + "_cover.jpg") || n.equals(id + "_cover.jpeg")
                || n.equals("cover.jpg") || n.equals("cover.jpeg")) {
            return true;
        }

        // Accept common separators around "cover" to avoid matching "discover"
        int idx = n.indexOf("cover");
        if (idx >= 0) {
            boolean beforeOk = (idx == 0) || !Character.isLetterOrDigit(n.charAt(idx - 1));
            boolean afterOk  = (idx + 5 >= n.length()) || !Character.isLetterOrDigit(n.charAt(idx + 5));
            if (beforeOk && afterOk) return true; // e.g., "_cover.jpg", "-cover.png", "cover_large.jpg"
        }

        return false;
    }

}
