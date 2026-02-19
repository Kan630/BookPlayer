package com.driot.bookplayer.quickshare;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;

import dagger.hilt.android.lifecycle.HiltViewModel;

import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;
import com.google.android.gms.nearby.connection.Payload;

import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;

@HiltViewModel
public class NearbyShareViewModel extends LoggingAndroidViewModel {

    private final NearbyConnectionsHelper nearbyHelper;
    private final MutableLiveData<String> status = new MutableLiveData<>();
    private final MutableLiveData<Integer> progressTotal = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> progressCurrent = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> isActive = new MutableLiveData<>(false);
    private final MutableLiveData<String> connectedEndpoint = new MutableLiveData<>("");
    private final MutableLiveData<String> bookName = new MutableLiveData<>("");
    private final MutableLiveData<String> coverPath = new MutableLiveData<>("");
    private final MutableLiveData<Integer> totalFiles = new MutableLiveData<>(0);
    private final MutableLiveData<Long> totalSize = new MutableLiveData<>(0L);
    private final MutableLiveData<Boolean> isSendMode = new MutableLiveData<>(true);
    private final MutableLiveData<String> toastError = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isTransferFinished = new MutableLiveData<>(false);
    private final java.util.Map<String, String> endpointNames = new java.util.HashMap<>();
    private final java.util.concurrent.atomic.AtomicBoolean stopInProgress = new java.util.concurrent.atomic.AtomicBoolean(
            false);
    private final java.util.concurrent.ExecutorService fileProcessingExecutor = java.util.concurrent.Executors
            .newSingleThreadExecutor();
    private final NearbyShareReceiverHelper receiverHelper;

    @Inject
    public NearbyShareViewModel(@NonNull Application application, NearbyConnectionsHelper nearbyHelper) {
        super(application);
        this.nearbyHelper = nearbyHelper;
        this.receiverHelper = new NearbyShareReceiverHelper(application);
        status.setValue(application.getString(R.string.nearby_share_ready));
    }

    public LiveData<String> getStatus() {
        return status;
    }

    public LiveData<Integer> getProgressTotal() {
        return progressTotal;
    }

    public LiveData<Integer> getProgressCurrent() {
        return progressCurrent;
    }

    public LiveData<Boolean> getIsActive() {
        return isActive;
    }

    public LiveData<String> getConnectedEndpoint() {
        return connectedEndpoint;
    }

    public LiveData<Boolean> getIsSendMode() {
        return isSendMode;
    }

    public LiveData<String> getToastError() {
        return toastError;
    }

    public LiveData<Boolean> getIsTransferFinished() {
        return isTransferFinished;
    }

    public LiveData<String> getBookName() {
        return bookName;
    }

    public LiveData<Integer> getTotalFiles() {
        return totalFiles;
    }

    public LiveData<Long> getTotalSize() {
        return totalSize;
    }

    public LiveData<String> getCoverPath() {
        return coverPath;
    }

    public void setSendMode(boolean sendMode) {
        isSendMode.setValue(sendMode);
    }

    public void sendMessageToOtherDevice(String msgType, String msgText) {
        if (nearbyHelper != null && nearbyHelper.getConnectedEndpointId() != null) {
            nearbyHelper.sendControlMessage(nearbyHelper.getConnectedEndpointId(), msgType, msgText);
        } else {
            myLogE("Sending message to other device - No connected device ! - message = [" + msgType + " - " + msgText
                    + "]");
        }
    }

    public void prePrepareData(Folder folder, boolean transferProgress) {
        if (folder == null || !Boolean.TRUE.equals(isSendMode.getValue()))
            return;

        new Thread(() -> {
            status.postValue(getApplication().getString(R.string.nearby_share_preparing_data));
            AppDatabase db = AppDatabase.getDatabase(getApplication());
            List<ZikFile> files = db.zikFileDao().getZikFilesForFolder(folder.getId());
            CompletableFuture<NearbyConnectionsHelper.PreparedData> future = nearbyHelper
                    .prepareBookData(folder, files, transferProgress);
            if (future != null) {
                future.join();
            }
            if (isActive.getValue() == null || !isActive.getValue())
                status.postValue(getApplication().getString(R.string.nearby_share_ready));
        }).start();
    }

