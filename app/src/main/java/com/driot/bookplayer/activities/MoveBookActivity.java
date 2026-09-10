package com.driot.bookplayer.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.helpers.StorageInfoCacheHelper;
import com.driot.bookplayer.helpers.SupportedFilesHelper;
import com.driot.bookplayer.helpers.UriHelper;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.utils.MsgBox;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.BaseActivity;
import com.driot.bookplayer.widgets.StorageBarView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Moves an already-imported book from its current storage location to a different one - unlike
 * ModifyFolderActivity's existing "pick a new location" repair flow (which just repoints the DB
 * to files the user already moved externally), this one actually copies the bytes, updates the
 * DB to point at the copies, then removes the originals.
 */
@AndroidEntryPoint
public class MoveBookActivity extends BaseActivity {

    private static final int REQ_CONFIRM_MOVE = 4001;

    private Folder folder;
    private StorageHelper.MemoryLocationType currentType;

    private LinearLayout llDestinations;
    private ImageView ivCurrentLocationIcon;
    private TextView tvCurrentLocation;
    private TextView tvCurrentLocationPath;
    private LinearLayout llMoveProgress;
    private TextView tvMoveProgressText;
    private ProgressBar pbMoveProgress;
    private Button btnMoveCancel;

    private volatile boolean cancelled = false;
    private boolean moveInProgress = false;

    // Set right before showing the confirm dialog, read back in onActivityResult.
    @Nullable
    private File pendingDestDir;
    @Nullable
    private Uri pendingDestTreeUri;
    private boolean pendingIsReserved;

    @Override
    protected int getNavSectionId() {
        return R.id.nav_add; // same section as ModifyFolderActivity - not itself a nav root
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_move_book);
        InsetHelper.apply(this);

        folder = getIntent().getParcelableExtra(Intents.EXTRA_FOLDER);
        if (folder == null) {
            myLogEE(null, "MoveBookActivity: no folder passed");
            myToastE(getString(R.string.could_not_identify_folder_to_modify));
            finish();
            return;
        }

        TextView tvTitle = findViewById(R.id.tvMoveBookTitle);
        tvTitle.setText(folder.getName());

        ivCurrentLocationIcon = findViewById(R.id.ivCurrentLocationIcon);
        tvCurrentLocation = findViewById(R.id.tvCurrentLocation);
        tvCurrentLocationPath = findViewById(R.id.tvCurrentLocationPath);
        llDestinations = findViewById(R.id.llDestinations);
        llMoveProgress = findViewById(R.id.llMoveProgress);
        tvMoveProgressText = findViewById(R.id.tvMoveProgressText);
        pbMoveProgress = findViewById(R.id.pbMoveProgress);
        btnMoveCancel = findViewById(R.id.btnMoveCancel);

        currentType = StorageHelper.getMemoryLocationType(this, folder.getPath());
        ivCurrentLocationIcon.setImageResource(folder.getMemoryLocationIcon(this));
        tvCurrentLocation.setText(folder.getMemoryLocationText(this));
        if (folder.getPath() != null && !folder.getPath().isEmpty()) {
            tvCurrentLocationPath.setText(folder.getPath());
            tvCurrentLocationPath.setVisibility(View.VISIBLE);
        } else {
            tvCurrentLocationPath.setVisibility(View.GONE);
        }

