package com.driot.bookplayer.testutil;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public class LoggingWatcher extends TestWatcher implements LogSupport {

    @Override
    protected void starting(Description description) {
        myLog("oo-----------------------------------------------------------------------------oo");
        myLogI("➡️ START " + description.getDisplayName() + " ➡️➡️➡️➡️➡️➡️➡️➡️➡️");
        myLog("oo-----------------------------------------------------------------------------oo");
    }

    @Override
    protected void succeeded(Description description) {
        myLog("oo-----------------------------------------------------------------------------oo");
        myLogI("✅ PASS  " + description.getDisplayName());
        myLog("oo-----------------------------------------------------------------------------oo");
    }

    @Override
    protected void failed(Throwable e, Description description) {
        myLog("oo-----------------------------------------------------------------------------oo");
        myLogE("❌ FAIL         " + description.getDisplayName());
        myLog("oo-----------------------------------------------------------------------------oo");
        myLogE(e.getMessage());
        myLog("oo-----------------------------------------------------------------------------oo");
    }

    @Override
    protected void finished(Description description) {
        myLog("oo-----------------------------------------------------------------------------oo");
        myLogI("⏹ END   " + description.getDisplayName());
        myLog("oo-----------------------------------------------------------------------------oo");
    }
}
