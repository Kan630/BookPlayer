package com.driot.bookplayer.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.NearbyConnectionsHelper;
import com.driot.bookplayer.helpers.NearbyShareReceiverHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.BaseActivity;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity for sharing books between devices using Nearby Connections API
 */
public class NearbyShareActivity extends BaseActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;

    private Folder folder;
    private List<ZikFile> zikFiles;
    private NearbyConnectionsHelper nearbyHelper;
    private NearbyShareReceiverHelper receiverHelper;
    private final java.util.concurrent.ExecutorService processingExecutor = java.util.concurrent.Executors
            .newSingleThreadExecutor();

    private TextView tvBookInfo;
    private TextView tvStatus;
    private MaterialButton btnStartSharing;
    private ProgressBar progressBar;
    private RadioGroup rgMode;
    private RadioButton rbSendMode;
    private RadioButton rbReceiveMode;

    private android.widget.LinearLayout layoutBookPreview;
    private android.widget.ImageView ivCoverPreview;
    private TextView tvBookTitlePreview;
    private TextView title;

    private boolean isActive = false; // Either advertising or discovering
    private boolean isSendMode = true; // true = send/advertise, false = receive/discover

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nearby_share);
        InsetHelper.apply(this);

        // Get folder from intent (optional for receive mode)
        folder = getIntent().getParcelableExtra(Intents.EXTRA_FOLDER);
        boolean receiveMode = getIntent().getBooleanExtra("RECEIVE_MODE", false);

        if (folder == null && !receiveMode) {
            myLogEE(null, "No folder provided");
            myToastE("Error: No folder to share");
            finish();
            return;
        }

        // Initialize views
        title = findViewById(R.id.title);
        tvBookInfo = findViewById(R.id.tvBookInfo);
        tvStatus = findViewById(R.id.tvStatus);
        btnStartSharing = findViewById(R.id.btnStartSharing);
        progressBar = findViewById(R.id.progressBar);
        rgMode = findViewById(R.id.rgMode);
        rbSendMode = findViewById(R.id.rbSendMode);
        rbReceiveMode = findViewById(R.id.rbReceiveMode);

        layoutBookPreview = findViewById(R.id.layoutBookPreview);
        ivCoverPreview = findViewById(R.id.ivCoverPreview);
        tvBookTitlePreview = findViewById(R.id.tvBookTitlePreview);

        // Set up mode change listener
        rgMode.setOnCheckedChangeListener((group, checkedId) -> {
            isSendMode = (checkedId == R.id.rbSendMode);
            updateUI();
        });

        // Load book files only if we have a folder
        if (folder != null) {
            loadBookFiles();
        } else {
            // In receive mode without a folder
            tvBookInfo.setText(getString(R.string.nearby_share_receive_mode_info));
            zikFiles = new ArrayList<>();
            // Default preview text
            tvBookTitlePreview.setText("Waiting for book...");
        }

        // Initialize Nearby Connections Helper
        nearbyHelper = new NearbyConnectionsHelper(this);

        // Set up button click
        btnStartSharing.setOnClickListener(v -> {
            if (isActive) {
                stopSharing();
            } else {
                checkPermissionsAndStart();
            }
        });

        // Hide the mode toggle as requested in UX update
        findViewById(R.id.rgMode).setVisibility(android.view.View.GONE);

        // If launched in receive mode, auto-select Receive and update UI
        if (receiveMode) {
            rbReceiveMode.setChecked(true);
            isSendMode = false;
        }

        // Log Google Play Services info for diagnostics
        logPlayServicesInfo();

        updateUI();
    }

    private void logPlayServicesInfo() {
        try {
            GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
            int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);

            if (resultCode == ConnectionResult.SUCCESS) {
                myLogI("Google Play Services: Available");
            } else {
                myLogW("Google Play Services issue: " + apiAvailability.getErrorString(resultCode));
            }

            // Try to get version (may not work on all devices)
            try {
                int versionCode = getPackageManager()
                        .getPackageInfo("com.google.android.gms", 0).versionCode;
                myLogI("Google Play Services version code: " + versionCode);
            } catch (Exception e) {
                myLogW("Could not get Play Services version: " + e.getMessage());
            }
        } catch (Exception e) {
            myLogE("Error checking Play Services: " + e.getMessage());
        }
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
                        Tonio.formatSizeMB_translate(this, totalSize));
                tvBookInfo.setText(info);

                // Set preview
                tvBookTitlePreview.setText(folder.getName());
                if (folder.image != null && !folder.image.isEmpty()) {
                    ivCoverPreview.setImageURI(android.net.Uri.fromFile(new java.io.File(folder.image)));
                }
            });
        }).start();
    }

    private void updateUI() {
        if (isActive) {
            btnStartSharing.setText(
                    isSendMode ? R.string.nearby_share_stop_advertising : R.string.nearby_share_stop_discovering);
            btnStartSharing.setBackgroundTintList(getColorStateList(R.color.colorError));
            progressBar.setVisibility(android.view.View.VISIBLE);
        } else {
            btnStartSharing.setText(
                    isSendMode ? R.string.nearby_share_start_advertising : R.string.nearby_share_start_discovering);

            btnStartSharing.setBackgroundTintList(null);
            progressBar.setVisibility(android.view.View.GONE);
            tvStatus.setText(R.string.nearby_share_ready);
        }

        // Update Title dynamically
        title.setText(isSendMode ? "Quick Share - Send" : "Quick Share - Receive");
    }

    private void checkPermissionsAndStart() {
        myLogI("Checking permissions (SDK " + Build.VERSION.SDK_INT + ")");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            List<String> missing = new ArrayList<>();

            if (!checkPermission(Manifest.permission.BLUETOOTH_ADVERTISE))
                missing.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            if (!checkPermission(Manifest.permission.BLUETOOTH_CONNECT))
                missing.add(Manifest.permission.BLUETOOTH_CONNECT);
            if (!checkPermission(Manifest.permission.BLUETOOTH_SCAN))
                missing.add(Manifest.permission.BLUETOOTH_SCAN);
            if (!checkPermission(Manifest.permission.ACCESS_FINE_LOCATION))
                missing.add(Manifest.permission.ACCESS_FINE_LOCATION);

            if (missing.isEmpty()) {
                startProcess();
            } else {
                requestPermissions(missing.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            }

        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                startProcess();
            } else {
                requestPermissions(
                        new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                        PERMISSION_REQUEST_CODE);
            }

        } else {
            // API ≤ 28 — FORCE REQUEST
            myLogW("SDK ≤ 28: Forcing runtime request of FINE + COARSE location");

            requestPermissions(
                    new String[] {
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    PERMISSION_REQUEST_CODE);
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
            myLogI("Permission request result received");

            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                myLogI("Permission " + permission + ": " + (granted ? "GRANTED" : "DENIED"));
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

    private void startProcess() {
        if (isSendMode) {
            startAdvertising();
        } else {
            startDiscovery();
        }
    }

    private void startAdvertising() {
        myLogI("Starting advertising");
        isActive = true;
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

                    // Accept the connection and set up callbacks
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
                        public void onFilePayloadReceived(long payloadId, Payload filePayload) {
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
                });
            }

            @Override
            public void onConnectionEstablished(String endpointId) {
                runOnUiThread(() -> {
                    myLog("Connection established, starting transfer");
                    tvStatus.setText(R.string.nearby_share_transferring);

                    // NOW send the book data
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

    private void startDiscovery() {
        myLogI("Starting discovery");
        isActive = true;
        updateUI();

        nearbyHelper.startDiscovery(new NearbyConnectionsHelper.DiscoveryCallback() {
            @Override
            public void onDiscoveryStarted() {
                runOnUiThread(() -> {
                    myLog("Discovery started");
                    tvStatus.setText(R.string.nearby_share_searching);
                });
            }

            @Override
            public void onEndpointFound(String endpointId, String endpointName) {
                runOnUiThread(() -> {
                    myLog("Found device: " + endpointName);
                    tvStatus.setText("Found: " + endpointName + ". Connecting...");

                    // Automatically request connection to first found device
                    String localDeviceName = Build.MODEL;
                    nearbyHelper.requestConnection(endpointId, localDeviceName);
                });
            }

            @Override
            public void onEndpointLost(String endpointId) {
                runOnUiThread(() -> {
                    myLog("Lost device: " + endpointId);
                });
            }

            @Override
            public void onConnectionInitiated(String endpointId, String endpointName) {
                runOnUiThread(() -> {
                    myLog("Connection initiated with: " + endpointName);
                    tvStatus.setText("Connecting to " + endpointName + "...");

                    // Initialize receiver helper
                    receiverHelper = new NearbyShareReceiverHelper(NearbyShareActivity.this);
                    receiverHelper.setProgressCallback(new NearbyShareReceiverHelper.ProgressCallback() {
                        @Override
                        public void onProgress(String message, int currentFile, int totalFiles,
                                long bytesReceived, long totalBytes) {
                            runOnUiThread(() -> {
                                String sizeInfo = formatBytes(bytesReceived) + " / " +
                                        formatBytes(totalBytes);
                                tvStatus.setText(message + "\n" +
                                        "File " + currentFile + "/" + totalFiles + "  " + sizeInfo);

                                int progress = totalBytes > 0 ? (int) ((bytesReceived * 100.0) / totalBytes) : 0;
                                progressBar.setProgress(progress);
                            });
                        }

                        @Override
                        public void onCoverReceived(String path) {
                            runOnUiThread(() -> {
                                if (path != null && !path.isEmpty()) {
                                    ivCoverPreview.setImageURI(android.net.Uri.fromFile(new java.io.File(path)));
                                }
                            });
                        }

                        @Override
                        public void onComplete(String bookName) {
                            runOnUiThread(() -> {
                                myLog("Transfer complete: " + bookName);
                                tvStatus.setText("Import complete: " + bookName);
                                myToast("Book imported successfully: " + bookName);
                                finish();
                            });
                        }

                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> {
                                myLogE("Receive error: " + error);
                                tvStatus.setText("Error: " + error);
                                myToastE("Import failed: " + error);
                            });
                        }
                    });

                    // Accept the connection to receive files
                    nearbyHelper.acceptConnection(endpointId, new NearbyConnectionsHelper.PayloadCallback() {
                        @Override
                        public void onPayloadSent(long payloadId) {
                            // Not used in receiver mode
                        }

                        @Override
                        public void onPayloadTransferUpdate(long payloadId, int bytesTransferred, int totalBytes) {
                            // Progress handled by receiver helper
                        }

                        @Override
                        public void onPayloadReceived(long payloadId, byte[] data) {
                            handleReceivedPayload(payloadId, data);
                        }

                        @Override
                        public void onFilePayloadReceived(long payloadId, Payload filePayload) {
                            if (receiverHelper == null) {
                                myLogE("Receiver helper not initialized for file payload");
                                return;
                            }

                            processingExecutor.execute(() -> {
                                try {
                                    // Try getting PFD directly first (preferred for received files)
                                    android.os.ParcelFileDescriptor pfd = filePayload.asFile().asParcelFileDescriptor();

                                    // Fallback to Java File if PFD is null
                                    if (pfd == null) {
                                        java.io.File file = filePayload.asFile().asJavaFile();
                                        if (file != null) {
                                            pfd = android.os.ParcelFileDescriptor.open(file,
                                                    android.os.ParcelFileDescriptor.MODE_READ_ONLY);
                                        }
                                    }

                                    if (pfd != null) {
                                        // Determine if it's cover or audio based on whether metadata has cover
                                        if (!receiverHelper.saveCoverFile(pfd)) {
                                            receiverHelper.saveAudioFile(pfd, filePayload.asFile().getSize());
                                        }
                                        pfd.close();
                                    } else {
                                        myLogE("Failed to get file descriptor for payload " + payloadId);
                                    }
                                } catch (Exception e) {
                                    myLogEE(e, "Failed to process file payload");
                                }
                            });
                        }

                        @Override
                        public void onTransferComplete() {
                            runOnUiThread(() -> {
                                myLog("Receive complete");
                                tvStatus.setText(R.string.nearby_share_complete);
                                myToast(getString(R.string.nearby_share_complete));
                                stopSharing();
                            });
                        }

                        @Override
                        public void onTransferFailed(String error) {
                            runOnUiThread(() -> {
                                myLogE("Receive failed: " + error);
                                tvStatus.setText(R.string.nearby_share_failed);
                                myToastE(getString(R.string.nearby_share_failed) + ": " + error);
                                stopSharing();
                            });
                        }
                    });
                });
            }

            @Override
            public void onConnectionEstablished(String endpointId) {
                runOnUiThread(() -> {
                    myLog("Connection established, ready to receive");
                    tvStatus.setText(R.string.nearby_share_receiving);
                });
            }

            @Override
            public void onConnectionFailed(String error) {
                runOnUiThread(() -> {
                    myLogE("Discovery connection failed: " + error);
                    tvStatus.setText(R.string.nearby_share_failed);
                    myToastE(getString(R.string.nearby_share_failed));
                    stopSharing();
                });
            }
        });
    }

    /**
     * Handle received payload (metadata, cover, or audio file)
     */
    private void handleReceivedPayload(long payloadId, byte[] data) {
        if (receiverHelper == null) {
            myLogE("Receiver helper not initialized");
            return;
        }

        if (!receiverHelper.hasMetadata()) {
            // First payload should be metadata
            myLogI("Processing metadata payload");
            if (receiverHelper.parseMetadata(data)) {
                runOnUiThread(() -> {
                    tvBookInfo.setText("Receiving: " + receiverHelper.getBookName());
                    tvStatus.setText("Preparing to receive " + receiverHelper.getTotalFileCount() + " files...");

                    // Update preview title
                    tvBookTitlePreview.setText(receiverHelper.getBookName());
                });

                // Create book folder in background
                processingExecutor.execute(() -> {
                    if (!receiverHelper.createBookFolder()) {
                        runOnUiThread(() -> {
                            myToastE("Import failed: Folder may already exist. Check logs.");
                            stopSharing();
                        });
                    }
                });
            } else {
                runOnUiThread(() -> {
                    myToastE("Failed to parse metadata");
                    stopSharing();
                });
            }
        } else {
            // Not a metadata payload, ignore byte[] payloads after metadata
            // Files come as ParcelFileDescriptor, handled in
            // NearbyConnectionsHelper.PayloadCallbackImpl
            myLogI("Ignoring non-metadata byte payload: " + payloadId);
        }
    }

    private void stopSharing() {
        myLogI("Stopping nearby sharing/discovery");
        isActive = false;
        if (isSendMode) {
            nearbyHelper.stopAdvertising();
        } else {
            nearbyHelper.stopDiscovery();
        }
        updateUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (nearbyHelper != null) {
            nearbyHelper.cleanup();
        }
        if (processingExecutor != null) {
            processingExecutor.shutdown();
        }
    }

    /**
     * Format bytes into human-readable string
     */
    private static String formatBytes(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format(java.util.Locale.US, "%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