        buildDestinationRows();
        buildStorageSummary();
    }

    // ------------------------------------------------------------------------------------
    // Destination list - grouped as Copy (app-managed, fixed path) vs Link (user-picked folder,
    // nothing duplicated) so the practical tradeoff (extra space vs. none) is stated up front
    // rather than left for the user to infer from 4 flat options.
    // ------------------------------------------------------------------------------------

    private void buildDestinationRows() {
        llDestinations.removeAllViews();
        boolean sdAvailable = StorageHelper.isExternalSDCardAvailable(this);

        addGroupHeader(R.string.move_group_copy_title, R.string.move_group_copy_subtitle, R.color.pastel_blue_500);
        addDestinationRow(StorageHelper.MemoryLocationType.INTERNAL_RESERVED,
                R.drawable.ic_memory_general_smartphone_r,
                getString(R.string.audio_location_bookplayer_reserved_storage), DestMode.RESERVED);
        if (sdAvailable) {
            addDestinationRow(StorageHelper.MemoryLocationType.SDCARD_RESERVED,
                    R.drawable.ic_memory_sdcard_r,
                    getString(R.string.audio_location_sdcard_reserved_storage), DestMode.RESERVED);
        }

        addGroupHeader(R.string.move_group_link_title, R.string.move_group_link_subtitle, R.color.green_500);
        addDestinationRow(StorageHelper.MemoryLocationType.PHONE_SHARED,
                R.drawable.ic_memory_general_smartphone,
                getString(R.string.audio_location_smartphone_shared_storage), DestMode.LINK_DEFAULT);
        if (sdAvailable) {
            addDestinationRow(StorageHelper.MemoryLocationType.SDCARD_SHARED,
                    R.drawable.ic_memory_sdcard,
                    getString(R.string.audio_location_sdcard), DestMode.LINK_DEFAULT);
        }
        // Not tied to a specific MemoryLocationType until the user actually picks a folder -
        // classified afterwards from the resulting path/uri, same as any other shared location.
        addDestinationRow(StorageHelper.MemoryLocationType.NOT_FOUND,
                R.drawable.ic_folder_24px,
                getString(R.string.move_group_link_custom_folder), DestMode.PICKER);
    }

    private enum DestMode {
        RESERVED,      // fixed app-managed "Copy" folder, no prompt
        LINK_DEFAULT,  // fixed default "Link" folder on that medium, no prompt
        PICKER         // user picks a custom folder via SAF
    }

    private void addGroupHeader(int titleRes, int subtitleRes, int colorRes) {
        TextView title = new TextView(this);
        title.setText(getString(titleRes));
        title.setTextSize(15);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setTextColor(getResources().getColor(colorRes, null));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = ViewHelper.dp(this, 14);
        title.setLayoutParams(titleParams);
        llDestinations.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(getString(subtitleRes));
        subtitle.setTextSize(12);
        subtitle.setAlpha(0.7f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.bottomMargin = ViewHelper.dp(this, 6);
        subtitle.setLayoutParams(subtitleParams);
        llDestinations.addView(subtitle);
    }

    // ------------------------------------------------------------------------------------
    // Storage summary - reuses the same StorageBarView + cached size figures as StatsActivity,
    // just narrowed down to the three numbers relevant here: free space, space used by books
    // BookPlayer copied (app-managed storage), and space used by books it only links to (their
    // bytes live outside BookPlayer's own storage, so moving/deleting them there doesn't free
    // anything here - shown separately for exactly that reason).
    // ------------------------------------------------------------------------------------

    private void buildStorageSummary() {
        computeAndShowBookSize();

        TextView tvInternalTitle = findViewById(R.id.tvStorageInternalTitle);
        TextView tvInternalBody = findViewById(R.id.tvStorageInternalBody);
        StorageBarView barInternal = findViewById(R.id.storageBarInternal);
        LinearLayout llSdCard = findViewById(R.id.llStorageSdCard);
        TextView tvSdBody = findViewById(R.id.tvStorageSdCardBody);
        StorageBarView barSdCard = findViewById(R.id.storageBarSdCard);

        // Only worth labeling "Device Storage Memory" when there's a second (SD card) block
        // to distinguish it from - a single block needs no title of its own.
        boolean sdAvailable = StorageHelper.isExternalSDCardAvailable(this);
        tvInternalTitle.setVisibility(sdAvailable ? View.VISIBLE : View.GONE);

        long internalTotal = StorageHelper.getTotaLInternalMemorySize();
        long internalFree = StorageHelper.getAvailableInternalMemorySize();
        // usedByBookPlayer (internal) also bundles in the app's own db/logs/images overhead -
        // subtract that back out so "copied books" reflects audio content only.
        long internalCopied = Math.max(0, StorageInfoCacheHelper.getCachedInternalUsedByBookPlayer()
                - StorageInfoCacheHelper.getCachedInternalApp());
        long internalLinked = StorageInfoCacheHelper.getCachedInternalLinkedAudios();
        renderStorageBlock(tvInternalBody, barInternal, internalTotal, internalFree, internalCopied, internalLinked);

        if (sdAvailable) {
            long sdTotal = StorageHelper.getTotalRemovableSDCardSize(this);
            long sdFree = StorageHelper.getAvailableRemovableSDCardSize(this);
            long sdCopied = StorageInfoCacheHelper.getCachedSDCardUsedByBookPlayer();
            long sdLinked = StorageInfoCacheHelper.getCachedSDCardLinkedAudios();
            renderStorageBlock(tvSdBody, barSdCard, sdTotal, sdFree, sdCopied, sdLinked);
            llSdCard.setVisibility(View.VISIBLE);
        } else {
            llSdCard.setVisibility(View.GONE);
        }
    }

    private void computeAndShowBookSize() {
        TextView tvBookSize = findViewById(R.id.tvStorageBookSize);
        AppDatabase.databaseReadExecutor.execute(() -> {
            List<ZikFile> files = AppDatabase.getDatabase(this).zikFileDao().getZikFiles(folder.getId());
            long totalSize = 0;
            for (ZikFile zf : files) {
                try {
                    Uri src = UriHelper.resolveUriFromPath(this, zf.getPath());
                    if (src != null) {
                        totalSize += UriHelper.getSize(this, src);
                    }
                } catch (Exception ignored) {
                }
            }
            long finalTotalSize = totalSize;
            runOnUiThread(() -> tvBookSize.setText(
                    getString(R.string.move_storage_book_size, Tonio.getReadableSize(finalTotalSize))));
        });
    }

    private void renderStorageBlock(TextView tvBody, StorageBarView bar, long total, long free, long copied,
            long linked) {
        long others = Math.max(0, total - free - copied - linked);

        String copiedLine = getString(R.string.move_storage_copied_books, Tonio.getReadableSize(copied));
        String linkedLine = getString(R.string.move_storage_linked_books, Tonio.getReadableSize(linked));
        String freeLine = getString(R.string.move_storage_free, Tonio.getReadableSize(free),
                Tonio.getReadableSize(total));
        String fullText = copiedLine + "\n" + linkedLine + "\n" + freeLine;

        SpannableString spannable = new SpannableString(fullText);
        int copiedColor = getResources().getColor(R.color.pastel_blue_500, null);
        int linkedColor = getResources().getColor(R.color.green_500, null);
        spannable.setSpan(new ForegroundColorSpan(copiedColor), 0, copiedLine.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int linkedStart = copiedLine.length() + 1;
        spannable.setSpan(new ForegroundColorSpan(linkedColor), linkedStart, linkedStart + linkedLine.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvBody.setText(spannable);

        // No "appStorage" segment here (unlike StatsActivity's bar) - that overhead is already
        // folded into "others" above, since this screen only cares about copied vs linked books.
        bar.setStorageValues(total, others, copied, 0, linked);
    }

    private void addDestinationRow(StorageHelper.MemoryLocationType type, int iconRes, String label,
            DestMode mode) {
        boolean isCurrent = mode != DestMode.PICKER && type == currentType;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = ViewHelper.dp(this, 10);
        row.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = ViewHelper.dp(this, 8);
        row.setLayoutParams(rowParams);
        row.setBackgroundResource(R.drawable.bg_chip);

        ImageView icon = new ImageView(this);
        int iconSize = ViewHelper.dp(this, 36);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.setMarginEnd(ViewHelper.dp(this, 12));
        icon.setLayoutParams(iconParams);
        icon.setImageResource(iconRes);
        row.addView(icon);

        TextView labelView = new TextView(this);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        labelView.setLayoutParams(labelParams);
        labelView.setText(isCurrent ? label + "\n" + getString(R.string.move_book_current_marker) : label);
        row.addView(labelView);

        if (isCurrent) {
            row.setAlpha(0.5f);
        } else {
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> onDestinationChosen(type, mode));
        }

        llDestinations.addView(row);
    }

    // ------------------------------------------------------------------------------------
    // Destination resolution (reserved = fixed app-managed path, shared = user picks a folder)
    // ------------------------------------------------------------------------------------

    private void onDestinationChosen(StorageHelper.MemoryLocationType type, DestMode mode) {
        if (moveInProgress) {
            return;
        }
        switch (mode) {
            case RESERVED: {
                boolean useSdCard = (type == StorageHelper.MemoryLocationType.SDCARD_RESERVED);
                File destDir = computeUniqueDestinationDir(StorageHelper.getUnzipFolder(this, useSdCard));
                confirmAndStartMove(destDir, null, true);
                break;
            }
            case LINK_DEFAULT: {
                boolean useSdCard = (type == StorageHelper.MemoryLocationType.SDCARD_SHARED);
                File base = StorageHelper.getDefaultLinkedFolder(this, useSdCard);
                if (base == null) {
                    myToast(getString(R.string.could_not_identify_folder_to_modify));
                    break;
                }
                File destDir = computeUniqueDestinationDir(base);
                confirmAndStartMove(destDir, null, false);
                break;
            }
            case PICKER: {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                try {
                    pickSharedFolderLauncher.launch(intent);
                } catch (Exception e) {
                    myToastEE(e, "could not open folder picker");
                }
                break;
            }
        }
    }

    private File computeUniqueDestinationDir(File base) {
        String safeName = folder.getName() == null ? "book" : folder.getName();
        File dest = new File(base, safeName);
        int suffix = 1;
        while (dest.exists()) {
            dest = new File(base, safeName + " (" + (++suffix) + ")");
        }
        return dest;
    }

    private final ActivityResultLauncher<Intent> pickSharedFolderLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                    return;
                }
                Uri treeUri = result.getData().getData();
                if (treeUri == null) {
                    return;
                }
                try {
                    getContentResolver().takePersistableUriPermission(treeUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (Exception e) {
                    myLogEE(e, "takePersistableUriPermission failed");
                }
                confirmAndStartMove(null, treeUri, false);
            });

    // ------------------------------------------------------------------------------------
    // Confirmation
    // ------------------------------------------------------------------------------------

    private void confirmAndStartMove(@Nullable File destDir, @Nullable Uri destTreeUri, boolean isReserved) {
        AppDatabase.databaseReadExecutor.execute(() -> {
            List<ZikFile> files = AppDatabase.getDatabase(this).zikFileDao().getZikFiles(folder.getId());
            long totalSize = 0;
            for (ZikFile zf : files) {
                try {
                    Uri src = UriHelper.resolveUriFromPath(this, zf.getPath());
                    if (src != null) {
                        totalSize += UriHelper.getSize(this, src);
                    }
                } catch (Exception ignored) {
                }
            }
            int count = files.size();
            long finalTotalSize = totalSize;
            runOnUiThread(() -> {
                pendingDestDir = destDir;
                pendingDestTreeUri = destTreeUri;
                pendingIsReserved = isReserved;
                String message = getString(R.string.move_confirm_message, count,
                        Tonio.getReadableSize(finalTotalSize));
                MsgBox.ask(this, getString(R.string.move_confirm_title), message, null,
                        getString(R.string.move_book_button), getString(android.R.string.cancel), REQ_CONFIRM_MOVE);
            });
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CONFIRM_MOVE && resultCode == RESULT_OK) {
            startMove(pendingDestDir, pendingDestTreeUri, pendingIsReserved);
        }
    }

    // ------------------------------------------------------------------------------------
    // The actual move
    // ------------------------------------------------------------------------------------

    private void startMove(@Nullable File destDir, @Nullable Uri destTreeUri, boolean isReserved) {
        moveInProgress = true;
        cancelled = false;
        llMoveProgress.setVisibility(View.VISIBLE);
        for (int i = 0; i < llDestinations.getChildCount(); i++) {
            llDestinations.getChildAt(i).setEnabled(false);
            llDestinations.getChildAt(i).setAlpha(0.5f);
        }
        btnMoveCancel.setOnClickListener(v -> cancelled = true);

        new Thread(() -> runMove(destDir, destTreeUri, isReserved)).start();
    }

    private void updateMoveProgress(int done, int total, String fileName) {
        pbMoveProgress.setMax(total);
        pbMoveProgress.setProgress(done);
        tvMoveProgressText.setText(getString(R.string.move_progress_text, done, total, fileName));
    }

    private void runMove(@Nullable File destDir, @Nullable Uri destTreeUri, boolean isReserved) {
        List<ZikFile> files = AppDatabase.getDatabase(this).zikFileDao().getZikFiles(folder.getId());
        boolean hasCover = folder.image != null && !folder.image.isEmpty();
        int total = files.size() + (hasCover ? 1 : 0);
        int done = 0;

        Map<Long, String> oldZikFilePaths = new HashMap<>();
        Map<Long, String> newZikFilePaths = new HashMap<>();
        String oldCoverPath = folder.image;
        String newCoverPath = null;
        List<File> createdFiles = new ArrayList<>();
        List<Uri> createdUris = new ArrayList<>();
        boolean allOk = true;

        DocumentFile destTreeDoc = destTreeUri != null ? DocumentFile.fromTreeUri(this, destTreeUri) : null;
        if (isReserved && destDir != null) {
            destDir.mkdirs();
        }

        for (ZikFile zf : files) {
            oldZikFilePaths.put(zf.getId(), zf.getPath());
            if (cancelled) {
                allOk = false;
                break;
            }
            try {
                Uri srcUri = UriHelper.resolveUriFromPath(this, zf.getPath());
                if (srcUri == null) {
                    allOk = false;
                    break;
                }
                String fileName = zf.getName();
                String newPath = copyOneFile(srcUri, fileName, destDir, destTreeDoc, createdFiles, createdUris);
                if (newPath == null) {
                    allOk = false;
                    break;
                }
                newZikFilePaths.put(zf.getId(), newPath);
                done++;
                int finalDone = done, finalTotal = total;
                runOnUiThread(() -> updateMoveProgress(finalDone, finalTotal, fileName));
            } catch (Exception e) {
                myLogEE(e, "runMove: error copying " + zf.getName());
                allOk = false;
                break;
            }
        }

        if (allOk && !cancelled && hasCover) {
            try {
                Uri srcCover = UriHelper.resolveUriFromPath(this, oldCoverPath);
                if (srcCover != null) {
                    String coverFileName = new File(oldCoverPath.replace("file://", "")).getName();
                    newCoverPath = copyOneFile(srcCover, coverFileName, destDir, destTreeDoc, createdFiles, createdUris);
                    done++;
                    int finalDone = done, finalTotal = total;
                    String finalCoverName = coverFileName;
                    runOnUiThread(() -> updateMoveProgress(finalDone, finalTotal, finalCoverName));
                }
            } catch (Exception e) {
                // Non-fatal: the book itself still moves fine, it just keeps its old cover path -
                // which will still resolve since we only delete old files we know we replaced.
                myLogEE(e, "runMove: error copying cover (non-fatal)");
            }
        }

        if (cancelled || !allOk) {
            for (File f : createdFiles) {
                try {
                    f.delete();
                } catch (Exception ignored) {
                }
            }
            for (Uri u : createdUris) {
                try {
                    DocumentsContract.deleteDocument(getContentResolver(), u);
                } catch (Exception ignored) {
                }
            }
            boolean finalCancelled = cancelled;
            runOnUiThread(() -> onMoveFinished(finalCancelled));
            return;
        }

        // Everything copied - point the DB at the new copies, then remove the old files.
        boolean oldWasReserved = StorageHelper.getMemoryLocationType(this, folder.getPath())
                == StorageHelper.MemoryLocationType.INTERNAL_RESERVED
                || StorageHelper.getMemoryLocationType(this, folder.getPath())
                        == StorageHelper.MemoryLocationType.SDCARD_RESERVED;
        String oldFolderRootPath = folder.getPath();

        AppDatabase db = AppDatabase.getDatabase(this);
        for (ZikFile zf : files) {
            String np = newZikFilePaths.get(zf.getId());
            if (np != null) {
                zf.setPath(np);
                db.zikFileDao().update(zf);
            }
        }
        String newFolderRootPath = isReserved ? destDir.getAbsolutePath() : destTreeUri.toString();
        folder.setPath(newFolderRootPath);
        if (newCoverPath != null) {
            folder.image = newCoverPath;
        }
        db.folderDao().update(folder);

        for (String oldPath : oldZikFilePaths.values()) {
            deleteOldPath(oldPath);
        }
        if (newCoverPath != null && oldCoverPath != null) {
            deleteOldPath(oldCoverPath);
        }
        // Best-effort tidy-up of the old app-managed folder, only when we own that whole
        // directory (never for a "shared" location, where the containing folder might hold
        // other content that isn't ours to touch) - and only if it's genuinely empty now.
        if (oldWasReserved && oldFolderRootPath != null && !oldFolderRootPath.startsWith("content://")) {
            try {
                File oldDir = new File(oldFolderRootPath.startsWith("file://")
                        ? Uri.parse(oldFolderRootPath).getPath() : oldFolderRootPath);
                File[] remaining = oldDir.listFiles();
                if (remaining != null && remaining.length == 0) {
                    oldDir.delete();
                }
            } catch (Exception ignored) {
            }
        }

        runOnUiThread(this::onMoveSucceeded);
    }

    @Nullable
    private String copyOneFile(Uri srcUri, String fileName, @Nullable File destDir,
            @Nullable DocumentFile destTreeDoc, List<File> createdFiles, List<Uri> createdUris) throws Exception {
        if (destDir != null) {
            File outFile = new File(destDir, fileName);
            copyUriToOutputStream(srcUri, new FileOutputStream(outFile));
            createdFiles.add(outFile);
            return outFile.getAbsolutePath();
        } else if (destTreeDoc != null) {
            String mime = SupportedFilesHelper.getMimeType(this, srcUri);
            DocumentFile created = destTreeDoc.createFile(mime != null ? mime : "application/octet-stream", fileName);
            if (created == null) {
                return null;
            }
            OutputStream out = getContentResolver().openOutputStream(created.getUri());
            if (out == null) {
                return null;
            }
            copyUriToOutputStream(srcUri, out);
            createdUris.add(created.getUri());
            return created.getUri().toString();
        }
        return null;
    }

    private void copyUriToOutputStream(Uri srcUri, OutputStream out) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(srcUri)) {
            if (in == null) {
                throw new IllegalStateException("openInputStream returned null for: " + srcUri);
            }
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                if (cancelled) {
                    throw new InterruptedException("move cancelled");
                }
                out.write(buf, 0, len);
            }
        } finally {
            try {
                out.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void deleteOldPath(String path) {
        try {
            if (path.startsWith("content://")) {
                DocumentsContract.deleteDocument(getContentResolver(), Uri.parse(path));
            } else {
                FileHelper.deleteFile(this, path);
            }
        } catch (Exception e) {
            myLogEE(e, "deleteOldPath: could not delete [" + path + "]");
        }
    }

    private void onMoveSucceeded() {
        moveInProgress = false;
        myToast(getString(R.string.move_success));
        // The copied/linked split just changed - refresh the cached figures StatsActivity (and
        // this screen, if opened again for another book) reads, instead of leaving them stale
        // until the next app start.
        StorageInfoCacheHelper.recalculate(this);
        setResult(RESULT_OK);
        finish();
    }

    private void onMoveFinished(boolean wasCancelled) {
        moveInProgress = false;
        llMoveProgress.setVisibility(View.GONE);
        for (int i = 0; i < llDestinations.getChildCount(); i++) {
            llDestinations.getChildAt(i).setEnabled(true);
            llDestinations.getChildAt(i).setAlpha(1f);
        }
        myToast(getString(wasCancelled ? R.string.move_cancelled : R.string.move_failed));
    }
}
