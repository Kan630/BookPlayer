package com.driot.bookplayer.activities;


import static com.driot.tonylib.KanLogger.myLogE;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.driot.bookplayer.R;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import java.io.File;
import java.io.FileNotFoundException;

public class SynchroActivity extends AppCompatActivity {
    private static final String TAG = "SynchroActivity";
    private static final String SERVICE_ID = "com.example.bookplayer.SERVICE_ID";
    private static final int REQUEST_PERMISSIONS_CODE = 1;
    private ConnectionsClient connectionsClient;
    private String otherEndpointId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_synchro);

        connectionsClient = Nearby.getConnectionsClient(this);

        Button startAdvertisingButton = findViewById(R.id.startAdvertisingButton);
        Button startDiscoveryButton = findViewById(R.id.startDiscoveryButton);

        startAdvertisingButton.setOnClickListener(v -> startAdvertising());
        startDiscoveryButton.setOnClickListener(v -> startDiscovery());

        requestPermissions();
    }

    private void requestPermissions() {
        String[] requiredPermissions = new String[]{
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.INTERNET
        };

        boolean allPermissionsGranted = true;
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }

        if (!allPermissionsGranted) {
            ActivityCompat.requestPermissions(this, requiredPermissions, REQUEST_PERMISSIONS_CODE);
        }
    }

    private void startAdvertising() {
        connectionsClient.startAdvertising(
                        "DeviceName",
                        SERVICE_ID,
                        connectionLifecycleCallback,
                        new AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
                ).addOnSuccessListener(unused -> Log.d(TAG, "Advertising started"))
                .addOnFailureListener(e -> Log.e(TAG, "Advertising failed", e));
    }

    private void startDiscovery() {
        connectionsClient.startDiscovery(
                        SERVICE_ID,
                        endpointDiscoveryCallback,
                        new DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
                ).addOnSuccessListener(unused -> Log.d(TAG, "Discovery started"))
                .addOnFailureListener(e -> Log.e(TAG, "Discovery failed", e));
    }

    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo connectionInfo) {
            connectionsClient.acceptConnection(endpointId, payloadCallback);
        }

        @Override
        public void onConnectionResult(@NonNull String endpointId, @NonNull ConnectionResolution result) {
            if (result.getStatus().isSuccess()) {
                otherEndpointId = endpointId;
                Log.d(TAG, "Connected to endpoint: " + endpointId);

                // Send a file after successful connection
                String filePath = getFilesDir() + "/unzipped/file_01/file_example_MP3_700KB.mp3";
                File fileToSend = new File(filePath);
                Payload filePayload = null;
                try {
                    filePayload = Payload.fromFile(fileToSend);
                } catch (FileNotFoundException e) {
                    myLogE("File not found : " + filePath);
                    throw new RuntimeException(e);
                }
                connectionsClient.sendPayload(endpointId, filePayload);
            }
        }

        @Override
        public void onDisconnected(@NonNull String endpointId) {
            Log.d(TAG, "Disconnected from endpoint: " + endpointId);
        }
    };

    private final EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(@NonNull String endpointId, @NonNull DiscoveredEndpointInfo info) {
            connectionsClient.requestConnection("DeviceName", endpointId, connectionLifecycleCallback);
        }

        @Override
        public void onEndpointLost(@NonNull String endpointId) {
            Log.d(TAG, "Endpoint lost: " + endpointId);
        }
    };

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            if (payload.getType() == Payload.Type.FILE) {
                // Handle received file payload
                File receivedFile = payload.asFile().asJavaFile();
                Log.d(TAG, "File received: " + receivedFile.getAbsolutePath());
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId, @NonNull PayloadTransferUpdate update) {
            // Handle transfer updates
            Log.d(TAG, "Payload transfer update: " + update.getStatus());
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "Permission not granted!");
                    return;
                }
            }
        }
    }
}
