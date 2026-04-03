package com.driot.bookplayer.activities;

import static com.driot.bookplayer.global.Var.SOURCE_LOCATION_EBOOK_GUTENDEX;
import static com.driot.bookplayer.helpers.StorageHelper.getUnzipFolder;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.ebooks.gutendex.GutendexBook;
import com.driot.bookplayer.ebooks.gutendex.GutendexMapper;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.helpers.SupportedFilesHelper;
import com.driot.bookplayer.imports.BookLoadingWorkLauncher;
import com.driot.bookplayer.imports.ImportBookTaskState;
import com.driot.bookplayer.imports.ImportHelper;
import com.driot.bookplayer.nav.FullActivity;
import com.driot.bookplayer.utils.HashWorker;
import com.driot.bookplayer.utils.MsgBox;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class EbookDetailActivity extends FullActivity {

    private static final int REQ_DOWNLOAD_UNMETERED = 2001;
    private static final int REQ_DOWNLOAD_ROAMING = 2002;

    private EbookDetailViewModel viewModel;

    private String pendingDownloadUrl;
    private String pendingDownloadPath;

    private ImageView coverView;
    private TextView tvTitle;
    private TextView tvAuthors;
    private TextView tvInfo;
    private TextView tvGutenbergLink;
    private TextView tvDescription;
    private TextView tvStatus;
    private Button bGet;

    private int gutendexId;
    private String title;
    private String authors;
    private String language;
    private int downloads;
    private String coverUrl;
    private String epubUrl;

    @Override
    protected int getNavSectionId() {
        return R.id.nav_add;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_ebook_detail;
    }

    @Override
    protected boolean enableOngoingTaskOverlay() {
        return true;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        coverView = findViewById(R.id.imageDetailCover);
        tvTitle = findViewById(R.id.textDetailTitle);
        tvAuthors = findViewById(R.id.textDetailAuthors);
        tvInfo = findViewById(R.id.textDetailInfo);
        tvGutenbergLink = findViewById(R.id.tvGutenbergLink);
        tvDescription = findViewById(R.id.tvDescription);
        tvStatus = findViewById(R.id.tvStatus);
        bGet = findViewById(R.id.bGet);

        // --- Read extras ---
        Intent intent = getIntent();
        gutendexId = intent.getIntExtra("gutendex_id", 0);
        title = intent.getStringExtra("title");
        authors = intent.getStringExtra("authors");
        language = intent.getStringExtra("language");
        downloads = intent.getIntExtra("downloads", 0);
        coverUrl = intent.getStringExtra("cover_url");
        epubUrl = intent.getStringExtra("epub_url");

        if (epubUrl == null || epubUrl.isEmpty() || gutendexId == 0) {
            myLogE("EbookDetailActivity: invalid extras, finishing.");
            finish();
            return;
        }

        if (title == null)
            title = "";
        if (authors == null)
            authors = "";
        if (language == null)
            language = "";

        // --- Populate UI ---

        tvTitle.setText(title);
        tvAuthors.setText(authors);

        String info = "";
        if (!language.isEmpty()) {
            info += getString(R.string.Language_2pt) + language;
        }
        if (downloads > 0) {
            if (!info.isEmpty())
                info += " · ";
            info += String.format(Locale.US, "%,d %s", downloads, getString(R.string.downloads));
        }
        tvInfo.setText(info);

        // Gutenberg link (simple)
        String gutenbergUrl = "https://www.gutenberg.org/ebooks/" + gutendexId;
        tvGutenbergLink.setText(gutenbergUrl);
        tvGutenbergLink.setOnClickListener(v -> {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(gutenbergUrl));
                startActivity(i);
            } catch (Exception e) {
                myToastE(getString(R.string.an_error_occurred));
            }
        });

        // Cover — show persisted file immediately if available
        java.io.File persistedCover = ImageHelper.getGutendexImageFile(this, gutendexId);
        if (persistedCover.exists()) {
            Glide.with(coverView.getContext())
                    .load(persistedCover)
                    .placeholder(R.drawable.placeholder_cover)
                    .error(R.drawable.placeholder_cover)
                    .into(coverView);
        } else {
            coverView.setImageResource(R.drawable.placeholder_cover);
        }

        // ViewModel: fetch once, survive orientation change / back nav
        viewModel = new ViewModelProvider(this).get(EbookDetailViewModel.class);
        viewModel.getBookData().observe(this, this::onBookRefreshed);
        viewModel.fetchBookIfNeeded(gutendexId);

        // Initial status
        tvStatus.setText("");

        // Button logic
        updateGetButtonEnabled();

        bGet.setOnClickListener(v -> {
            bGet.setEnabled(false);
            tvStatus.setText(getString(R.string.checking_availability));
            myLogI("------> USER CLICKS - GET - GUTENDEX EBOOK");
            final List<String> candidates = GutendexMapper.buildDownloadCandidates(epubUrl, gutendexId);
            myLog("candidates : " + String.join(", ", candidates));
            new Thread(() -> {
                String workingUrl = findFirstWorkingUrl(candidates);
                runOnUiThread(() -> {
                    if (workingUrl != null) {
                        tvStatus.setText("");
                        checkThenDownload(workingUrl);
                    } else {
                        myLogE("No working mirror found for gutendexId=" + gutendexId);
                        tvStatus.setText(getString(R.string.an_error_occurred));
                        bGet.setEnabled(true);
                    }
                });
            }).start();
        });
    }

    /** HEAD-checks each candidate URL in order; returns the first that answers 2xx/3xx, or null. */
    @Nullable
    private String findFirstWorkingUrl(List<String> candidates) {
        for (String urlStr : candidates) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setConnectTimeout(5_000);
                conn.setReadTimeout(5_000);
                conn.setRequestMethod("HEAD");
                conn.setInstanceFollowRedirects(true);
                conn.connect();
                int code = conn.getResponseCode();
                conn.disconnect();
                myLogD("HEAD " + code + " <- " + urlStr);
                if (code == HttpURLConnection.HTTP_OK
                        || code == HttpURLConnection.HTTP_PARTIAL
                        || code == HttpURLConnection.HTTP_MOVED_PERM
                        || code == HttpURLConnection.HTTP_MOVED_TEMP
                        || code == 307 || code == 308) {
                    myLogI("Using mirror: " + urlStr);
                    return urlStr;
                }
            } catch (Exception e) {
                myLogD("HEAD failed for " + urlStr + ": " + e.getMessage());
            }
        }
        return null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateGetButtonEnabled();
    }

    private void onBookRefreshed(GutendexBook book) {
        // Description: prefer summaries, fall back to subjects
        String description = null;
        if (book.summaries != null && !book.summaries.isEmpty()) {
            description = android.text.TextUtils.join("\n", book.summaries);
        } else if (book.subjects != null && !book.subjects.isEmpty()) {
            description = android.text.TextUtils.join(" · ", book.subjects);
        }
        if (description != null && !description.isEmpty()) {
            tvDescription.setText(description);
            tvDescription.setVisibility(View.VISIBLE);
        } else {
            tvDescription.setVisibility(View.GONE);
        }

        // Cover: only re-download if the URL changed or file is missing
        String newCoverUrl = GutendexMapper.findCoverUrl(book);
        if (newCoverUrl == null || newCoverUrl.isEmpty()) return;

        java.io.File imageFile = ImageHelper.getGutendexImageFile(this, gutendexId);
        String savedUrl = ImageHelper.getGutendexSavedCoverUrl(this, gutendexId);

        if (imageFile.exists() && newCoverUrl.equals(savedUrl)) {
            myLogD("onBookRefreshed: cover unchanged for id=" + gutendexId);
            return;
        }

        myLogD("onBookRefreshed: cover changed or missing, re-downloading id=" + gutendexId);
        new Thread(() -> {
            String path = ImageHelper.forceDownloadGutendexImage(this, gutendexId, newCoverUrl);
            if (path != null) {
                coverView.post(() -> {
                    try {
                        Glide.with(coverView.getContext())
                                .load(new java.io.File(path))
                                .placeholder(R.drawable.placeholder_cover)
                                .error(R.drawable.placeholder_cover)
                                .into(coverView);
                    } catch (Exception e) {
                        myLogEE(e, "glide error refreshing gutendex cover");
                    }
                });
            }
        }).start();
    }

    private void updateGetButtonEnabled() {
        AppDatabase.databaseReadExecutor.execute(() -> {
            boolean running = ImportHelper.isAnyImportActiveSync(this);
            runOnUiThread(() -> {
                bGet.setEnabled(!running);
                if (running) {
                    tvStatus.setText(getString(R.string.please_wait_another_book));
                } else {
                    tvStatus.setText("");
                }
            });
        });
    }

    private void checkThenDownload(String url) {
        // For now we use the same base folder as other imports
        String futurePath = getUnzipFolder(this).getAbsolutePath()
                + "/gutendex_" + gutendexId;

        AppDatabase.databaseReadExecutor.execute(() -> {
            if (AppDatabase.getDatabase(this).folderDao().folderAlreadyExist_checkFolderPath(futurePath) > 0) {
                runOnUiThread(() -> {
                    myToast(getString(R.string.error_media_already_loaded_samePath));
                    bGet.setEnabled(true);
                });
            } else {
                NetworkHelper.logCurrentNetworkState(this);
                runOnUiThread(() -> handleNetworkPolicyThenDownload(url, futurePath));
            }
        });
    }

    private void handleNetworkPolicyThenDownload(String url, String futurePath) {
        if (!NetworkHelper.isConnected(this)) {
            myToast(getString(R.string.no_internet_connection));
            bGet.setEnabled(true);
            return;
        }

        if (Option.getNetworkPolicyManualDownload().equals(NetworkHelper.NetworkPolicyManual.NETWORK_POLICY_UNMETERED)
                && !NetworkHelper.isUnmeteredConnected(this)) {
            pendingDownloadUrl = url;
            pendingDownloadPath = futurePath;
            MsgBox.ask(this,
                    getString(R.string.download_warning_title_unmetered),
                    getString(R.string.download_warning_message_unmetered),
                    null,
                    getString(android.R.string.ok),
                    getString(android.R.string.cancel),
                    REQ_DOWNLOAD_UNMETERED);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (Option.getNetworkPolicyManualDownload()
                    .equals(NetworkHelper.NetworkPolicyManual.NETWORK_POLICY_NOT_ROAMING)
                    && !NetworkHelper.isRoaming(this)) {
                pendingDownloadUrl = url;
                pendingDownloadPath = futurePath;
                MsgBox.ask(this,
                        getString(R.string.download_warning_title_roaming),
                        getString(R.string.download_warning_message_roaming),
                        null,
                        getString(android.R.string.ok),
                        getString(android.R.string.cancel),
                        REQ_DOWNLOAD_ROAMING);
            } else {
                proceedWithDownload(url, futurePath);
            }
        } else {
            proceedWithDownload(url, futurePath);
        }
    }

    private void proceedWithDownload(String url, String futurePath) {
        ImportBookTaskState state = new ImportBookTaskState();

        Uri epubUri = Uri.parse(url);
        state.originalUri = epubUri;
        state.sourceType = "EPUB";
        state.dynamicUri = epubUri;
        state.dynamicType = "EPUB";

        state.title = title;
        state.futureFolderName = "gutendex_" + gutendexId;
        state.futureFolderPath = futurePath;

        state.optionSplit = false;
        state.optionCopy = true;
        state.optionDelete = false;

        state.originalFile = SupportedFilesHelper.getFileName(this, epubUri);
        state.sourceLocation = SOURCE_LOCATION_EBOOK_GUTENDEX;
        state.fileExtension = "epub";

        // Compute originalHash for the EPUB URL
        try {
            String hash = HashWorker.computeHashFromUri(this, epubUri);
            if (hash != null && !hash.isEmpty()) {
                state.originalHash = hash;
                myLogD("Computed originalHash for Gutenberg ebook [" + title + "]: " + hash);
            } else {
                myLogE("Failed to compute hash for Gutenberg ebook [" + title + "], originalHash will be null");
            }
        } catch (Exception e) {
            myLogE("Error computing hash for Gutenberg ebook [" + title + "]: " + e.getMessage());
        }

        java.io.File coverFile = ImageHelper.getGutendexImageFile(this, gutendexId);
        state.imagePath = coverFile.exists() ? coverFile.getAbsolutePath() : null;

        state.onGoingLoading = true;
        state.progressText = getString(R.string.About_to_start_download);

        BookLoadingWorkLauncher.launch(this, state, false);

        // Add a dedicated analytics event if you want
        FirebaseAnalyticsHelper.tellEbookDownloadFromGutendex(title);

        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQ_DOWNLOAD_UNMETERED || requestCode == REQ_DOWNLOAD_ROAMING) {
                if (pendingDownloadUrl != null && pendingDownloadPath != null) {
                    proceedWithDownload(pendingDownloadUrl, pendingDownloadPath);
                }
            }
        } else {
            if (requestCode == REQ_DOWNLOAD_UNMETERED) {
                myLogD("User cancelled download (unmetered warning)");
                bGet.setEnabled(true);
            } else if (requestCode == REQ_DOWNLOAD_ROAMING) {
                myLogD("User cancelled download (roaming warning)");
                bGet.setEnabled(true);
            }
        }

        // Clear pending strings
        pendingDownloadUrl = null;
        pendingDownloadPath = null;
    }
}
