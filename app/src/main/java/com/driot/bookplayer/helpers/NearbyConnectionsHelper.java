package com.driot.bookplayer.helpers;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.ZikFile;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class for managing Nearby Connections API
 * Handles device discovery, connections, and file transfers for book sharing
 */
public class NearbyConnectionsHelper {

    private static final String SERVICE_ID = "com.driot.bookplayer.nearby";
    private static final Strategy STRATEGY = Strategy.P2P_POINT_TO_POINT;

    private final Context context;
    private final ConnectionsClient connectionsClient;

    // Callbacks
    private AdvertisingCallback advertisingCallback;
    private DiscoveryCallback discoveryCallback;
    private PayloadCallback payloadCallback;

    // Connection tracking
    private final Map<Long, Integer> payloadProgress = new HashMap<>();
    private final Map<Long, Payload> receivedFilePayloads = new HashMap<>();
    private final Map<Long, String> payloadNames = new HashMap<>();
    private final Map<Long, Integer> payloadLastLoggedStep = new HashMap<>();
    private String connectedEndpointId;

    public NearbyConnectionsHelper(Context context) {
        this.context = context;
        this.connectionsClient = Nearby.getConnectionsClient(context);
    }

    // Callback interfaces
    public interface AdvertisingCallback {
        void onAdvertisingStarted();

        void onConnectionInitiated(String endpointId, String endpointName);

        void onConnectionEstablished(String endpointId);

        void onConnectionFailed(String error);
    }

    public interface DiscoveryCallback {
        void onDiscoveryStarted();

        void onEndpointFound(String endpointId, String endpointName);

        void onEndpointLost(String endpointId);

        void onConnectionInitiated(String endpointId, String endpointName);

        void onConnectionEstablished(String endpointId);

        void onConnectionFailed(String error);
    }

    public interface PayloadCallback {
        void onPayloadSent(long payloadId);

        void onPayloadTransferUpdate(long payloadId, int bytesTransferred, int totalBytes);

        void onPayloadReceived(long payloadId, byte[] data);

        void onFilePayloadReceived(long payloadId, Payload filePayload);

        void onTransferComplete();

        void onTransferFailed(String error);
    }

    /**
     * Start advertising this device for nearby connections
     */
    public void startAdvertising(String deviceName, AdvertisingCallback callback) {
        this.advertisingCallback = callback;

        AdvertisingOptions options = new AdvertisingOptions.Builder()
                .setStrategy(STRATEGY)
                .build();

        connectionsClient.startAdvertising(
                deviceName,
                SERVICE_ID,
                connectionLifecycleCallback,
                options).addOnSuccessListener(unused -> {
                    if (advertisingCallback != null) {
                        advertisingCallback.onAdvertisingStarted();
                    }
                }).addOnFailureListener(e -> {
                    if (advertisingCallback != null) {
                        advertisingCallback.onConnectionFailed("Advertising failed: " + e.getMessage());
                    }
                });
    }

    /**
     * Start discovering nearby devices
     */
    public void startDiscovery(DiscoveryCallback callback) {
        this.discoveryCallback = callback;

        DiscoveryOptions options = new DiscoveryOptions.Builder()
                .setStrategy(STRATEGY)
                .build();

        connectionsClient.startDiscovery(
                SERVICE_ID,
                endpointDiscoveryCallback,
                options).addOnSuccessListener(unused -> {
                    if (discoveryCallback != null) {
                        discoveryCallback.onDiscoveryStarted();
                    }
                }).addOnFailureListener(e -> {
                    if (discoveryCallback != null) {
                        discoveryCallback.onConnectionFailed("Discovery failed: " + e.getMessage());
                    }
                });
    }

    /**
     * Request connection to discovered endpoint
     */
    public void requestConnection(String endpointId, String localDeviceName) {
        connectionsClient.requestConnection(
                localDeviceName,
                endpointId,
                connectionLifecycleCallback).addOnFailureListener(e -> {
                    if (discoveryCallback != null) {
                        discoveryCallback.onConnectionFailed("Connection request failed: " + e.getMessage());
                    }
                });
    }

    /**
     * Accept an incoming connection
     */
    public void acceptConnection(String endpointId, PayloadCallback callback) {
        this.payloadCallback = callback;
        this.connectedEndpointId = endpointId;

        connectionsClient.acceptConnection(endpointId, payloadCallbackImpl);
    }

