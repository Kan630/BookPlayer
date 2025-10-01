package com.driot.bookplayer.test;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;


public class JustOpenAndWait {

    private static final int WAIT_IN_SEC = 5;

    private ActivityScenario<?> scenario;



    @Before
    public void launchApp() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent launch = ctx.getPackageManager()
                .getLaunchIntentForPackage(ctx.getPackageName());
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        scenario = ActivityScenario.launch(launch);   // stays open until @After
    }

    @After
    public void tearDown() {
        if (scenario != null) scenario.close();
    }

    @Test
    public void navigate() {
        // your Espresso steps here (click buttons, scroll, etc.)
        SystemClock.sleep(1000 * WAIT_IN_SEC); // optional visual pause during debugging
    }
}
