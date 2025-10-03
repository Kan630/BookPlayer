package com.driot.bookplayer.testutil;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public class LoggingWatcher extends TestWatcher implements LogSupport {

    @Override
    protected void starting(Description description) {
        myLogI("➡️ START " + description.getDisplayName());
    }

    @Override
    protected void succeeded(Description description) {
        myLogI("✅ PASS  " + description.getDisplayName());
    }

    @Override
    protected void failed(Throwable e, Description description) {
        myLogE("❌ FAIL  " + description.getDisplayName() + " :: " + e);
    }

    @Override
    protected void finished(Description description) {
        myLogI("⏹ END   " + description.getDisplayName());
    }
}
