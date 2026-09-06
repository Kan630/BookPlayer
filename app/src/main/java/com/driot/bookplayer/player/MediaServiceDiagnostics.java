package com.driot.bookplayer.player;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.service.notification.StatusBarNotification;

import androidx.core.content.ContextCompat;

import com.driot.bookplayer.utils.log.LoggerHelper;

/**
 * Diagnostics for two ongoing investigations:
 * - "headset button needs 2 presses after idle": correlates screen on/off and Bluetooth link
 * connect/disconnect timing against onMediaButtonEvent.
 * - "stuck Preparing/Please wait notification": dumps the actual title/text of whatever is
 * currently shown for our foreground notification id, so the log shows directly (not by
 * inference) what the user sees at a given moment.
 */
public class MediaServiceDiagnostics extends LoggerHelper {

    private final Context context;
    private final int notificationId;

    private BroadcastReceiver wakeDiagnosticsReceiver;

    public MediaServiceDiagnostics(Context context, int notificationId) {
        super(MediaServiceDiagnostics.class);
        this.context = context;
        this.notificationId = notificationId;
    }

    public void register() {
        wakeDiagnosticsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action == null)
                    return;
                if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)
                        || BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                    String deviceInfo = "unknown";
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null) {
                        try {
                            if (ContextCompat.checkSelfPermission(MediaServiceDiagnostics.this.context,
                                    Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                deviceInfo = device.getName() + " (" + device.getAddress() + ")";
                            } else {
                                deviceInfo = device.getAddress();
                            }
                        } catch (SecurityException e) {
                            deviceInfo = "(no BLUETOOTH_CONNECT permission)";
                        }
                    }
                    myLogW("WAKE_DIAGNOSTICS: " + action + " - device=" + deviceInfo);
                } else {
                    myLogW("WAKE_DIAGNOSTICS: " + action);
                }
            }
        };

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        ContextCompat.registerReceiver(context, wakeDiagnosticsReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    public void unregister() {
        if (wakeDiagnosticsReceiver != null) {
            try {
                context.unregisterReceiver(wakeDiagnosticsReceiver);
            } catch (IllegalArgumentException ignored) {
                // already unregistered
            }
            wakeDiagnosticsReceiver = null;
        }
    }

    public void logActiveNotification(String label) {
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null)
                return;
            StatusBarNotification[] active = nm.getActiveNotifications();
            boolean found = false;
            for (StatusBarNotification sbn : active) {
                if (sbn.getId() == notificationId) {
                    Notification n = sbn.getNotification();
                    CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
                    CharSequence text = n.extras.getCharSequence(Notification.EXTRA_TEXT);
                    myLogW("DIAG_NOTIF[" + label + "]: title=[" + title + "] text=[" + text + "]");
                    found = true;
                }
            }
            if (!found) {
                myLogW("DIAG_NOTIF[" + label + "]: no active notification with id=" + notificationId);
            }
        } catch (Throwable t) {
            myLogEE(t, "logActiveNotification(" + label + ") failed");
        }
    }

    public void logFocusChange(int change) {
        String changeStr;
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS:
                changeStr = "AUDIOFOCUS_LOSS";
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                changeStr = "AUDIOFOCUS_LOSS_TRANSIENT";
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                changeStr = "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK";
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                changeStr = "AUDIOFOCUS_GAIN";
                break;
            default:
                changeStr = "UNKNOWN(" + change + ")";
                break;
        }
        myLogI("Audio Focus Change: " + changeStr + " (" + change + ")");
    }
}