    public void startAdvertising(String deviceName, Folder folder, boolean transferProgress) {
        myLogI("Starting advertising");
        resetState();
        stopInProgress.set(false);
        isActive.setValue(true);
        nearbyHelper.startAdvertising(deviceName, new NearbyConnectionsHelper.AdvertisingCallback() {
            @Override
            public void onAdvertisingStarted() {
                myLogD("Advertising started");
                status.postValue(getApplication().getString(R.string.nearby_share_advertising_started));
            }

            @Override
            public void onConnectionInitiated(String endpointId, String endpointName) {
                endpointNames.put(endpointId, endpointName);
                myLogD("Connection initiated");
                status.postValue(getApplication().getString(R.string.nearby_share_connection_initiated, endpointName));
                nearbyHelper.acceptConnection(endpointId, createPayloadCallback());
            }

            @Override
            public void onConnectionEstablished(String endpointId) {
                String name = endpointNames.get(endpointId);
                connectedEndpoint.postValue(name != null ? name : endpointId);
                status.postValue(getApplication().getString(R.string.nearby_share_connection_established));
                myLogD("Connection established");
                new Thread(() -> {
                    AppDatabase db = AppDatabase.getDatabase(getApplication());
                    List<ZikFile> files = db.zikFileDao().getZikFilesForFolder(folder.getId());
                    nearbyHelper.sendBookData(endpointId, folder, files, transferProgress);
                }).start();
            }

            @Override
            public void onConnectionFailed(String errorMessage) {
                stopAll(errorMessage);
            }
        });
    }

