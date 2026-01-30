package com.driot.bookplayer.activities;

import static com.driot.bookplayer.global.Var.SOURCE_LOCATION_LIBRIVOX;
import static com.driot.bookplayer.helpers.StorageHelper.getUnzipFolder;
import static com.driot.bookplayer.utils.TextOptions.parseMaybeHtml;
import static com.driot.bookplayer.utils.Tonio.getReadableSize;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.BuildConfig;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.helpers.SupportedFilesHelper;
import com.driot.bookplayer.imports.ImportHelper;
import com.driot.bookplayer.librivox.ItemMetadata;
import com.driot.bookplayer.librivox.LibrivoxApi;
import com.driot.bookplayer.librivox.LanguageMapper;
import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.imports.BookLoadingWorkLauncher;
import com.driot.bookplayer.utils.Tonio;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;

import dagger.hilt.android.AndroidEntryPoint;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@AndroidEntryPoint
public class LibrivoxDetailActivity extends BaseBottomNavActivity {

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
    protected int getNavId() {
        return R.id.nav_add;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_librivox_detail;
    }

    @Override
    protected boolean enableOngoingTaskOverlay() {
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

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
        idView.setText(getString(com.driot.bookplayer.R.string.id_label) + viewModel.identifier);
        infoView.setText(getString(R.string.loading_details));

        // Load cached image if any
        File localImage = ImageHelper.getLibrivoxImageFile(this, viewModel.identifier);
        if (localImage.exists()) {
            cachedPicSizeBytes = localImage.length();
            futureCoverPic = localImage.getAbsolutePath();
            myLogD("local Image found : (" + getReadableSize(cachedPicSizeBytes) + ") " + localImage.getAbsolutePath());
            Glide.with(coverView.getContext()).load(localImage).into(coverView);
        } else {
            myLogD("no local Image found => check on internet");
            // Fallback low-res: archive.org/services/img
            new Thread(() -> {
                String fallbackUrl = "https://archive.org/services/img/" + viewModel.identifier;
                String localPath = ImageHelper.getOrDownloadLibrivoxImage(this, viewModel.identifier, fallbackUrl,
                        false);
                if (localPath != null) {
                    futureCoverPic = localPath;
                    runOnUiThread(() -> Glide.with(coverView.getContext())
                            .load(new File(localPath))
                            .placeholder(R.drawable.placeholder_cover)
                            .into(coverView));
                }
            }).start();
        }

        // Retrofit
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(this::myLog);
        logging.setLevel(Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL);
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(logging).build();

        api = new Retrofit.Builder()
                .baseUrl(BuildConfig.LIBRIVOX_PROXY_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(LibrivoxApi.class);

        // Observers
        viewModel.metadata.observe(this, this::showMetadata);

        // Observe existing folder
        viewModel.existingFolder.observe(this, folder -> {
            if (folder != null) {
                // Book already downloaded
                myLogI("Book already downloaded, showing open button");
                tvDownloadLink.setText("\n✅ " + getString(R.string.already_downloaded));
                bGet.setText(R.string.open_audiobook);
                bGet.setEnabled(true);

                // Don't check download link if already downloaded
            } else {
                // Book not downloaded yet - observe download link
                myLogI("Book not downloaded, setting up download observers");
                bGet.setText(R.string.Librivox_bGetAudioBook);
                setupDownloadLinkObserver();
                // Check download file availability
                if (viewModel.download_link.getValue() == null) {
                    checkDownloadFile();
                } else {
                    updateGetButtonEnabled();
                }
            }
        });

        // Kick off metadata fetch and existing folder check
        if (viewModel.metadata.getValue() == null)
            fetchMetadata();
        checkIfAlreadyDownloaded();

        bGet.setOnClickListener(v -> {
            bGet.setEnabled(false);

            Folder existingFolder = viewModel.existingFolder.getValue();
            if (existingFolder != null) {
                // Open existing book
                myLogI("------> USER CLICKS - OPEN - EXISTING LIBRIVOX BOOK");
                openExistingBook(existingFolder);
            } else {
                // Download new book
                myLogI("------> USER CLICKS - GET - NEW LIBRIVOX BOOK");
                String downloadUrl = viewModel.download_link.getValue();
                if (downloadUrl == null || downloadUrl.isEmpty()) {
                    downloadUrl = "https://archive.org/download/" + viewModel.identifier + "/" + viewModel.identifier
                            + "_64kb_mp3.zip";
                }
                checkThenDownload(downloadUrl);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-check if book exists (user might have deleted it)
        checkIfAlreadyDownloaded();
        // updateGetButtonEnabled will be called by the observer
    }

    private void checkIfAlreadyDownloaded() {
        String futurePath = getUnzipFolder(this).getAbsolutePath() + "/" + viewModel.identifier;

        AppDatabase.databaseReadExecutor.execute(() -> {
            Folder folder = AppDatabase.getDatabase(this).folderDao().getFolderByPath(futurePath);
            runOnUiThread(() -> viewModel.existingFolder.setValue(folder));
        });
    }

    private void setupDownloadLinkObserver() {
        viewModel.download_link.observe(this, download_link -> {
            if (download_link == null)
                return;

            if (!download_link.isEmpty()) {
                long size = viewModel.zipFileSizeBytes.getValue() != null ? viewModel.zipFileSizeBytes.getValue() : 0;
                tvDownloadLink.setText("\n✅ " + getString(R.string.librivox_zip_mp3_available) + " ("
                        + Tonio.getReadableSize(size) + ")");
            } else {
                tvDownloadLink.setText("\n❌ " + getString(R.string.librivox_zip_mp3_not_found));
            }
            updateGetButtonEnabled();
        });
    }

    private void updateGetButtonEnabled() {
        Folder existingFolder = viewModel.existingFolder.getValue();

        if (existingFolder != null) {
            // Always enable for opening existing book
            bGet.setEnabled(true);
            return;
        }

        // For new downloads, check if workflow is running
        AppDatabase.databaseReadExecutor.execute(() -> {
            boolean running = ImportHelper.isAnyImportActiveSync(this);
            runOnUiThread(() -> {
                bGet.setEnabled(!running && viewModel.download_link.getValue() != null
                        && !viewModel.download_link.getValue().isEmpty());
                if (running) {
                    tvDownloadLink
                            .setText(tvDownloadLink.getText() + "\n❌ " + getString(R.string.please_wait_another_book));
                }
            });
        });
    }

    private void openExistingBook(Folder folder) {
        Intent intent = new Intent(this, ZikFileActivity.class);
        intent.putExtra(Intents.EXTRA_FOLDER, folder);
        startActivity(intent);
        finish();
    }

    // --- Networking ---

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

    /**
     * Only populate a nicer high-res image + basic text; no more file/bitrate
     * counting.
     */
    private void showMetadata(ItemMetadata metadata) {
        // Basic info
        StringBuilder sb = new StringBuilder();
        if (metadata.metadata != null) {
            if (metadata.metadata.creator != null) {
                sb.append("\n").append(getString(R.string.Creator)).append(": ").append(metadata.metadata.creator);
            }
            if (metadata.metadata.date != null) {
                sb.append("\n").append(getString(R.string.Available_since)).append(": ").append(metadata.metadata.date);
            }
            String language = LanguageMapper.getNameFromThreeLetter(metadata.metadata.language);
            if (language != null) {
                sb.append("\n").append(getString(R.string.Language)).append(": ").append(language);
            }
            if (metadata.metadata.runtime != null) {
                sb.append("\n").append(getString(R.string.Duration)).append(": ").append(metadata.metadata.runtime);
            }
        }
        String text = sb.toString();
        infoView.setText(text.startsWith("\n") ? text.substring(1) : text);

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
            findViewById(R.id.ll_link_librivox).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.ll_link_librivox).setVisibility(View.GONE);
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
            boolean[] finalResult = { false };
            long[] finalSize = { 0 };
            String[] successfulUrl = { null };

            String id = viewModel.identifier;
            String classicTemplate = "https://archive.org/download/%s/%s_64kb_mp3.zip";
            String compressTemplate = "https://archive.org/compress/%s/formats=64KBPS%%20MP3&file=/%s.zip";

            String classicUrl = String.format(java.util.Locale.ROOT, classicTemplate, id, id);
            String compressUrl = String.format(java.util.Locale.ROOT, compressTemplate, id, id);

            class CheckResult {
                final boolean ok;
                final long size;
                final String url;

                CheckResult(boolean ok, long size, String url) {
                    this.ok = ok;
                    this.size = size;
                    this.url = url;
                }
            }

            java.util.function.Function<String, java.util.concurrent.Callable<CheckResult>> makeChecker = (
                    final String url) -> (java.util.concurrent.Callable<CheckResult>) () -> {
                        HttpURLConnection conn = null;
                        try {
                            myLog("checking existence for [" + url + "]");
                            conn = (HttpURLConnection) new URL(url).openConnection();
                            conn.setRequestMethod("GET");
                            conn.setRequestProperty("Range", "bytes=0-0");
                            conn.setRequestProperty("User-Agent", Var.USER_AGENT_BOOKPLAYER);
                            conn.setConnectTimeout(10000);
                            conn.setReadTimeout(15000);
                            conn.setInstanceFollowRedirects(true);
                            conn.connect();

                            int responseCode = conn.getResponseCode();
                            String contentRange = conn.getHeaderField("Content-Range");
                            String contentLength = conn.getHeaderField("Content-Length");

                            long fileSize = -1;
                            if (contentRange != null && contentRange.contains("/")) {
                                try {
                                    String total = contentRange.split("/")[1];
                                    fileSize = Long.parseLong(total);
                                } catch (Exception ignored) {
                                }
                            } else if (contentLength != null) {
                                try {
                                    long len = Long.parseLong(contentLength);
                                    if (len > 0)
                                        fileSize = len;
                                } catch (Exception ignored) {
                                }
                            }

                            boolean exists = false;
                            if (responseCode == HttpURLConnection.HTTP_PARTIAL && fileSize > 0) {
                                exists = true;
                            } else if (responseCode == HttpURLConnection.HTTP_OK) {
                                exists = true;
                            }

                            return new CheckResult(exists, fileSize, url);
                        } catch (Exception e) {
                            myLogEE(e, "Error checking file url: " + url);
                            return new CheckResult(false, -1L, url);
                        } finally {
                            if (conn != null)
                                conn.disconnect();
                        }
                    };

            for (int round = 1; round <= 2 && !finalResult[0]; round++) {
                java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
                java.util.concurrent.ExecutorCompletionService<CheckResult> ecs = new java.util.concurrent.ExecutorCompletionService<>(
                        executor);

                ecs.submit(makeChecker.apply(classicUrl));
                ecs.submit(makeChecker.apply(compressUrl));

                int remaining = 2;
                try {
                    while (remaining > 0 && !finalResult[0]) {
                        java.util.concurrent.Future<CheckResult> f;
                        try {
                            f = ecs.take();
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        remaining--;
                        try {
                            CheckResult cr = f.get();
                            if (cr.ok) {
                                finalResult[0] = true;
                                finalSize[0] = cr.size;
                                successfulUrl[0] = cr.url;
                                myLog("Found zip (round " + round + "): " + cr.url + " (size="
                                        + Tonio.getReadableSize(cr.size) + ")");
                                break;
                            } else {
                                myLog("Not found (round " + round + ") for url: " + cr.url);
                            }
                        } catch (Exception e) {
                            myLogEE(e, "Error while getting check result");
                        }
                    }
                } finally {
                    try {
                        executor.shutdownNow();
                        executor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    } catch (Exception ignored) {
                    }
                }
            }

            runOnUiThread(() -> {
                viewModel.zipFileSizeBytes.setValue(finalSize[0]);
                if (finalResult[0]) {
                    viewModel.download_link.setValue(successfulUrl[0]);
                    myLog("Successful zip URL: " + successfulUrl[0]);
                } else {
                    viewModel.download_link.setValue("");
                    myLogW("Zip not found after two parallel rounds for id: " + id);
                }
                updateGetButtonEnabled();
            });
        }).start();
    }

    private void checkThenDownload(String url) {
        String futurePath = getUnzipFolder(this).getAbsolutePath() + "/" + viewModel.identifier;
        AppDatabase.databaseReadExecutor.execute(() -> {
            if (AppDatabase.getDatabase(this).folderDao().folderAlreadyExist_checkFolderPath(futurePath) > 0) {
                runOnUiThread(() -> myToast(getString(R.string.error_media_already_loaded_samePath)));
            } else {
                NetworkHelper.logCurrentNetworkState(this);
                runOnUiThread(() -> {
                    if (Option.getNetworkPolicyManualDownload()
                            .equals(NetworkHelper.NetworkPolicyManual.NETWORK_POLICY_UNMETERED)
                            && !NetworkHelper.isUnmeteredConnected(this)) {
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.download_warning_title_unmetered)
                                .setMessage(R.string.download_warning_message_unmetered)
                                .setPositiveButton(android.R.string.ok,
                                        (dialog, which) -> proceedWithDownload(url, futurePath))
                                .setNegativeButton(android.R.string.cancel,
                                        (dialog, which) -> myLogD(
                                                "User cancelled download (Network state popup unmetered)"))
                                .show();
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        if (Option.getNetworkPolicyManualDownload()
                                .equals(NetworkHelper.NetworkPolicyManual.NETWORK_POLICY_NOT_ROAMING)
                                && !NetworkHelper.isRoaming(this)) {
                            new AlertDialog.Builder(this)
                                    .setTitle(R.string.download_warning_title_roaming)
                                    .setMessage(R.string.download_warning_message_roaming)
                                    .setPositiveButton(android.R.string.ok,
                                            (dialog, which) -> proceedWithDownload(url, futurePath))
                                    .setNegativeButton(android.R.string.cancel,
                                            (dialog, which) -> myLogD(
                                                    "User cancelled download (Network state popup roaming)"))
                                    .show();
                        } else {
                            proceedWithDownload(url, futurePath);
                        }
                    }
                });
            }
        });
    }

    private void proceedWithDownload(String url, String futurePath) {
        LoadBookTaskState state = new LoadBookTaskState();

        state.originalUri = Uri.parse(url);
        state.originalType = "ZIP";
        state.dynamicUri = Uri.parse(url);
        state.dynamicType = "ZIP";
        state.title = viewModel.title;
        state.futureFolderName = viewModel.identifier;
        state.futureFolderPath = futurePath;
        state.optionSplit = false;
        state.optionCopy = true;
        state.optionDelete = false;
        state.originalFile = SupportedFilesHelper.getFileName(this, Uri.parse(url));
        state.sourceLocation = SOURCE_LOCATION_LIBRIVOX;
        state.fileExtension = "zip";
        state.imagePath = futureCoverPic;
        state.onGoingLoading = true;
        state.progressText = getString(R.string.About_to_start_download);

        BookLoadingWorkLauncher.launch(this, state, false);

        FirebaseAnalyticsHelper.tellLibrivoxDownload(state.title);
        finish();
    }
}