    /**
     * Reject an incoming connection
     */
    public void rejectConnection(String endpointId) {
        connectionsClient.rejectConnection(endpointId);
    }

    /**
     * Send book data to connected endpoint
     */
    public void sendBookData(String endpointId, Folder folder, List<ZikFile> files) {
        myLogI("Sending book from folder: " + folder.getPath());
        payloadNames.clear();
        payloadLastLoggedStep.clear();

        try {
            // Step 1: Send metadata
            JSONObject metadata = createBookMetadata(folder, files);
            Payload metadataPayload = Payload.fromBytes(metadata.toString().getBytes());
            payloadNames.put(metadataPayload.getId(), "Metadata");
            connectionsClient.sendPayload(endpointId, metadataPayload);

            // Step 2: Send cover image if available
            if (folder.image != null && !folder.image.isEmpty()) {
                File coverFile = new File(folder.image);
                if (coverFile.exists()) {
                    try {
                        Payload coverPayload = Payload.fromFile(coverFile);
                        payloadNames.put(coverPayload.getId(), "Cover Image");
                        connectionsClient.sendPayload(endpointId, coverPayload);
                    } catch (Exception e) {
                        // Skip cover if we can't read it
                    }
                }
            }

            // Step 3: Send audio files
            for (ZikFile zikFile : files) {
                String filePath = zikFile.getPath() + "/" + zikFile.getName();
                File audioFile = new File(filePath);

                myLogI("Preparing to send file: " + filePath);

                if (audioFile.exists()) {
                    try {
                        Payload filePayload = Payload.fromFile(audioFile);
                        payloadNames.put(filePayload.getId(), zikFile.getName());
                        connectionsClient.sendPayload(endpointId, filePayload)
                                .addOnFailureListener(e -> myLogE("Failed to send payload: " + e.getMessage()));

                        myLogI("Queued payload: " + filePayload.getId() + " for " + zikFile.getName());
                    } catch (Exception e) {
                        myLogEE(e, "Skipping file " + zikFile.getName());
                    }
                } else {
                    myLogE("File does not exist: " + filePath);
                }
                // If file doesn't exist, skip it
            }

        } catch (Exception e) {
            myLogEE(e, "Error sending book data");
            if (payloadCallback != null) {
                payloadCallback.onTransferFailed("Failed to send book data: " + e.getMessage());
            }
        }
    }

    /**
     * Create book metadata JSON
     */
    private JSONObject createBookMetadata(Folder folder, List<ZikFile> files) throws JSONException {
        JSONObject metadata = new JSONObject();
        metadata.put("bookName", folder.getName());
        metadata.put("fileCount", files.size());
        metadata.put("hasCover", folder.image != null && !folder.image.isEmpty());

        // Calculate total size
        long totalSize = 0;
        JSONArray fileList = new JSONArray();
        for (ZikFile file : files) {
            JSONObject fileInfo = new JSONObject();
            fileInfo.put("name", file.getName());
            fileInfo.put("displayName", file.getDisplayName());
            fileInfo.put("size", file.getSize());
            fileInfo.put("duration", file.getDuration());
            totalSize += file.getSize();
            fileList.put(fileInfo);
        }
        metadata.put("files", fileList);
        metadata.put("totalSize", totalSize);

        return metadata;
    }

    /**
     * Stop advertising
     */
    public void stopAdvertising() {
        connectionsClient.stopAdvertising();
    }

    /**
     * Stop discovery
     */
    public void stopDiscovery() {
        connectionsClient.stopDiscovery();
    }

    /**
     * Disconnect from endpoint
     */
    public void disconnect(String endpointId) {
        connectionsClient.disconnectFromEndpoint(endpointId);
    }

    /**
     * Clean up resources
     */
    public void cleanup() {
        connectionsClient.stopAllEndpoints();
        connectionsClient.stopAdvertising();
        connectionsClient.stopDiscovery();
    }

