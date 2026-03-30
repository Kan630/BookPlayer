package com.driot.bookplayer.activities;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.imports.ImportBookSingleActivity;
import com.driot.bookplayer.imports.OngoingTaskViewModel;
import com.driot.bookplayer.nav.FullActivity;
import com.driot.bookplayer.utils.MsgBox;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.views.EditText2linesWithPaste;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class GetDirectLinkActivity extends FullActivity {

    private static final int REQ_DOWNLOAD_UNMETERED = 2001;
    private String pendingDownloadUrl;

    private View importDimScrim;
    private EditText2linesWithPaste etDirectDownload;
    private OngoingTaskViewModel viewModel;
    private TextView importDimMessage;

    private ActivityResultLauncher<Intent> loadBookActivityResultLauncher;

    @Override
    protected int getNavSectionId() {
        return R.id.nav_add;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_get_direct_link;
    }

    @Override
    protected boolean enableOngoingTaskOverlay() {
        return true;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        Button bDirectDownload = findViewById(R.id.bDirectDownload);
        etDirectDownload = findViewById(R.id.etDirectDownload);
        etDirectDownload.getEditText().setHint("https://...");

        importDimScrim = findViewById(R.id.importDimScrim);
        importDimMessage = findViewById(R.id.importDimMessage);

        // Eat all touches explicitly (belt & suspenders)
        importDimScrim.setOnTouchListener((v, ev) -> true);
        importDimScrim.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        importDimScrim.setContentDescription(getString(R.string.Import_in_progress));

        viewModel = new ViewModelProvider(this).get(OngoingTaskViewModel.class);
        // myLogD("ViewModel instance: " + System.identityHashCode(viewModel));

        viewModel.getUi().observe(this, ui -> {
            setImportOverlayVisible(ui.isRunningLike());
        });

        // ADD RESOURCE (log)
        registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    myLog("results from ActivityResultContracts.StartActivityForResult");
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        myLog("result OK - closing activity");
                        finish();
                    } else {
                        myLog("no ok result - doing nothing");
                    }
                });

        // JUST GET IT

        bDirectDownload.setOnClickListener(view -> {
            myLogI("Button click : JUST GET IT");
            String justGetItUrl = Tonio.cleanSearchString(etDirectDownload.getText());
            if (justGetItUrl.isEmpty()) {
                myToast(getString(R.string.Please_enter_a_URL));
                return;
            }
            if (!NetworkHelper.isConnected(this)) {
                myToast(getString(R.string.no_internet_connection));
                return;
            }
            if (Option.getNetworkPolicyManualDownload()
                    .equals(NetworkHelper.NetworkPolicyManual.NETWORK_POLICY_UNMETERED)
                    && !NetworkHelper.isUnmeteredConnected(this)) {

                pendingDownloadUrl = justGetItUrl;
                MsgBox.ask(this,
                        getString(R.string.download_warning_title_unmetered),
                        getString(R.string.download_warning_message_unmetered),
                        null,
                        getString(android.R.string.ok),
                        getString(android.R.string.cancel),
                        REQ_DOWNLOAD_UNMETERED);

            } else {
                Intent intent = new Intent(this, ImportBookSingleActivity.class);
                intent.putExtra(ImportBookSingleActivity.EXTRA_URI, Uri.parse(justGetItUrl));
                loadBookActivityResultLauncher.launch(intent);
                FirebaseAnalyticsHelper.tellAnalyticsManualDownload(justGetItUrl, "no_se");
            }
        });

        // RESULT LAUNCHER

        loadBookActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        startActivity(new Intent(this, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
                        // startActivity(new Intent(this, AddResourceActivity.class));
                    }
                });

        // Secret
        View secretEntry = findViewById(R.id.viewSecretEntry);
        final long[] taps = new long[3];
        secretEntry.setOnClickListener(v -> {
            System.arraycopy(taps, 1, taps, 0, taps.length - 1);
            taps[taps.length - 1] = System.currentTimeMillis();
            if (taps[0] >= System.currentTimeMillis() - 1000) {
                myLogI("click on secret");
                etDirectDownload.setText(Var.AUTOTEST_FILE_01);
            }
        });
    }

    private void setImportOverlayVisible(boolean show) {
        if (importDimScrim == null)
            return;

        final float target = show ? 1f : 0f;
        if (show && importDimScrim.getVisibility() != View.VISIBLE) {
            importDimScrim.setAlpha(0f);
            importDimScrim.setVisibility(View.VISIBLE);
        }
        importDimScrim.animate()
                .alpha(target)
                .setDuration(180)
                .withEndAction(() -> {
                    if (!show)
                        importDimScrim.setVisibility(View.GONE);
                })
                .start();

        View root = findViewById(R.id.rootContainer);
        if (root != null) {
            root.setImportantForAccessibility(
                    show ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                            : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        }

        importDimMessage.setText(getString(R.string.please_wait_another_book_is_being_imported));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_DOWNLOAD_UNMETERED) {
            if (resultCode == RESULT_OK && pendingDownloadUrl != null) {
                String urlToGet = pendingDownloadUrl;
                Intent intent = new Intent(this, ImportBookSingleActivity.class);
                intent.putExtra(ImportBookSingleActivity.EXTRA_URI, Uri.parse(urlToGet));
                loadBookActivityResultLauncher.launch(intent);
                FirebaseAnalyticsHelper.tellAnalyticsManualDownload(urlToGet, "no_se");
            } else {
                myLogD("User cancelled download (Network state popup)");
            }
            pendingDownloadUrl = null;
        }
    }
}
