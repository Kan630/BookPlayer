package com.driot.bookplayer.testutil;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import org.junit.rules.ExternalResource;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;

import android.os.RemoteException;

public class OrientationRule extends ExternalResource {
    private final int desiredOrientation;

    public OrientationRule(int desiredOrientation) {
        this.desiredOrientation = desiredOrientation;
    }

    @Override
    protected void before() throws RemoteException {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.freezeRotation();
        if (desiredOrientation == SCREEN_ORIENTATION_LANDSCAPE) {
            device.setOrientationLeft();   // or setOrientationRight()
        } else {
            device.setOrientationNatural();
        }
    }

    @Override
    protected void after() {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        try {
            device.unfreezeRotation();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }
}