    private NearbyConnectionsHelper.PayloadCallback createPayloadCallback() {
        return new NearbyConnectionsHelper.PayloadCallback() {
            @Override
            public void onPayloadSent(long payloadId) {
            }

            @Override
            public void onPayloadTransferUpdate(long payloadId, int bytesTransferred, int totalBytes) {
                Boolean active = isActive.getValue();
                if (active == null || !active)
                    return;

                int percent = totalBytes > 0 ? (int) ((bytesTransferred * 100.0) / totalBytes) : 0;

                // Get file index and count
                int index = nearbyHelper.getPayloadIndex(payloadId);
                int total = nearbyHelper.getTotalFilesToSend();
                String name = nearbyHelper.getPayloadName(payloadId);

                // Format: Sending file n°X/Y - XX% \n [Track Name]
                if (index > 0 && name != null) {
                    String statusMsg = getApplication().getString(R.string.nearby_share_sending_file_progress, index,
                            total, percent, name);
                    status.postValue(statusMsg);
                } else if (name != null) {
                    // Fallback for metadata or unknown
                    myLogE("fallback... to delete ?");
                    status.postValue(getApplication().getString(R.string.nearby_share_sending_progress, name, percent));
                }

                // Calculate progress using both completed and active payloads
                long totalSent = nearbyHelper.getTotalBytesSentOverall() + nearbyHelper.getActiveBytesTransferred();
                long totalSize = nearbyHelper.getPayloadsTotalSize();

                if (totalSize == 0) {
                    myLogW("totalSize is still 0 during transfer update. index=" + index + ", name=" + name);
                }

                int progress = totalSize > 0 ? (int) ((totalSent * 100) / totalSize) : 0;

                if (progress > 100)
                    progress = 100;
                progressTotal.postValue(progress);
                progressCurrent.postValue(percent);
            }

            @Override
            public void onPayloadReceived(long payloadId, byte[] data) { // sendMessageToOtherDevice /
                                                                         // sendControlMessage
                myLog("PayloadReceived -- ControlMessage?");
                new Thread(() -> {
                    try {
                        String jsonStr = new String(data);
                        if (jsonStr.startsWith("{")) {
                            JSONObject obj = new JSONObject(jsonStr);
                            String type = obj.optString("type");
                            if ("ERROR".equals(type)) {
                                String msg = obj.optString("message", "Unknown error");
                                stopAll(msg);
                            } else if ("SUCCESS".equals(type)) {
                                String msg = obj.optString("message",
                                        getApplication().getString(R.string.nearby_share_complete));
                                stopAll(msg, false);
                            } else if ("CANCEL".equals(type)) {
                                String msg = obj.optString("message",
                                        getApplication().getString(R.string.nearby_share_transfer_cancelled));
                                stopAll(msg, false);
                            } else {
                                myLogW("Unknown control message type: " + type);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }).start();
            }

            @Override
            public void onFilePayloadReceived(long payloadId, Payload filePayload) {
                myLog("FilePayloadReceived " + payloadId);
            }

            @Override
            public void onTransferComplete() {
                // Sender side transfer complete (all payloads sent) - wait for receiver
                // confirmation
                // We do NOT stop here anymore, we wait for the SUCCESS message from receiver
                status.postValue(getApplication().getString(R.string.nearby_share_waiting_confirmation));
            }

            @Override
            public void onTransferFailed(String errorMsg) {
                stopAll(errorMsg);
            }

        };
    }

    private void resetState() {
        if (receiverHelper != null) {
            receiverHelper.reset();
        }
        status.postValue(getApplication().getString(R.string.nearby_share_ready));
        progressTotal.postValue(0);
        progressCurrent.postValue(0);
        connectedEndpoint.postValue("");
        bookName.postValue("");
        coverPath.postValue("");
        totalFiles.postValue(0);
        totalSize.postValue(0L);
        toastError.postValue(null);
        isTransferFinished.postValue(false);
        endpointNames.clear();
    }

    public void startDiscovery(String deviceName) {
        resetState();
        stopInProgress.set(false);
        isActive.setValue(true);
        nearbyHelper.startDiscovery(new NearbyConnectionsHelper.DiscoveryCallback() {
            @Override
            public void onDiscoveryStarted() {
                status.postValue(getApplication().getString(R.string.nearby_share_discovery_started));
            }

            @Override
            public void onEndpointFound(String endpointId, String endpointName) {
                status.postValue(getApplication().getString(R.string.nearby_share_found_device, endpointName) + ". "
                        + getApplication().getString(R.string.nearby_share_connecting));
                nearbyHelper.requestConnection(endpointId, deviceName);
            }

            @Override
            public void onEndpointLost(String endpointId) {
            }

            @Override
            public void onConnectionInitiated(String endpointId, String endpointName) {
                endpointNames.put(endpointId, endpointName);
                status.postValue(getApplication().getString(R.string.nearby_share_connection_initiated, endpointName));
                nearbyHelper.acceptConnection(endpointId, createReceiverPayloadCallback(receiverHelper));
                FirebaseAnalyticsHelper.tellQuickShareConnected(endpointName);
            }

            @Override
            public void onConnectionEstablished(String endpointId) {
                String name = endpointNames.get(endpointId);
                connectedEndpoint.postValue(name != null ? name : endpointId);
                status.postValue(getApplication().getString(R.string.nearby_share_connected_waiting));
                setupReceiverProgressCallback(receiverHelper);
            }

            @Override
            public void onConnectionFailed(String errorMessage) {
                if (errorMessage != null
                        && (errorMessage.contains("8034") || errorMessage.contains("MISSING_PERMISSION_COARSE_LOACTION")
                                || errorMessage.contains("MISSING_PERMISSION_FINE_LOACTION"))) {
                    stopAll(errorMessage + getApplication().getString(R.string.nearby_share_error_location_hint));
                } else {
                    stopAll(errorMessage);
                }
                FirebaseAnalyticsHelper.tellQuickShareConnectionFailed(errorMessage);
            }
        });
    }

    private NearbyConnectionsHelper.PayloadCallback createReceiverPayloadCallback(
            NearbyShareReceiverHelper receiverHelper) {
        return new NearbyConnectionsHelper.PayloadCallback() {
            @Override
            public void onPayloadSent(long payloadId) {
            }

            @Override
            public void onPayloadTransferUpdate(long payloadId, int bytesTransferred, int totalBytes) {
            }

            @Override
            public void onPayloadReceived(long payloadId, byte[] data) {
                new Thread(() -> {
                    try {
                        String jsonStr = new String(data);
                        if (jsonStr.startsWith("{")) {
                            JSONObject obj = new JSONObject(jsonStr);
                            if ("ERROR".equals(obj.optString("type"))) {
                                String msg = obj.optString("message", "Unknown error");
                                stopAll(msg);
                                return;
                            } else if ("SUCCESS".equals(obj.optString("type"))) {
                                // Receiver receiving success message? Should not happen usually but handle it
                                String msg = obj.optString("message",
                                        getApplication().getString(R.string.nearby_share_complete));
                                stopAll(msg, false);
                                return;
                            }
                        }
                    } catch (Exception ignored) {
                    }

                    if (receiverHelper.parseMetadata(data)) {
                        String err = receiverHelper.createBookFolder();
                        if (err == null) {
                            status.postValue(getApplication().getString(R.string.nearby_share_metadata_received));
                        } else {
                            String errorForOtherDevice = getApplication()
                                    .getString(R.string.nearby_share_error_other_device_prefix, err);
                            nearbyHelper.sendControlMessage(nearbyHelper.getConnectedEndpointId(), "ERROR",
                                    errorForOtherDevice);
                            stopAll(err, true);
                        }
                    }
                }).start();
            }

            @Override
            public void onFilePayloadReceived(long payloadId,
                    com.google.android.gms.nearby.connection.Payload filePayload) {
                Boolean active = isActive.getValue();
                if (active == null || !active) {
                    myLogW("Ignoring file payload: ViewModel is not active");
                    return;
                }
                fileProcessingExecutor.execute(() -> {
                    try {
                        android.os.ParcelFileDescriptor pfd = filePayload.asFile().asParcelFileDescriptor();
                        if (pfd == null) {
                            java.io.File file = filePayload.asFile().asJavaFile();
                            if (file != null) {
                                pfd = android.os.ParcelFileDescriptor.open(file,
                                        android.os.ParcelFileDescriptor.MODE_READ_ONLY);
                            }
                        }

                        if (pfd != null) {
                            receiverHelper.saveFile(payloadId, pfd, filePayload.asFile().getSize());
                            pfd.close();
                        }
                    } catch (Exception e) {
                        myLogEE(e, "Failed to process file payload");
                    }
                });
            }

            @Override
            public void onTransferComplete() {
            }

            @Override
            public void onTransferFailed(String errorMsg) {
                stopAll(errorMsg);
            }
        };
    }

    private void setupReceiverProgressCallback(NearbyShareReceiverHelper receiverHelper) {
        receiverHelper.setProgressCallback(new NearbyShareReceiverHelper.ProgressCallback() {
            @Override
            public void onProgress(String fileName, int currentFile, int totalFilesCount, long bytesReceived,
                    long totalBytes,
                    int currentFileProgress) {
                Boolean active = isActive.getValue();
                if (active == null || !active)
                    return;

                if (currentFile == 0) { // Metadata received
                    bookName.postValue(receiverHelper.getBookName());
                    totalFiles.postValue(totalFilesCount);
                    totalSize.postValue(totalBytes);
                }

                // X/Y files written on disk
                String writtenStatus = getApplication().getString(R.string.nearby_share_files_written, currentFile,
                        totalFilesCount);
                String secondLine;

                if (currentFileProgress < 100) {
                    // now writing : [Track Name] - XX%
                    secondLine = getApplication().getString(R.string.nearby_share_now_writing, currentFileProgress,
                            fileName);
                } else {
                    // last written : [Track Name]
                    secondLine = getApplication().getString(R.string.nearby_share_last_written, fileName);
                }

                status.postValue(writtenStatus + "\n" + secondLine);

                int totalProgress = totalBytes > 0 ? (int) ((bytesReceived * 100.0) / totalBytes) : 0;
                progressTotal.postValue(totalProgress);
                progressCurrent.postValue(currentFileProgress);
            }

            @Override
            public void onCoverReceived(String path) {
                coverPath.postValue(path);
            }

            @Override
            public void onComplete(String bookName) {
                // Determine success message
                String successMsg = getApplication().getString(R.string.nearby_share_transfer_complete_with_name,
                        bookName);

                // 1. Notify sender that we are done
                nearbyHelper.sendControlMessage(nearbyHelper.getConnectedEndpointId(), "SUCCESS", successMsg);

                // 2. Stop ourselves
                stopAll(successMsg, false);
                progressTotal.postValue(100);
                progressCurrent.postValue(100);
            }

            @Override
            public void onError(String errorMsg) {
                toastError.postValue(errorMsg);
            }
        });
    }

    public void stopAll() {
        stopAll(null, false);
    }

    public void stopAll(String message) {
        stopAll(message, true);
    }

    public void stopAll(String message, boolean isError) {
        myLog("Stopping all: (isError=" + isError + ") - " + message);
        if (!stopInProgress.compareAndSet(false, true)) {
            myLogD("Stop already in progress, ignoring: " + message);
            return;
        }

        if (receiverHelper != null) {
            receiverHelper.cancel();
            receiverHelper.cleanupOnError();
            receiverHelper.reset();
        }

        nearbyHelper.cleanup();
        nearbyHelper.clearPreparedData();

        boolean complete = !isError && message != null && message.toLowerCase().contains("complete");

        if (isActive.getValue() != null && isActive.getValue()) {
            isActive.postValue(false);
            connectedEndpoint.postValue("");
            // If it's a success completion, mark finished but don't clear progress
            if (complete) {
                isTransferFinished.postValue(true);
                status.postValue(message);
                if (isSendMode != null && totalFiles != null && totalSize != null) {
                    FirebaseAnalyticsHelper.tellQuickShareComplete(isSendMode.getValue(), bookName.getValue(), "" + totalFiles.getValue(), "" + totalSize.getValue());
                } else {
                    FirebaseAnalyticsHelper.tellQuickShareComplete(null, bookName.getValue(), "", "");
                }

            } else {
                // Error or manual stop
                isTransferFinished.postValue(false);
                if (message != null && !message.isEmpty()) {
                    if (isError) {
                        status.postValue(getApplication().getString(R.string.nearby_share_stopped_with_error, message));
                        toastError.postValue(message);
                        FirebaseAnalyticsHelper.tellQuickShareError(message);
                    } else {
                        status.postValue(message);
                        FirebaseAnalyticsHelper.tellQuickShareCancel("cancel");
                    }
                } else {
                    status.postValue(getApplication().getString(R.string.nearby_share_stopped));
                    FirebaseAnalyticsHelper.tellQuickShareCancel("stopped");
                }
            }
        } else {
            // Already stopping or stopped
            isActive.postValue(false);
            if (complete) {
                isTransferFinished.postValue(true);
                status.postValue(message);
            } else {
                isTransferFinished.postValue(false);
            }
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopAll();
        fileProcessingExecutor.shutdown();
    }
}
