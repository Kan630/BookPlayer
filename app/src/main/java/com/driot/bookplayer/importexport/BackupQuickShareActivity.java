package com.driot.bookplayer.importexport;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.driot.bookplayer.R;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.quickshare.NearbyConnectionsHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.BaseActivity;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Sends or receives a single arbitrary file (a Full Backup zip) over Nearby Connections - the
 * generic counterpart to NearbyShareFragment (which is book/Folder-specific: cover preview,
 * per-track metadata, imports the result as a library folder). This one has no book domain
 * knowledge at all: on receive, the file just lands in the cache dir and this hands the caller
 * its path back to do whatever it wants with (here: feed it into the same restore-preview flow
 * a manually-picked zip already goes through).
 * <p>
 * Reuses NearbyConnectionsHelper's connection/payload plumbing (same Hilt-singleton
 * ConnectionsClient as book sharing) via its new sendSingleFile() method plus the already-generic
 * PayloadCallback - see that method's own doc for why no new receive-side method was needed.
 */
@AndroidEntryPoint
public class BackupQuickShareActivity extends BaseActivity {

    public static final String EXTRA_MODE = "extra_mode";
    public static final int MODE_SEND = 0;
    public static final int MODE_RECEIVE = 1;
    public static final String EXTRA_FILE_PATH = "extra_file_path"; // send mode: local file to send
    public static final String EXTRA_DISPLAY_NAME = "extra_display_name"; // send mode: name to show/send
    public static final String EXTRA_RECEIVED_FILE_PATH = "extra_received_file_path"; // result, receive mode

    private static final int PERMISSION_REQUEST_CODE = 1101;
    private static final String CONTROL_TYPE_SUCCESS = "SUCCESS";
    private static final String CONTROL_TYPE_ERROR = "ERROR";
    private static final String CONTROL_TYPE_CANCEL = "CANCEL";

    @Inject
    NearbyConnectionsHelper nearbyHelper;

    private int mode;
    private String filePath;
    private String displayName;

    private TextView tvTitle, tvExplain, tvFileInfo, tvConnectedEndpoint, tvStatus;
    private MaterialButton btnStart;
    private ProgressBar progressBar;

