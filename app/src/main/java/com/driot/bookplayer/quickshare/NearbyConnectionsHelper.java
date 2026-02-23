package com.driot.bookplayer.quickshare;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;
import javax.inject.Inject;
import javax.inject.Singleton;
import dagger.hilt.android.qualifiers.ApplicationContext;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.helpers.UriHelper;
import com.driot.bookplayer.player.heatmaps.PlaySession;
import com.driot.bookplayer.utils.Tonio;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Helper class for managing Nearby Connections API
 * Handles device discovery, connections, and file transfers for book sharing
 */
@Singleton
public class NearbyConnectionsHelper {

    private static final String SERVICE_ID = "com.driot.bookplayer.nearby";
    private static final Strategy STRATEGY = Strategy.P2P_POINT_TO_POINT;

    private final Context context;
    private final ConnectionsClient connectionsClient;

    // Callbacks
    private AdvertisingCallback advertisingCallback;
    private DiscoveryCallback discoveryCallback;
    private PayloadCallback payloadCallback;

    // Connection tracking (Thread-safe)
    private final java.util.concurrent.ConcurrentHashMap<Long, Integer> payloadProgress = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Long, Payload> receivedFilePayloads = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Long, String> payloadNames = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Long, Long> payloadSizes = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Long, Integer> payloadIndices = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Long, Integer> payloadLastLoggedStep = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile int totalFilesToSend = 0;
    private volatile String connectedEndpointId;
    private volatile long totalBytesSentOverall = 0;
    private volatile long payloadsTotalSize = 0;
    private volatile java.util.concurrent.CompletableFuture<PreparedData> preparationFuture;

    public static class PreparedData {
        public final Folder folder;
        public final List<Payload> filePayloads;
        public final List<Long> payloadIds;
        public final List<ZikFile> audioFiles;
        public final boolean transferProgress;
        public final long totalSize;
        public final Map<Long, String> names = new java.util.HashMap<>();
        public final Map<Long, Long> sizes = new java.util.HashMap<>();
        public final Map<Long, Integer> indices = new java.util.HashMap<>();

        public PreparedData(Folder folder, List<Payload> filePayloads, List<Long> payloadIds,
                List<ZikFile> audioFiles, boolean transferProgress, long totalSize) {
            this.folder = folder;
            this.filePayloads = filePayloads;
            this.payloadIds = payloadIds;
            this.audioFiles = audioFiles;
            this.transferProgress = transferProgress;
            this.totalSize = totalSize;
        }

