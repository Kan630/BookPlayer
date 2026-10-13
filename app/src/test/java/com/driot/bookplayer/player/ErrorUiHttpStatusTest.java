package com.driot.bookplayer.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * The agreement between ExoRadioPlayerEngine (which writes "[HTTP nnn]" into its error message,
 * the status being reachable only from the exception's cause) and ErrorUi, which turns it into
 * something a listener can read. Two files, one format - exactly what rots unnoticed.
 */
public class ErrorUiHttpStatusTest {

    @Test
    public void readsTheStatusTheEngineWrites() {
        // the real message from France 24's retired Akamai endpoints
        assertEquals("HTTP 400", ErrorUi.httpStatusToken(
                "Exo Error: ERROR_CODE_IO_BAD_HTTP_STATUS (2004) Source error [HTTP 400]"));
        assertEquals("HTTP 404", ErrorUi.httpStatusToken("whatever [HTTP 404] trailing text"));
    }

    @Test
    public void noStatusMeansNoToken() {
        assertNull(ErrorUi.httpStatusToken(null));
        assertNull(ErrorUi.httpStatusToken(""));
        assertNull(ErrorUi.httpStatusToken(
                "Exo Error: ERROR_CODE_IO_NETWORK_CONNECTION_FAILED (2001) Source error"));
        assertNull(ErrorUi.httpStatusToken("truncated [HTTP "));
        assertNull(ErrorUi.httpStatusToken("empty [HTTP]"));
    }
}
