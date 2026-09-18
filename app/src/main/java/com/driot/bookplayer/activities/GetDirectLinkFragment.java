package com.driot.bookplayer.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.imports.ImportBookSingleActivity;
import com.driot.bookplayer.imports.OngoingTaskViewModel;
import com.driot.bookplayer.utils.MsgBox;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingFragment;
import com.driot.bookplayer.views.EditText2linesWithPaste;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class GetDirectLinkFragment extends LoggingFragment {

    private static final int REQ_DOWNLOAD_UNMETERED = 2001;
    private String pendingDownloadUrl;

    private View importDimScrim;
    private EditText2linesWithPaste etDirectDownload;
    private OngoingTaskViewModel viewModel;
    private TextView importDimMessage;

    private ActivityResultLauncher<Intent> loadBookActivityResultLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // registerForActivityResult() must be called unconditionally during Fragment
        // initialization (onCreate, not onCreateView/onViewCreated - those can re-run on the
        // same Fragment instance without onCreate re-running, and re-registering would crash).
        loadBookActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK) {
                        startActivity(new Intent(requireContext(), MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_get_direct_link, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button bDirectDownload = view.findViewById(R.id.bDirectDownload);
        etDirectDownload = view.findViewById(R.id.etDirectDownload);
        etDirectDownload.setHint(getString(R.string.direct_link_hint));
        etDirectDownload.setHistoryKey("direct_link"); // keep history separate from other screens

        importDimScrim = view.findViewById(R.id.importDimScrim);
        importDimMessage = view.findViewById(R.id.importDimMessage);

        // Eat all touches explicitly (belt & suspenders)
        importDimScrim.setOnTouchListener((v, ev) -> true);
        importDimScrim.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        importDimScrim.setContentDescription(getString(R.string.Import_in_progress));

        viewModel = new ViewModelProvider(this).get(OngoingTaskViewModel.class);

        viewModel.getUi().observe(getViewLifecycleOwner(), ui -> {
            setImportOverlayVisible(ui.isRunningLike());
        });

        // JUST GET IT

        bDirectDownload.setOnClickListener(v -> {
            myLogI("Button click : JUST GET IT");
            String justGetItUrl = Tonio.cleanSearchString(etDirectDownload.getText());
            if (justGetItUrl.isEmpty()) {
                myToast(getString(R.string.Please_enter_a_URL));
                return;
            }
            if (!NetworkHelper.isConnected(requireContext())) {
                myToast(getString(R.string.no_internet_connection));
                return;
            }
            if (Option.getNetworkPolicyManualDownload()
                    .equals(NetworkHelper.NetworkPolicyManual.NETWORK_POLICY_UNMETERED)
                    && !NetworkHelper.isUnmeteredConnected(requireContext())) {

                pendingDownloadUrl = justGetItUrl;
                MsgBox.ask(this,
                        getString(R.string.download_warning_title_unmetered),
                        getString(R.string.download_warning_message_unmetered),
                        null,
                        getString(android.R.string.ok),
                        getString(android.R.string.cancel),
                        REQ_DOWNLOAD_UNMETERED);

            } else {
                Intent intent = new Intent(requireContext(), ImportBookSingleActivity.class);
                intent.putExtra(ImportBookSingleActivity.EXTRA_URI, Uri.parse(justGetItUrl));
                loadBookActivityResultLauncher.launch(intent);
                FirebaseAnalyticsHelper.tellAnalyticsManualDownload(justGetItUrl, "no_se");
            }
        });

        // Secret
        View secretEntry = view.findViewById(R.id.viewSecretEntry);
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

        View root = getView() != null ? getView().findViewById(R.id.rootContainer) : null;
        if (root != null) {
            root.setImportantForAccessibility(
                    show ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                            : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        }

        importDimMessage.setText(getString(R.string.please_wait_another_book_is_being_imported));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_DOWNLOAD_UNMETERED) {
            if (resultCode == android.app.Activity.RESULT_OK && pendingDownloadUrl != null) {
                String urlToGet = pendingDownloadUrl;
                Intent intent = new Intent(requireContext(), ImportBookSingleActivity.class);
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
