package com.driot.bookplayer.utils.log;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.nio.channels.ClosedByInterruptException;

public class CrashReportTest {

    @Test
    public void redact_removesPlainAndEncodedEmails() {
        assertEquals("content://com.google.android.gm.sapi/<email>/message_attachment_external/1",
                CrashReport.redact("content://com.google.android.gm.sapi/some.one+x@gmail.com/message_attachment_external/1"));
        assertEquals("file:///x/<email>/y", CrashReport.redact("file:///x/foo%40bar.fr/y"));
        String noEmail = "content://org.telegram.messenger.provider/media/Butterfly%205.m4b";
        assertEquals(noEmail, CrashReport.redact(noEmail));
        assertEquals("null", CrashReport.redact(null));
    }

    @Test
    public void clip_keepsCrashlyticsValuesUnder1024() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3000; i++)
            sb.append('a');
        String clipped = CrashReport.clip(sb.toString());
        assertEquals(CrashReport.MAX_CRASHLYTICS_VALUE, clipped.length());
        assertTrue(clipped.endsWith("…"));
        assertEquals("short", CrashReport.clip("short"));
    }

    @Test
    public void allow_dropsRepeatsWithinTheWindow() {
        String key = "test|" + System.nanoTime();
        assertEquals(0, CrashReport.allow(key));
        assertEquals(-1, CrashReport.allow(key));
        assertEquals(-1, CrashReport.allow(key));
        assertEquals(0, CrashReport.allow(key + "-other"));
    }

    @Test
    public void isInterruption_onlyForCancellations() {
        assertTrue(CrashReport.isInterruption(new InterruptedIOException("thread interrupted")));
        assertTrue(CrashReport.isInterruption(new ClosedByInterruptException()));
        assertTrue(CrashReport.isInterruption(new RuntimeException(new InterruptedException())));
        assertFalse(CrashReport.isInterruption(new SocketTimeoutException("Read timed out")));
        assertFalse(CrashReport.isInterruption(new IOException("boom")));
        assertFalse(CrashReport.isInterruption(null));
    }

    @Test
    public void callerStack_startsAtTheRealCaller() {
        StackTraceElement[] stack = myLogEEWrapper();
        // the logger-style wrapper (utils.log + "my..." method) is stripped: the test method is the top frame
        assertEquals(CrashReportTest.class.getName(), stack[0].getClassName());
        assertEquals("callerStack_startsAtTheRealCaller", stack[0].getMethodName());
    }

    /** Mimics BaseActivity.myLogEE / LoggerStaticHelper.myLogEE (same package, "my" prefix). */
    private static StackTraceElement[] myLogEEWrapper() {
        return CrashReport.callerStack();
    }
}
