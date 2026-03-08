package com.driot.bookplayer.net;

import com.driot.bookplayer.objects.CoverResult;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class GoogleImageProviderTest {

    @Test
    public void testSearch() {
        GoogleImageProvider provider = new GoogleImageProvider();
        // Context is nullable in provider if not used for anything but signatures
        List<CoverResult> results = provider.search(null, "The Lord of the Rings book cover", 10);

        assertNotNull(results);
        // We expect at least some results if internet is available
        // If no internet, results might be empty, which is okay for a simple
        // verification
        System.out.println("Found " + results.size() + " results from Google Image");
        for (CoverResult r : results) {
            System.out.println("Source: " + r.source + " | Title: " + r.title + " | URL: " + r.imageUrl);
            assertNotNull(r.imageUrl);
            assertFalse(r.imageUrl.isEmpty());
            assertEquals("GoogleImage", r.source);
        }
    }
}
