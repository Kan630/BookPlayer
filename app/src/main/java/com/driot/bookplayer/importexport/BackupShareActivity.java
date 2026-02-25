package com.driot.bookplayer.importexport;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.driot.bookplayer.R;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.quickshare.NearbyConnectionsHelper;
import com.driot.bookplayer.utils.log.BaseActivity;
import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class BackupShareActivity extends BaseActivity {

    public static final String EXTRA_MODE = "extra_mode";
    public static final String RESULT_JSON = "result_json";

    // Use static payload to avoid TransactionTooLargeException
    public static String sBackupPayload;

    public static final int MODE_SEND = 0;
    public static final int MODE_RECEIVE = 1;

    private static final int PERMISSION_REQUEST_CODE = 1002;
    private static final String MSG_TYPE_BACKUP = "BACKUP_JSON";

    @Inject
    NearbyConnectionsHelper nearbyHelper;

    private int mode = MODE_SEND;
    private String backupJson;

    private TextView tvTitle, tvExplain, tvStatus, tvConnected, tvProgressInfo;
    private MaterialButton btnAction, btnClose;
    private ProgressBar progressBar;

    private boolean isActive = false;
    private String connectedEndpointId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup_share);
        InsetHelper.apply(this);

        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_SEND);
        backupJson = sBackupPayload;

        initViews();
        setupUI();
    }

    private void initViews() {
        tvTitle = findViewById(R.id.tv_title);
        tvExplain = findViewById(R.id.tv_explain);
        tvStatus = findViewById(R.id.tv_status_main);
        tvConnected = findViewById(R.id.tv_connected_endpoint);
        tvProgressInfo = findViewById(R.id.tv_progress_info);
        btnAction = findViewById(R.id.btn_action_share);
        btnClose = findViewById(R.id.btn_close);
        progressBar = findViewById(R.id.progress_bar);

        btnAction.setOnClickListener(v -> toggleNearby());
        btnClose.setOnClickListener(v -> handleClose());
    }

    private void setupUI() {
        if (mode == MODE_SEND) {
            tvTitle.setText(getString(R.string.backup_share_send_title));
            tvExplain.setText(getString(R.string.backup_share_send_explain));
            btnAction.setText(getString(R.string.backup_share_send_btn));
        } else {
            tvTitle.setText(getString(R.string.backup_share_receive_title));
            tvExplain.setText(getString(R.string.backup_share_receive_explain));
            btnAction.setText(getString(R.string.backup_share_receive_btn));
        }
    }

    private void toggleNearby() {
        if (isActive) {
            stopNearby();
        } else {
            checkPermissionsAndStart();
        }
    }

    private void checkPermissionsAndStart() {
        List<String> missing = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!checkPermission(Manifest.permission.BLUETOOTH_ADVERTISE))
                missing.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            if (!checkPermission(Manifest.permission.BLUETOOTH_CONNECT))
                missing.add(Manifest.permission.BLUETOOTH_CONNECT);
            if (!checkPermission(Manifest.permission.BLUETOOTH_SCAN))
                missing.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!checkPermission(Manifest.permission.NEARBY_WIFI_DEVICES))
                missing.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }
        if (!checkPermission(Manifest.permission.ACCESS_FINE_LOCATION))
            missing.add(Manifest.permission.ACCESS_FINE_LOCATION);

        if (missing.isEmpty()) {
            startNearby();
        } else {
            ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    private boolean checkPermission(String p) {
        return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean all = true;
            for (int r : grantResults)
                if (r != PackageManager.PERMISSION_GRANTED)
                    all = false;
            if (all)
                startNearby();
            else
                myToast(getString(R.string.backup_share_permissions_required));
        }
    }

    private void startNearby() {
        isActive = true;
        btnAction.setText(getString(R.string.backup_share_btn_stop));
        tvStatus.setText(getString(R.string.backup_share_starting));
        String deviceName = getDeviceName();

        if (mode == MODE_SEND) {
            nearbyHelper.startAdvertising(deviceName, new NearbyConnectionsHelper.AdvertisingCallback() {
                @Override
                public void onAdvertisingStarted() {
                    runOnUiThread(() -> tvStatus.setText(getString(R.string.backup_share_ready_as, deviceName)));
                }

                @Override
                public void onConnectionInitiated(String id, String name) {
                    nearbyHelper.acceptConnection(id, createPayloadCallback());
                }

                @Override
                public void onConnectionEstablished(String id) {
                    connectedEndpointId = id;
                    runOnUiThread(() -> {
                        tvStatus.setText(getString(R.string.backup_share_connected_to, id));
                        tvConnected.setVisibility(View.VISIBLE);
                        tvConnected.setText(getString(R.string.backup_share_sending));
                        nearbyHelper.sendControlMessage(id, MSG_TYPE_BACKUP, backupJson);
                    });
                }

                @Override
                public void onConnectionFailed(String err) {
                    stopNearby();
                }
            });
        } else {
            nearbyHelper.startDiscovery(new NearbyConnectionsHelper.DiscoveryCallback() {
                @Override
                public void onDiscoveryStarted() {
                    runOnUiThread(() -> tvStatus.setText(getString(R.string.backup_share_looking)));
                }

                @Override
                public void onEndpointFound(String id, String name) {
                    nearbyHelper.requestConnection(id, deviceName);
                }

                @Override
                public void onEndpointLost(String id) {
                }

                @Override
                public void onConnectionInitiated(String id, String name) {
                    nearbyHelper.acceptConnection(id, createPayloadCallback());
                }

                @Override
                public void onConnectionEstablished(String id) {
                    connectedEndpointId = id;
                    runOnUiThread(() -> {
                        tvStatus.setText(getString(R.string.backup_share_connected));
                        tvConnected.setVisibility(View.VISIBLE);
                        tvConnected.setText(getString(R.string.backup_share_waiting));
                    });
                }

                @Override
                public void onConnectionFailed(String err) {
                    stopNearby();
                }
            });
        }
    }

    private void stopNearby() {
        isActive = false;
        nearbyHelper.cleanup();
        runOnUiThread(() -> {
            setupUI();
            tvStatus.setText(getString(R.string.backup_share_ready));
            tvConnected.setVisibility(View.GONE);
        });
    }

    private NearbyConnectionsHelper.PayloadCallback createPayloadCallback() {
        return new NearbyConnectionsHelper.PayloadCallback() {
            @Override
            public void onPayloadSent(long id) {
                if (mode == MODE_SEND) {
                    runOnUiThread(() -> {
                        myToast(getString(R.string.backup_share_sent));
                        finish();
                    });
                }
            }

            @Override
            public void onPayloadTransferUpdate(long id, int b, int t) {
            }

            @Override
            public void onPayloadReceived(long id, byte[] data) {
                try {
                    String jsonStr = new String(data, StandardCharsets.UTF_8);
                    JSONObject obj = new JSONObject(jsonStr);
                    if (MSG_TYPE_BACKUP.equals(obj.optString("type"))) {
                        String backupContent = obj.optString("message");
                        runOnUiThread(() -> {
                            sBackupPayload = backupContent; // Store for return
                            setResult(RESULT_OK);
                            finish();
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFilePayloadReceived(long id, com.google.android.gms.nearby.connection.Payload p) {
            }

            @Override
            public void onTransferComplete() {
            }

            @Override
            public void onTransferFailed(String err) {
                stopNearby();
            }
        };
    }

    private void handleClose() {
        stopNearby();
        finish();
    }

    private String getDeviceName() {
        String model = Build.MODEL;
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null && ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                String name = adapter.getName();
                if (name != null && !name.isEmpty())
                    return name;
            }
        } catch (Exception ignored) {
        }
        return model;
    }

    @Override
    protected void onDestroy() {
        nearbyHelper.cleanup();
        sBackupPayload = null; // Clear to prevent memory leaks
        super.onDestroy();
    }
}
