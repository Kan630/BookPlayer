package com.driot.bookplayer.quickshare;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.driot.bookplayer.R;
import com.driot.bookplayer.helpers.ColorHelper;
import com.driot.bookplayer.nav.BaseBottomNavActivity;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.helpers.GoogleServicesHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.UriHelper;
import com.driot.bookplayer.utils.Tonio;
import com.google.android.material.button.MaterialButton;

import androidx.lifecycle.ViewModelProvider;
import dagger.hilt.android.AndroidEntryPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity for sharing books between devices using Nearby Connections API
 */
@AndroidEntryPoint
public class NearbyShareActivity extends BaseBottomNavActivity {

    private static final int QUICK_SHARE_VERSION = 1;

    private static final int PERMISSION_REQUEST_CODE = 1001;

    private Folder folder;
    private NearbyShareViewModel viewModel;

    private TextView tvBookInfo;
    private TextView tvStatus;
    private MaterialButton btnStartSharing;
    private ProgressBar progressBarTotal;
    private ProgressBar progressBarCurrent;
    private android.widget.CheckBox cbTransferProgress;

    private android.widget.ImageView ivCoverPreview;
    private TextView tvBookTitlePreview;
    private TextView tv_title;
    private TextView tv_quick_share_explain;
    private TextView tvConnectedEndpoint;

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable delayedRunnable;