    // Connection lifecycle callback
    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo connectionInfo) {
            // Notify callback about connection initiation
            if (advertisingCallback != null) {
                advertisingCallback.onConnectionInitiated(endpointId, connectionInfo.getEndpointName());
            }
            if (discoveryCallback != null) {
                discoveryCallback.onConnectionInitiated(endpointId, connectionInfo.getEndpointName());
            }
        }

        @Override
        public void onConnectionResult(@NonNull String endpointId, @NonNull ConnectionResolution result) {
            if (result.getStatus().isSuccess()) {
                // Connection established successfully
                connectedEndpointId = endpointId;
                stopAdvertising();
                stopDiscovery();

                // Notify callbacks that connection is ready for data transfer
                if (advertisingCallback != null) {
                    advertisingCallback.onConnectionEstablished(endpointId);
                }
                if (discoveryCallback != null) {
                    discoveryCallback.onConnectionEstablished(endpointId);
                }
            } else {
                // Connection failed
                String error = "Connection failed with status: " + result.getStatus().getStatusCode();
                if (advertisingCallback != null) {
                    advertisingCallback.onConnectionFailed(error);
                }
                if (discoveryCallback != null) {
                    discoveryCallback.onConnectionFailed(error);
                }
            }
        }

        @Override
        public void onDisconnected(@NonNull String endpointId) {
            // Connection lost
            connectedEndpointId = null;
            if (payloadCallback != null) {
                payloadCallback.onTransferFailed("Connection lost");
            }
        }
    };

    // Endpoint discovery callback
    private final EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(@NonNull String endpointId,
                @NonNull com.google.android.gms.nearby.connection.DiscoveredEndpointInfo info) {
            if (discoveryCallback != null) {
                discoveryCallback.onEndpointFound(endpointId, info.getEndpointName());
            }
        }

        @Override
        public void onEndpointLost(@NonNull String endpointId) {
            if (discoveryCallback != null) {
                discoveryCallback.onEndpointLost(endpointId);
            }
        }
    };

    // Payload callback implementation
    private final com.google.android.gms.nearby.connection.PayloadCallback payloadCallbackImpl = new com.google.android.gms.nearby.connection.PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            if (payload.getType() == Payload.Type.BYTES) {
                byte[] data = payload.asBytes();
                if (payloadCallback != null) {
                    payloadCallback.onPayloadReceived(payload.getId(), data);
                }
            } else if (payload.getType() == Payload.Type.FILE) {
                // Store the payload for processing when transfer completes
                receivedFilePayloads.put(payload.getId(), payload);
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId, @NonNull PayloadTransferUpdate update) {
            long payloadId = update.getPayloadId();
            int bytesTransferred = (int) update.getBytesTransferred();
            int totalBytes = (int) update.getTotalBytes();

            // Track progress
            payloadProgress.put(payloadId, bytesTransferred);

            // Log progress for sending
            if (totalBytes > 0 && payloadNames.containsKey(payloadId)) {
                int percent = (int) ((long) bytesTransferred * 100 / totalBytes);
                int step = (percent / 25) * 25; // 0, 25, 50, 75, 100

                Integer lastLogged = payloadLastLoggedStep.get(payloadId);
                int lastStep = lastLogged != null ? lastLogged : -1;

                if (step > lastStep) {
                    String name = payloadNames.get(payloadId);
                    myLogI("Sending " + name + ": " + step + "% (" + bytesTransferred + "/" + totalBytes + ")");
                    payloadLastLoggedStep.put(payloadId, step);
                }
            }

            if (payloadCallback != null) {
                payloadCallback.onPayloadTransferUpdate(payloadId, bytesTransferred, totalBytes);
            }

            // Check for completion or failure
            if (update.getStatus() == PayloadTransferUpdate.Status.SUCCESS) {
                // Check if this was a file payload
                Payload filePayload = receivedFilePayloads.get(payloadId);
                if (filePayload != null) {
                    // File transfer complete, notify callback
                    if (payloadCallback != null) {
                        payloadCallback.onFilePayloadReceived(payloadId, filePayload);
                    }
                    receivedFilePayloads.remove(payloadId);
                } else {
                    // Regular payload sent confirmation
                    if (payloadCallback != null) {
                        payloadCallback.onPayloadSent(payloadId);
                    }
                }
            } else if (update.getStatus() == PayloadTransferUpdate.Status.FAILURE) {
                receivedFilePayloads.remove(payloadId); // Clean up on failure
                if (payloadCallback != null) {
                    payloadCallback.onTransferFailed("Payload transfer failed: " + payloadId);
                }
            }
        }
    };
}