    private volatile boolean isActive = false;
    private String connectedEndpointId;
    private final Map<String, String> endpointNames = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Receive-side: filled in once the small metadata payload arrives, before the file itself.
    private String expectedFileName;
    private long expectedFileSize = -1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup_quick_share);
        InsetHelper.apply(this);

        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_SEND);
        filePath = getIntent().getStringExtra(EXTRA_FILE_PATH);
        displayName = getIntent().getStringExtra(EXTRA_DISPLAY_NAME);

        if (mode == MODE_SEND && (filePath == null || !new File(filePath).exists())) {
            myLogEE(null, "BackupQuickShareActivity: MODE_SEND with no valid file");
            finish();
            return;
        }

        tvTitle = findViewById(R.id.title);
        tvExplain = findViewById(R.id.tv_quick_share_explain);
        tvFileInfo = findViewById(R.id.tv_file_info);
        tvConnectedEndpoint = findViewById(R.id.tv_connected_endpoint);
        tvStatus = findViewById(R.id.tv_status);
        btnStart = findViewById(R.id.btn_start_sharing);
        progressBar = findViewById(R.id.progress_bar);

        boolean isSend = mode == MODE_SEND;
        tvTitle.setText(isSend ? R.string.backup_quick_share_title_send : R.string.backup_quick_share_title_receive);
        tvExplain.setText(isSend ? R.string.backup_quick_share_explain_send : R.string.backup_quick_share_explain_receive);
        btnStart.setText(isSend ? R.string.nearby_share_start_advertising : R.string.nearby_share_start_discovering);
        tvStatus.setText(R.string.nearby_share_ready);

        if (isSend) {
            File f = new File(filePath);
            tvFileInfo.setVisibility(View.VISIBLE);
            tvFileInfo.setText(getString(R.string.backup_quick_share_file_info, displayName,
                    Tonio.getReadableSize(f.length())));
        }

        btnStart.setOnClickListener(v -> toggleSharing());
    }

    private void toggleSharing() {
        if (isActive) {
            myLogI("user clicks button : STOP");
            if (connectedEndpointId != null) {
                nearbyHelper.sendControlMessage(connectedEndpointId, CONTROL_TYPE_CANCEL,
                        getString(R.string.nearby_share_transfer_cancelled_other));
            }
            handler.postDelayed(() -> stopAll(getString(R.string.nearby_share_transfer_cancelled)), 200);
        } else {
            myLogI("user clicks button : START");
            checkPermissionsAndStart();
        }
    }

    private void startProcess() {
        isActive = true;
        connectedEndpointId = null;
        tvConnectedEndpoint.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        btnStart.setText(mode == MODE_SEND ? R.string.nearby_share_stop_advertising
                : R.string.nearby_share_stop_discovering);

        if (mode == MODE_SEND) {
            nearbyHelper.startAdvertising(getDeviceName(), advertisingCallback);
        } else {
            nearbyHelper.startDiscovery(discoveryCallback);
        }
    }

    private final NearbyConnectionsHelper.AdvertisingCallback advertisingCallback = new NearbyConnectionsHelper.AdvertisingCallback() {
        @Override
        public void onAdvertisingStarted() {
            runOnUiThread(() -> tvStatus.setText(R.string.nearby_share_advertising_started));
        }

        @Override
        public void onConnectionInitiated(String endpointId, String endpointName) {
            endpointNames.put(endpointId, endpointName);
            runOnUiThread(() -> tvStatus.setText(getString(R.string.nearby_share_connection_initiated, endpointName)));
            nearbyHelper.acceptConnection(endpointId, sendPayloadCallback);
        }

        @Override
        public void onConnectionEstablished(String endpointId) {
            connectedEndpointId = endpointId;
            String name = endpointNames.get(endpointId);
            runOnUiThread(() -> {
                tvConnectedEndpoint.setVisibility(View.VISIBLE);
                tvConnectedEndpoint.setText(getString(R.string.nearby_share_connected_to,
                        name != null ? name : endpointId));
                tvStatus.setText(R.string.nearby_share_connection_established);
            });
            nearbyHelper.sendSingleFile(endpointId, new File(filePath), displayName);
        }

        @Override
        public void onConnectionFailed(String errorMessage) {
            runOnUiThread(() -> stopAll(errorMessage));
        }
    };

    private final NearbyConnectionsHelper.PayloadCallback sendPayloadCallback = new NearbyConnectionsHelper.PayloadCallback() {
        @Override
        public void onPayloadSent(long payloadId) {
        }

        @Override
        public void onPayloadTransferUpdate(long payloadId, int bytesTransferred, int totalBytes) {
            if (nearbyHelper.getPayloadIndex(payloadId) <= 0) {
                return; // the small metadata payload, not the file itself
            }
            long totalSent = nearbyHelper.getTotalBytesSentOverall() + nearbyHelper.getActiveBytesTransferred();
            long totalSize = nearbyHelper.getPayloadsTotalSize();
            int percent = totalSize > 0 ? (int) Math.min(100, (totalSent * 100) / totalSize) : 0;
            runOnUiThread(() -> {
                progressBar.setProgress(percent);
                tvStatus.setText(getString(R.string.backup_quick_share_progress, percent));
            });
        }

        @Override
        public void onPayloadReceived(long payloadId, byte[] data) {
            handleControlMessage(data);
        }

        @Override
        public void onFilePayloadReceived(long payloadId, Payload filePayload) {
            // Not expected for the sender.
        }

        @Override
        public void onTransferComplete() {
            runOnUiThread(() -> tvStatus.setText(R.string.nearby_share_waiting_confirmation));
        }

        @Override
        public void onTransferFailed(String errorMsg) {
            // stopAll() disconnects, and that disconnect comes back here: once this screen has
            // stopped on purpose (success, cancel, error), it must not overwrite the outcome
            // with "connection lost".
            if (!isActive) {
                return;
            }
            runOnUiThread(() -> stopAll(errorMsg));
        }
    };

    private final NearbyConnectionsHelper.DiscoveryCallback discoveryCallback = new NearbyConnectionsHelper.DiscoveryCallback() {
        @Override
        public void onDiscoveryStarted() {
            runOnUiThread(() -> tvStatus.setText(R.string.nearby_share_discovery_started));
        }

        @Override
        public void onEndpointFound(String endpointId, String endpointName) {
            endpointNames.put(endpointId, endpointName);
            runOnUiThread(() -> tvStatus.setText(getString(R.string.nearby_share_found_device, endpointName) + ". "
                    + getString(R.string.nearby_share_connecting)));
            nearbyHelper.requestConnection(endpointId, getDeviceName());
        }

        @Override
        public void onEndpointLost(String endpointId) {
        }

        @Override
        public void onConnectionInitiated(String endpointId, String endpointName) {
            endpointNames.put(endpointId, endpointName);
            runOnUiThread(() -> tvStatus.setText(getString(R.string.nearby_share_connection_initiated, endpointName)));
            nearbyHelper.acceptConnection(endpointId, receivePayloadCallback);
        }

        @Override
        public void onConnectionEstablished(String endpointId) {
            connectedEndpointId = endpointId;
            String name = endpointNames.get(endpointId);
            runOnUiThread(() -> {
                tvConnectedEndpoint.setVisibility(View.VISIBLE);
                tvConnectedEndpoint.setText(getString(R.string.nearby_share_connected_to,
                        name != null ? name : endpointId));
                tvStatus.setText(R.string.nearby_share_connected_waiting);
            });
        }

        @Override
        public void onConnectionFailed(String errorMessage) {
            runOnUiThread(() -> stopAll(errorMessage));
        }
    };

    private final NearbyConnectionsHelper.PayloadCallback receivePayloadCallback = new NearbyConnectionsHelper.PayloadCallback() {
        @Override
        public void onPayloadSent(long payloadId) {
        }

        @Override
        public void onPayloadTransferUpdate(long payloadId, int bytesTransferred, int totalBytes) {
            if (totalBytes <= 0) {
                return;
            }
            int percent = (int) Math.min(100, (bytesTransferred * 100L) / totalBytes);
            runOnUiThread(() -> {
                progressBar.setProgress(percent);
                tvStatus.setText(getString(R.string.backup_quick_share_progress, percent));
            });
        }

        @Override
        public void onPayloadReceived(long payloadId, byte[] data) {
            if (handleControlMessage(data)) {
                return;
            }
            try {
                JSONObject obj = new JSONObject(new String(data));
                if ("SINGLE_FILE".equals(obj.optString("type"))) {
                    expectedFileName = obj.optString("name", "backup.zip");
                    expectedFileSize = obj.optLong("size", -1);
                    runOnUiThread(() -> {
                        tvFileInfo.setVisibility(View.VISIBLE);
                        tvFileInfo.setText(getString(R.string.backup_quick_share_file_info, expectedFileName,
                                Tonio.getReadableSize(Math.max(0, expectedFileSize))));
                    });
                }
            } catch (Exception e) {
                myLogEE(e, "receivePayloadCallback: could not parse metadata");
            }
        }

        @Override
        public void onFilePayloadReceived(long payloadId, Payload filePayload) {
            new Thread(() -> {
                String name = expectedFileName != null ? expectedFileName : "backup.zip";
                File destDir = new File(getCacheDir(), "quick_share_received");
                if (!destDir.exists()) {
                    destDir.mkdirs();
                }
                File dest = new File(destDir, name);
                try {
                    ParcelFileDescriptor pfd = filePayload.asFile() != null
                            ? filePayload.asFile().asParcelFileDescriptor()
                            : null;
                    if (pfd == null && filePayload.asFile() != null) {
                        File src = filePayload.asFile().asJavaFile();
                        if (src != null) {
                            pfd = ParcelFileDescriptor.open(src, ParcelFileDescriptor.MODE_READ_ONLY);
                        }
                    }
                    if (pfd == null) {
                        throw new java.io.IOException("no readable file payload");
                    }
                    try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
                            FileOutputStream out = new FileOutputStream(dest)) {
                        byte[] buf = new byte[64 * 1024];
                        int len;
                        while ((len = in.read(buf)) > 0) {
                            out.write(buf, 0, len);
                        }
                    }
                    if (connectedEndpointId != null) {
                        nearbyHelper.sendControlMessage(connectedEndpointId, CONTROL_TYPE_SUCCESS,
                                getString(R.string.nearby_share_complete));
                    }
                    runOnUiThread(() -> finishWithReceivedFile(dest));
                } catch (Exception e) {
                    myLogEE(e, "onFilePayloadReceived: failed to save received file");
                    if (connectedEndpointId != null) {
                        nearbyHelper.sendControlMessage(connectedEndpointId, CONTROL_TYPE_ERROR, e.getMessage());
                    }
                    runOnUiThread(() -> stopAll(getString(R.string.backup_quick_share_receive_failed, e.getMessage())));
                }
            }).start();
        }

        @Override
        public void onTransferComplete() {
        }

        @Override
        public void onTransferFailed(String errorMsg) {
            if (!isActive) {
                return; // already stopped on purpose - see the sender's callback above
            }
            runOnUiThread(() -> stopAll(errorMsg));
        }
    };

    /** SUCCESS/ERROR/CANCEL control messages, same small JSON protocol book sharing already
     *  uses. Returns true if this payload WAS a control message (whether or not it mattered),
     *  so the caller doesn't also try to parse it as file metadata. */
    private boolean handleControlMessage(byte[] data) {
        try {
            String jsonStr = new String(data);
            if (!jsonStr.startsWith("{")) {
                return false;
            }
            JSONObject obj = new JSONObject(jsonStr);
            String type = obj.optString("type");
            if (CONTROL_TYPE_ERROR.equals(type)) {
                String msg = obj.optString("message", "Unknown error");
                runOnUiThread(() -> stopAll(msg));
                return true;
            } else if (CONTROL_TYPE_SUCCESS.equals(type)) {
                String msg = obj.optString("message", getString(R.string.nearby_share_complete));
                runOnUiThread(() -> stopAll(msg, false));
                return true;
            } else if (CONTROL_TYPE_CANCEL.equals(type)) {
                String msg = obj.optString("message", getString(R.string.nearby_share_transfer_cancelled));
                runOnUiThread(() -> stopAll(msg, false));
                return true;
            }
            // "SINGLE_FILE" isn't a control message - let the caller parse it as file metadata.
            // Anything else JSON-shaped but unrecognized is swallowed rather than misread as one.
            return !"SINGLE_FILE".equals(type);
        } catch (Exception ignored) {
            return false; // not JSON at all - definitely not a control message
        }
    }

    private void finishWithReceivedFile(File file) {
        Intent result = new Intent();
        result.putExtra(EXTRA_RECEIVED_FILE_PATH, file.getAbsolutePath());
        setResult(RESULT_OK, result);
        finish();
    }

    private void stopAll(String message) {
        stopAll(message, true);
    }

    private void stopAll(String message, boolean isError) {
        isActive = false;
        nearbyHelper.stopAdvertising();
        nearbyHelper.stopDiscovery();
        if (connectedEndpointId != null) {
            nearbyHelper.disconnect(connectedEndpointId);
            connectedEndpointId = null;
        }
        progressBar.setVisibility(View.GONE);
        btnStart.setText(
                mode == MODE_SEND ? R.string.nearby_share_start_advertising : R.string.nearby_share_start_discovering);
        if (message != null) {
            tvStatus.setText(isError ? getString(R.string.nearby_share_stopped_with_error, message) : message);
        } else {
            tvStatus.setText(R.string.nearby_share_ready);
        }
        // Note: on the receive side, a successful transfer finishes this Activity (with a
        // result) from finishWithReceivedFile() directly, before this method would even run for
        // that same SUCCESS control message - this method only actually executes for failures/
        // cancellation, never as a no-op after success.
    }

    // --- Permissions (mirrors NearbyShareFragment.checkPermissionsAndStart(), Activity-flavored) ---

    private void checkPermissionsAndStart() {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && !checkPermission(Manifest.permission.NEARBY_WIFI_DEVICES)) {
                missing.add(Manifest.permission.NEARBY_WIFI_DEVICES);
            }
            if (missing.isEmpty()) {
                startProcess();
            } else {
                ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            }
        } else if (Build.VERSION.SDK_INT >= 29) {
            if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                startProcess();
            } else {
                ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                        PERMISSION_REQUEST_CODE);
            }
        } else {
            if (checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                startProcess();
            } else {
                ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.ACCESS_COARSE_LOCATION },
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
        if (requestCode != PERMISSION_REQUEST_CODE) {
            return;
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

    private String getDeviceName() {
        String model = Build.MODEL;
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter != null
                    && ActivityCompat.checkSelfPermission(this,
                            Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                String name = bluetoothAdapter.getName();
                if (name != null && !name.isEmpty()) {
                    return name.equals(model) ? name : name + " (" + model + ")";
                }
            }
        } catch (Exception e) {
            myLogW("Could not get Bluetooth name: " + e.getMessage());
        }
        return model;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isActive) {
            nearbyHelper.cleanup();
        }
    }
}
