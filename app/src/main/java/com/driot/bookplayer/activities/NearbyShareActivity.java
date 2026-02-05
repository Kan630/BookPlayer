package com.driot.bookplayer.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.helpers.NearbyConnectionsHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.BaseActivity;

import java.util.List;

/**
 * Activity for sharing books between devices using Nearby Connections API
 */
public class NearbyShareActivity extends BaseActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;

    private Folder folder;
    private List<ZikFile> zikFiles;
    private NearbyConnectionsHelper nearbyHelper;

    private TextView tvBookInfo;
    private TextView tvStatus;
    private Button btnStartSharing;
    private ProgressBar progressBar;

    private boolean isAdvertising = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nearby_share);

        // Get folder from intent
        folder = getIntent().getParcelableExtra(Intents.EXTRA_FOLDER);
        if (folder == null) {
            myLogEE(null, "No folder provided");
            myToastE("Error: No folder to share");
            finish();
            return;
        }

        // Initialize views
        tvBookInfo = findViewById(R.id.tvBookInfo);
        tvStatus = findViewById(R.id.tvStatus);
        btnStartSharing = findViewById(R.id.btnStartSharing);
        progressBar = findViewById(R.id.progressBar);

        // Load book files
        loadBookFiles();

        // Initialize Nearby Connections Helper
        nearbyHelper = new NearbyConnectionsHelper(this);

        // Set up button click
        btnStartSharing.setOnClickListener(v -> {
            if (isAdvertising) {
                stopSharing();
            } else {
                checkPermissionsAndStartSharing();
            }
        });

        updateUI();
    }

    private void loadBookFiles() {
        new Thread(() -> {
            zikFiles = AppDatabase.getDatabase(this)
                    .zikFileDao()
                    .getZikFilesForFolder(folder.getId());

            runOnUiThread(() -> {
                long totalSize = 0;
                for (ZikFile zikFile : zikFiles) {
                    totalSize += (long) zikFile.getSize();
                }

                String info = String.format(
                        getString(R.string.nearby_share_book_info),
                        folder.getName(),
                        zikFiles.size(),
                        Tonio.formatMemPadding(NearbyShareActivity.this, totalSize));
                tvBookInfo.setText(info);
            });
        }).start();
    }

    private void updateUI() {
        if (isAdvertising) {
            btnStartSharing.setText(R.string.nearby_share_stop_advertising);
            tvStatus.setText(R.string.nearby_share_searching);
            progressBar.setVisibility(ProgressBar.VISIBLE);
        } else {
            btnStartSharing.setText(R.string.nearby_share_start_advertising);
            tvStatus.setText("");
            progressBar.setVisibility(ProgressBar.GONE);
        }
    }

    private void checkPermissionsAndStartSharing() {
        // Check required permissions based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            if (checkPermission(Manifest.permission.BLUETOOTH_ADVERTISE) &&
                    checkPermission(Manifest.permission.BLUETOOTH_CONNECT) &&
                    checkPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                startSharing();
            } else {
                requestPermissions(new String[] {
                        Manifest.permission.BLUETOOTH_ADVERTISE,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.NEARBY_WIFI_DEVICES
                }, PERMISSION_REQUEST_CODE);
            }
        } else {
            // Android 11 and below
            if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                startSharing();
            } else {
                requestPermissions(new String[] {
                        Manifest.permission.ACCESS_FINE_LOCATION
                }, PERMISSION_REQUEST_CODE);
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
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                startSharing();
            } else {
                myToastE(getString(R.string.nearby_share_permissions_required));
            }
        }
    }

    private void startSharing() {
        myLogI("Starting nearby sharing");
        isAdvertising = true;
        updateUI();

        String deviceName = Build.MODEL;
        nearbyHelper.startAdvertising(deviceName, new NearbyConnectionsHelper.AdvertisingCallback() {
            @Override
            public void onAdvertisingStarted() {
                runOnUiThread(() -> {
                    myLog("Advertising started");
                    tvStatus.setText(R.string.nearby_share_searching);
                });
            }

            @Override
            public void onConnectionInitiated(String endpointId, String endpointName) {
                runOnUiThread(() -> {
                    myLog("Connection initiated with: " + endpointName);
                    tvStatus.setText(String.format(getString(R.string.nearby_share_found_device), endpointName));

                    // Accept the connection automatically
                    nearbyHelper.acceptConnection(endpointId, new NearbyConnectionsHelper.PayloadCallback() {
                        @Override
                        public void onPayloadSent(long payloadId) {
                            // Payload sent
                        }

                        @Override
                        public void onPayloadTransferUpdate(long payloadId, int bytesTransferred, int totalBytes) {
                            int progress = (int) ((bytesTransferred * 100.0) / totalBytes);
                            runOnUiThread(() -> {
                                tvStatus.setText(
                                        String.format(getString(R.string.nearby_share_transferring), progress));
                                progressBar.setProgress(progress);
                            });
                        }

                        @Override
                        public void onPayloadReceived(long payloadId, byte[] data) {
                            // Not used in sender mode
                        }

                        @Override
                        public void onTransferComplete() {
                            runOnUiThread(() -> {
                                myLog("Transfer complete");
                                tvStatus.setText(R.string.nearby_share_complete);
                                myToast(getString(R.string.nearby_share_complete));
                                stopSharing();
                            });
                        }

                        @Override
                        public void onTransferFailed(String error) {
                            runOnUiThread(() -> {
                                myLogE("Transfer failed: " + error);
                                tvStatus.setText(R.string.nearby_share_failed);
                                myToastE(getString(R.string.nearby_share_failed) + ": " + error);
                                stopSharing();
                            });
                        }
                    });

                    // Start sending book data
                    nearbyHelper.sendBookData(endpointId, folder, zikFiles);
                });
            }

            @Override
            public void onConnectionFailed(String error) {
                runOnUiThread(() -> {
                    myLogE("Connection failed: " + error);
                    tvStatus.setText(R.string.nearby_share_failed);
                    myToastE(getString(R.string.nearby_share_failed));
                    stopSharing();
                });
            }
        });
    }

    private void stopSharing() {
        myLogI("Stopping nearby sharing");
        isAdvertising = false;
        nearbyHelper.stopAdvertising();
        updateUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (nearbyHelper != null) {
            nearbyHelper.cleanup();
        }
    }
}