    @Override
    protected int getNavId() { return R.id.nav_library; }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_nearby_share;
    }

    @Override
    protected boolean enableOngoingTaskOverlay() {
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(NearbyShareViewModel.class);

        // Get folder from intent (optional for receive mode)
        folder = getIntent().getParcelableExtra(Intents.EXTRA_FOLDER);
        boolean receiveMode = getIntent().getBooleanExtra("RECEIVE_MODE", false);
        viewModel.setSendMode(!receiveMode);

        if (folder == null && !receiveMode) {
            myLogEE(null, "No folder provided");
            myToastE(getString(R.string.nearby_share_error_no_folder));
            finish();
            return;
        }

        // Initialize views
        tv_title = findViewById(R.id.title);
        tv_quick_share_explain = findViewById(R.id.tv_quick_share_explain);
        tvBookInfo = findViewById(R.id.tvBookInfo);
        tvStatus = findViewById(R.id.tvNearbyShareStatus);
        btnStartSharing = findViewById(R.id.btnStartSharing);
        progressBarTotal = findViewById(R.id.progressBarTotal);
        progressBarCurrent = findViewById(R.id.progressBarCurrent);

        ivCoverPreview = findViewById(R.id.ivCoverPreview);
        tvBookTitlePreview = findViewById(R.id.tvBookTitlePreview);
        tvConnectedEndpoint = findViewById(R.id.tvConnectedEndpoint);
        cbTransferProgress = findViewById(R.id.cbTransferProgress);

        // Initialize checkbox state
        cbTransferProgress.setChecked(Option.getQuickShareTransferProgress());
        cbTransferProgress.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Option.setQuickShareTransferProgress(isChecked);
        });

        // Load book files only if we have a folder
        if (folder != null) {
            loadBookFiles();
            viewModel.prePrepareData(folder, cbTransferProgress.isChecked());
        } else {
            // In receive mode without a folder
            tvBookInfo.setText("");
            // Default preview text
            tvBookTitlePreview.setText(R.string.nearby_share_waiting_for_book);
        }

        setupObservers();

        btnStartSharing.setOnClickListener(v -> toggleSharing());

        // Log Google Play Services info for diagnostics
        GoogleServicesHelper.logPlayServicesInfo(this);
    }

    private void setupObservers() {
        viewModel.getStatus().observe(this, this::updateUI);
        viewModel.getProgressTotal().observe(this, progress -> progressBarTotal.setProgress(progress));
        viewModel.getProgressCurrent().observe(this, progress -> progressBarCurrent.setProgress(progress));
        viewModel.getConnectedEndpoint().observe(this, endpoint -> {
            if (endpoint != null && !endpoint.isEmpty()) {
                tvConnectedEndpoint.setVisibility(android.view.View.VISIBLE);
                tvConnectedEndpoint.setText(getString(R.string.nearby_share_connected_to, endpoint));
            } else {
                tvConnectedEndpoint.setVisibility(android.view.View.GONE);
            }
        });
        viewModel.getIsActive().observe(this, active -> updateUI(viewModel.getStatus().getValue()));
        viewModel.getIsTransferFinished().observe(this, finished -> updateUI(viewModel.getStatus().getValue()));
        viewModel.getToastError().observe(this, error -> {
            if (error != null)
                myToastE(error);
        });
        viewModel.getIsSendMode().observe(this, isSendMode -> {
            cbTransferProgress.setVisibility(isSendMode ? android.view.View.VISIBLE : android.view.View.GONE);
            updateUI(viewModel.getStatus().getValue());
        });

        viewModel.getBookName().observe(this, name -> {
            if (name != null && !name.isEmpty()) {
                tvBookTitlePreview.setText(name);
            } else {
                if (folder != null) {
                    tvBookTitlePreview.setText(folder.getName());
                } else {
                    tvBookTitlePreview.setText(R.string.nearby_share_waiting_for_book);
                }
            }
        });
        viewModel.getTotalFiles().observe(this, count -> updateBookInfo());
        viewModel.getTotalSize().observe(this, size -> updateBookInfo());
        viewModel.getCoverPath().observe(this, path -> {
            if (path != null && !path.isEmpty()) {
                ivCoverPreview.setImageURI(UriHelper.resolveUriFromPath(this, path));
            } else {
                if (folder != null && folder.image != null && !folder.image.isEmpty()) {
                    ivCoverPreview.setImageURI(UriHelper.resolveUriFromPath(this, folder.image));
                } else {
                    ivCoverPreview.setImageDrawable(null); // Or default icon
                }
            }
        });
    }

    private void updateBookInfo() {
        String name;
        int count;
        long size;

        if (folder != null) {
            // Already loaded in loadBookFiles
            return;
        } else {
            name = viewModel.getBookName().getValue();
            Integer c = viewModel.getTotalFiles().getValue();
            Long s = viewModel.getTotalSize().getValue();
            count = c != null ? c : 0;
            size = s != null ? s : 0;
        }

        if (count > 0 || size > 0) {
            tvBookInfo.setText(formatBookInfo(name, count, size));
        } else {
            tvBookInfo.setText("");
        }
    }

    private void setupSendMode() {
        // ... (This function is actually mostly empty now or handled by LiveData)
    }

    private void setupReceiveMode() {
        // ...
    }

    private void loadBookFiles() {
        new Thread(() -> {
            List<ZikFile> files = AppDatabase.getDatabase(this)
                    .zikFileDao()
                    .getZikFilesForFolder(folder.getId());

            runOnUiThread(() -> {
                long totalSize = 0;
                for (ZikFile zikFile : files) {
                    totalSize += (long) zikFile.getSize();
                }

                String info = formatBookInfo(folder.getName(), files.size(), totalSize);
                tvBookInfo.setText(info);

                // Set preview
                tvBookTitlePreview.setText(folder.getName());
                if (folder.image != null && !folder.image.isEmpty()) {
                    ivCoverPreview.setImageURI(UriHelper.resolveUriFromPath(this, folder.image));
                }
            });
        }).start();
    }

    private String formatBookInfo(String name, int fileCount, long size) {
        return String.format(
                getString(R.string.nearby_share_book_info),
                fileCount,
                Tonio.formatSizeMB_translate(this, size));
    }

    /**
     * Update UI based on active state.
     * 
     * @param statusMsg Optional status message to display when stopped. If null and
     *                  stopped, shows "Ready".
     */
    private void updateUI(String statusMsg) {
        boolean isActive = viewModel.getIsActive().getValue() != null && viewModel.getIsActive().getValue();
        boolean isSendMode = viewModel.getIsSendMode().getValue() != null && viewModel.getIsSendMode().getValue();
        boolean isFinished = viewModel.getIsTransferFinished().getValue() != null
                && viewModel.getIsTransferFinished().getValue();

        if (isActive) {
            btnStartSharing.setText(
                    isSendMode ? R.string.nearby_share_stop_advertising : R.string.nearby_share_stop_discovering);
            //TODO set background color RED
            progressBarTotal.setVisibility(android.view.View.VISIBLE);
            progressBarCurrent.setVisibility(android.view.View.VISIBLE);
        } else {
            btnStartSharing.setText(
                    isSendMode ? R.string.nearby_share_start_advertising : R.string.nearby_share_start_discovering);
            //TODO set background color back to Primary

            if (isFinished) {
                // Keep progress bars visible if finished successfully
                progressBarTotal.setVisibility(android.view.View.VISIBLE);
                progressBarCurrent.setVisibility(android.view.View.VISIBLE);
            } else {
                progressBarTotal.setVisibility(android.view.View.GONE);
                progressBarCurrent.setVisibility(android.view.View.GONE);
            }

            if (statusMsg == null) {
                if (!isFinished) {
                    statusMsg = getString(R.string.nearby_share_ready);
                    tvConnectedEndpoint.setVisibility(android.view.View.GONE);
                    tvConnectedEndpoint.setText("");
                }
            }
        }

        if (statusMsg != null) {
            tvStatus.setText(statusMsg);
        }

        String txt_explain, txt_title;
        if (isSendMode) {
            txt_title = getString(R.string.nearby_share_title_send);
            txt_explain = getString(R.string.nearby_share_explain_intro) + "\n\n"
                    + getString(R.string.nearby_share_explain_send);
        } else {
            txt_title = getString(R.string.nearby_share_title_receive);
            txt_explain = getString(R.string.nearby_share_explain_intro) + "\n\n"
                    + getString(R.string.nearby_share_explain_receive);
        }
        tv_quick_share_explain.setText(txt_explain);
        tv_title.setText(txt_title);
    }

    private void checkPermissionsAndStart() {
        myLogD("Checking permissions (SDK " + Build.VERSION.SDK_INT + ")");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // 31 - Android 12
            List<String> missing = new ArrayList<>();

            if (!checkPermission(Manifest.permission.BLUETOOTH_ADVERTISE))
                missing.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            if (!checkPermission(Manifest.permission.BLUETOOTH_CONNECT))
                missing.add(Manifest.permission.BLUETOOTH_CONNECT);
            if (!checkPermission(Manifest.permission.BLUETOOTH_SCAN))
                missing.add(Manifest.permission.BLUETOOTH_SCAN);
            if (!checkPermission(Manifest.permission.ACCESS_FINE_LOCATION))
                missing.add(Manifest.permission.ACCESS_FINE_LOCATION);

            // New permission for Android 13+ (SDK 33)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // 33
                if (!checkPermission(Manifest.permission.NEARBY_WIFI_DEVICES)) {
                    missing.add(Manifest.permission.NEARBY_WIFI_DEVICES);
                }
            }

            if (missing.isEmpty()) {
                startProcess();
            } else {
                requestPermissions(missing.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            }

        } else if (Build.VERSION.SDK_INT >= 29) { // 29 - Android 10
            if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                startProcess();
            } else {
                requestPermissions(
                        new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                        PERMISSION_REQUEST_CODE);
            }

        } else { // Android 8, 9
            if (checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                startProcess();
            } else {
                requestPermissions(
                        new String[] { Manifest.permission.ACCESS_COARSE_LOCATION },
                        PERMISSION_REQUEST_CODE);
            }
        }
    }

    private boolean checkPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            myLogD("Permission request result received");

            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                myLog("Permission " + permission + ": " + (granted ? "GRANTED" : "DENIED"));
            }

            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                startProcess();
            } else {
                myToastE(getString(R.string.nearby_share_permissions_required));
            }
        }
    }

    private void toggleSharing() {
        boolean isActive = viewModel.getIsActive().getValue() != null && viewModel.getIsActive().getValue();
        if (isActive) {
            myLogI("user clicks button : STOP");
            viewModel.sendMessageToOtherDevice("CANCEL", getString(R.string.nearby_share_transfer_cancelled_other));
            // let a chance of 1st message to arrive
            delayedRunnable = () -> viewModel.stopAll(getString(R.string.nearby_share_transfer_cancelled), false);
            handler.postDelayed(delayedRunnable, 200);
        } else {
            myLogI("user clicks button : START");
            checkPermissionsAndStart();
        }
    }

    private void startProcess() {
        boolean isSendMode = viewModel.getIsSendMode().getValue() != null && viewModel.getIsSendMode().getValue();
        if (isSendMode) {
            startAdvertising();
        } else {
            startDiscovery();
        }
    }

    private void startAdvertising() {
        viewModel.startAdvertising(getDeviceName(), folder, cbTransferProgress.isChecked());
    }

    private void startDiscovery() {
        myLog("Starting discovery");
        viewModel.startDiscovery(getDeviceName());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && delayedRunnable != null) {
            handler.removeCallbacks(delayedRunnable);
        }
    }

    /**
     * Get device name from Bluetooth adapter if available, otherwise use model name
     */
    private String getDeviceName() {
        String model = Build.MODEL;
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter
                    .getDefaultAdapter();
            if (bluetoothAdapter != null) {
                if (ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    String name = bluetoothAdapter.getName();
                    if (name != null && !name.isEmpty()) {
                        if (!name.equals(model)) {
                            return name + " (" + model + ")";
                        }
                        return name;
                    }
                }
            }
        } catch (Exception e) {
            myLogW("Could not get Bluetooth name: " + e.getMessage());
        }
        return model;
    }

}
