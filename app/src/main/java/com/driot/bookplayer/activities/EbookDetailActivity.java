package com.driot.bookplayer.activities;

import static com.driot.bookplayer.global.Var.SOURCE_LOCATION_EBOOK_GUTENDEX;
import static com.driot.bookplayer.helpers.StorageHelper.getUnzipFolder;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.helpers.SupportedFilesHelper;
import com.driot.bookplayer.imports.BookLoadingWorkLauncher;
import com.driot.bookplayer.imports.ImportBookTaskState;
import com.driot.bookplayer.imports.ImportHelper;
import com.driot.bookplayer.nav.BaseBottomNavActivity;
import com.driot.bookplayer.utils.HashWorker;

import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class EbookDetailActivity extends BaseBottomNavActivity {

    private ImageView coverView;
    private TextView tvTitle;
    private TextView tvAuthors;
    private TextView tvInfo;
    private TextView tvGutenbergLink;
    private TextView tvStatus;
    private Button bGet;

    private int gutendexId;
    private String title;
    private String authors;
    private String language;
    private int downloads;
    private String coverUrl;
    private String epubUrl;

    // If later you download cover to a local file, store its path here
    private String localCoverPath;

    @Override
    protected int getNavId() {
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

        // Cover
        if (coverUrl != null && !coverUrl.isEmpty()) {
            Glide.with(coverView.getContext())
                    .load(coverUrl)
                    .placeholder(R.drawable.placeholder_cover)
                    .error(R.drawable.placeholder_cover)
                    .into(coverView);
        } else {
            coverView.setImageResource(R.drawable.placeholder_cover);
        }

        // For now, no local cover download → importer will rely on embedded cover in
        // EPUB
        localCoverPath = null;

        // Initial status
        tvStatus.setText("");

        // Button logic
        updateGetButtonEnabled();

        bGet.setOnClickListener(v -> {
            bGet.setEnabled(false);
            myLogI("------> USER CLICKS - GET -        GUTENDEX EBOOK");
            checkThenDownload(epubUrl);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateGetButtonEnabled();
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
        if (Option.getNetworkPolicyManualDownload().equals(NetworkHelper.NetworkPolicyManual.NETWORK_POLICY_UNMETERED)
                && !NetworkHelper.isUnmeteredConnected(this)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.download_warning_title_unmetered)
                    .setMessage(R.string.download_warning_message_unmetered)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> proceedWithDownload(url, futurePath))
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                        myLogD("User cancelled download (unmetered warning)");
                        bGet.setEnabled(true);
                    })
                    .show();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (Option.getNetworkPolicyManualDownload()
                    .equals(NetworkHelper.NetworkPolicyManual.NETWORK_POLICY_NOT_ROAMING)
                    && !NetworkHelper.isRoaming(this)) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.download_warning_title_roaming)
                        .setMessage(R.string.download_warning_message_roaming)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> proceedWithDownload(url, futurePath))
                        .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                            myLogD("User cancelled download (roaming warning)");
                            bGet.setEnabled(true);
                        })
                        .show();
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

        // For now: no explicit cover file – rely on embedded cover in EPUB.
        // If later you download the cover to a file, set:
        // state.imagePath = localCoverPath;
        state.imagePath = localCoverPath;

        state.onGoingLoading = true;
        state.progressText = getString(R.string.About_to_start_download);

        BookLoadingWorkLauncher.launch(this, state, false);

        // Add a dedicated analytics event if you want
        FirebaseAnalyticsHelper.tellEbookDownloadFromGutendex(title);

        finish();
    }
}