        public void cleanup() {
            for (Payload p : filePayloads) {
                try {
                    android.os.ParcelFileDescriptor pfd = p.asFile().asParcelFileDescriptor();
                    if (pfd != null)
                        pfd.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Inject
    public NearbyConnectionsHelper(@ApplicationContext Context context) {
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
                        advertisingCallback.onConnectionFailed(
                                context.getString(R.string.nearby_share_error_advertising_failed, e.getMessage()));
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
                        discoveryCallback.onConnectionFailed(
                                context.getString(R.string.nearby_share_error_discovery_failed, e.getMessage()));
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
                        discoveryCallback.onConnectionFailed(context
                                .getString(R.string.nearby_share_error_connection_request_failed, e.getMessage()));
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
     * Send control message to endpoint
     */
    public void sendControlMessage(String endpointId, String type, String message) {
        try {
            JSONObject controlObj = new JSONObject();
            controlObj.put("type", type);
            controlObj.put("message", message);
            Payload controlPayload = Payload.fromBytes(controlObj.toString().getBytes());
            connectionsClient.sendPayload(endpointId, controlPayload);
            myLog("Sent [" + type + "] message to endpoint: [" + message + "]");
        } catch (Exception e) {
            myLogEE(e, "Failed to send control message");
        }
    }

    /**
     * Pre-prepare payloads and metadata to speed up transfer start
     */
    public synchronized CompletableFuture<PreparedData> prepareBookData(Folder folder,
            List<ZikFile> audioFiles, boolean transferProgress) {
        myLogD("Requesting pre-preparation for: " + folder.getName());
        clearPreparedData();
        preparationFuture = java.util.concurrent.CompletableFuture
                .supplyAsync(() -> performPreparation(folder, audioFiles, transferProgress));
        return preparationFuture;
    }

    private PreparedData performPreparation(Folder folder, List<ZikFile> audioFiles, boolean transferProgress) {
        myLogD("Starting preparation for: " + folder.getName());
        try {
            // Prepare list of payloads and metadata info
            java.util.ArrayList<Uri> urisToSend = new java.util.ArrayList<>();
            java.util.ArrayList<String> fileNames = new java.util.ArrayList<>();
            java.util.ArrayList<Long> fileSizes = new java.util.ArrayList<>();

            // 1. Add cover if exists
            if (folder.image != null && !folder.image.isEmpty()) {
                myLogD("Book has a cover in DB");
                Uri coverUri = UriHelper.resolveUriFromPath(context, folder.image);
                if (coverUri != null) {
                    urisToSend.add(coverUri);
                    fileNames.add("cover.jpg"); // Force standard name
                    fileSizes.add(UriHelper.getSize(context, coverUri));
                } else {
                    myLogW("Cover URI not resolvable: [" + folder.image + "]");
                }
            }

            // 2. Add audio files
            for (ZikFile zikFile : audioFiles) {
                Uri audioUri = UriHelper.resolvePlayableUri(context, zikFile);
                if (audioUri != null) {
                    urisToSend.add(audioUri);
                    fileNames.add(zikFile.getName());
                    fileSizes.add(UriHelper.getSize(context, audioUri));
                } else {
                    myLogEE(null, "Quick share : could not resolve playable URI for " + zikFile.getName());
                }
            }
            myLogD("all files added");

            // 1. Prepare all payloads and collect their IDs
            List<Payload> filePayloads = new java.util.ArrayList<>();
            List<Long> payloadIds = new java.util.ArrayList<>();
            long totalSize = 0;

            PreparedData data = new PreparedData(folder, filePayloads, payloadIds, audioFiles, transferProgress, 0);

            for (int i = 0; i < urisToSend.size(); i++) {
                // Early exit if disconnected/cancelled
                if (connectedEndpointId == null && (preparationFuture != null && preparationFuture.isCancelled())) {
                    myLogW("Preparation aborted");
                    data.cleanup();
                    return null;
                }

                Uri uri = urisToSend.get(i);
                String name = fileNames.get(i);
                try {
                    android.os.ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r");
                    if (pfd != null) {
                        Payload filePayload = Payload.fromFile(pfd);
                        filePayloads.add(filePayload);
                        payloadIds.add(filePayload.getId());

                        data.names.put(filePayload.getId(), name);
                        data.sizes.put(filePayload.getId(), fileSizes.get(i));
                        data.indices.put(filePayload.getId(), i + 1);
                        totalSize += fileSizes.get(i);
                    }
                } catch (Exception e) {
                    myLogEE(e, "Error preparing payload for " + name);
                }
            }

            PreparedData finalData = new PreparedData(folder, filePayloads, payloadIds, audioFiles, transferProgress,
                    totalSize);
            finalData.names.putAll(data.names);
            finalData.sizes.putAll(data.sizes);
            finalData.indices.putAll(data.indices);

            myLogI("Preparation complete: " + folder.getName() + " (" + filePayloads.size() + " files)");
            return finalData;

        } catch (Exception e) {
            myLogEE(e, "Error in preparation");
            return null;
        }
    }

    public synchronized void clearPreparedData() {
        if (preparationFuture != null) {
            preparationFuture.cancel(true);
            preparationFuture.thenAccept(data -> {
                if (data != null)
                    data.cleanup();
            });
            preparationFuture = null;
            myLogD("Prepared data future cleared/cancelled");
        }
    }

    /**
     * Send book data to connected endpoint
     */
    public void sendBookData(String endpointId, Folder folder, List<ZikFile> audioFiles, boolean transferProgress) {
        myLogD("starting sendBookData task");
        new Thread(() -> {
            payloadNames.clear();
            payloadSizes.clear();
            payloadIndices.clear();
            payloadLastLoggedStep.clear();
            totalBytesSentOverall = 0;
            payloadsTotalSize = 0;

            PreparedData data = null;
            java.util.concurrent.CompletableFuture<PreparedData> future = preparationFuture;

            // Check if we can use/wait for the existing future
            if (future != null) {
                try {
                    PreparedData cached = future.get(5, java.util.concurrent.TimeUnit.SECONDS);
                    if (cached != null && cached.folder.getId() == folder.getId()
                            && cached.transferProgress == transferProgress) {
                        myLogI("Using PREPARED data (synced) for " + folder.getName());
                        data = cached;
                    }
                } catch (Exception e) {
                    myLogW("Preparation future failed or timed out: " + e.getMessage());
                }
            }

            // Fallback: Perform preparation now if no valid cache
            if (data == null) {
                myLogW("No valid prepared data, performing sync preparation...");
                data = performPreparation(folder, audioFiles, transferProgress);
            }

            if (data == null) {
                myLogE("Data preparation failed, cannot send.");
                if (payloadCallback != null)
                    payloadCallback
                            .onTransferFailed(context.getString(R.string.nearby_share_error_data_preparation_failed));
                return;
            }

            try {
                payloadNames.putAll(data.names);
                payloadSizes.putAll(data.sizes);
                payloadIndices.putAll(data.indices);
                payloadsTotalSize = data.totalSize;
                totalFilesToSend = data.filePayloads.size();

                // Send metadata
                // IMPORTANT: pass the maps (not .values()) so createBookMetadata can iterate
                // in payloadIds order — HashMap.values() order is non-deterministic and causes
                // mismatched payloadId→filename mappings on the receiver side.
                JSONObject metadata = createBookMetadata(folder, data.names, data.sizes,
                        data.payloadIds, data.audioFiles, transferProgress);
                Payload metadataPayload = Payload.fromBytes(metadata.toString().getBytes());
                payloadNames.put(metadataPayload.getId(), "Metadata");
                payloadSizes.put(metadataPayload.getId(), (long) metadataPayload.asBytes().length);
                payloadsTotalSize += metadataPayload.asBytes().length;

                myLogD("sending metadata");
                connectionsClient.sendPayload(endpointId, metadataPayload);

                // Send files
                myLogD("sending files");
                for (Payload filePayload : data.filePayloads) {
                    if (connectedEndpointId == null) {
                        myLogW("Send aborted: disconnected");
                        return;
                    }
                    connectionsClient.sendPayload(endpointId, filePayload);
                }
            } catch (Exception e) {
                myLogEE(e, "Error sending book data");
                if (payloadCallback != null) {
                    payloadCallback.onTransferFailed(
                            context.getString(R.string.nearby_share_error_send_data_failed, e.getMessage()));
                }
            }
        }).start();
    }

    /**
     * Create book metadata JSON
     * Includes book info, file list (names and sizes), and optionally reading
     * progress.
     */
    private JSONObject createBookMetadata(Folder folder, Map<Long, String> namesMap,
            Map<Long, Long> sizesMap, java.util.List<Long> payloadIds, List<ZikFile> audioFiles,
            boolean transferProgress) throws JSONException {
        JSONObject metadata = new JSONObject();
        metadata.put("bookName", folder.getName());
        metadata.put("fileCount", payloadIds.size());
        metadata.put("trackCount", audioFiles.size());
        metadata.put("transferProgress", transferProgress);

        long totalSize = 0;
        JSONArray fileList = new JSONArray();

        int audioFileIndex = 0;
        for (int i = 0; i < payloadIds.size(); i++) {
            long payloadId = payloadIds.get(i);
            // Look up name and size by payloadId to guarantee correct pairing
            String name = namesMap.get(payloadId);
            Long sizeObj = sizesMap.get(payloadId);
            long size = sizeObj != null ? sizeObj : 0;

            JSONObject fileInfo = new JSONObject();
            fileInfo.put("name", name);
            fileInfo.put("size", size);
            fileInfo.put("payloadId", payloadId);
            totalSize += size;

            // Check if this is an audio file (not the cover)
            if (name != null && !name.equalsIgnoreCase("cover.jpg") && audioFileIndex < audioFiles.size()) {
                ZikFile zikFile = audioFiles.get(audioFileIndex++);

                if (transferProgress) {
                    fileInfo.put("position", zikFile.getPosition());
                    fileInfo.put("percentdone", zikFile.getPercentdone());
                    fileInfo.put("finished", zikFile.isFinished());
                    fileInfo.put("lFirstAccess", zikFile.lFirstAccess);
                    fileInfo.put("lLastAccess", zikFile.lLastAccess);
                    fileInfo.put("zeorder", zikFile.getZeorder());

                    // Add PlaySessions if available
                    try {
                        List<PlaySession> sessions = AppDatabase.getDatabase(context).playSessionDao()
                                .getAllForFile(zikFile.getId());

                        if (sessions != null && !sessions.isEmpty()) {
                            JSONArray sessionArray = new JSONArray();
                            for (PlaySession session : sessions) {
                                JSONObject sObj = new JSONObject();
                                sObj.put("timestampStart", session.timestampStart);
                                sObj.put("timestampEnd", session.timestampEnd);
                                sObj.put("positionStart", session.positionStart);
                                sObj.put("positionEnd", session.positionEnd);
                                sessionArray.put(sObj);
                            }
                            fileInfo.put("playSessions", sessionArray);
                        }
                    } catch (Exception e) {
                        myLogE("Failed to include PlaySessions for " + zikFile.getName() + ": " + e.getMessage());
                    }
                }
            }

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
        connectedEndpointId = null;
        clearPreparedData();
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
                String error = context.getString(R.string.nearby_share_error_connection_failed_status,
                        String.valueOf(result.getStatus().getStatusCode()));
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
                payloadCallback.onTransferFailed(context.getString(R.string.nearby_share_error_connection_lost));
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
                    myLogD("Sending " + name + ": " + step + "% (" + Tonio.getReadableSize(bytesTransferred) + "/"
                            + Tonio.getReadableSize(totalBytes) + ")");
                    payloadLastLoggedStep.put(payloadId, step);
                }
            }

            if (payloadCallback != null) {
                payloadCallback.onPayloadTransferUpdate(payloadId, bytesTransferred, totalBytes);
            }

            // Update overall progress for sender
            if (update.getStatus() == PayloadTransferUpdate.Status.SUCCESS) {
                Long size = payloadSizes.get(payloadId);
                if (size != null) {
                    totalBytesSentOverall += size;
                }
                payloadProgress.remove(payloadId); // Remove from active progress
            } else if (update.getStatus() == PayloadTransferUpdate.Status.FAILURE) {
                payloadProgress.remove(payloadId); // Remove from active progress
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
                    payloadCallback.onTransferFailed(context
                            .getString(R.string.nearby_share_error_payload_transfer_failed, String.valueOf(payloadId)));
                }
            }
        }
    };

    public String getPayloadName(long payloadId) {
        return payloadNames.get(payloadId);
    }

    public long getPayloadsTotalSize() {
        return payloadsTotalSize;
    }

    public long getTotalBytesSentOverall() {
        return totalBytesSentOverall;
    }

    public String getConnectedEndpointId() {
        return connectedEndpointId;
    }

    public int getPayloadIndex(long payloadId) {
        if (payloadIndices.containsKey(payloadId)) {
            return payloadIndices.get(payloadId);
        }
        return -1;
    }

    public long getActiveBytesTransferred() {
        long active = 0;
        for (long bytes : payloadProgress.values()) {
            active += bytes;
        }
        return active;
    }

    public int getTotalFilesToSend() {
        return totalFilesToSend;
    }

}
