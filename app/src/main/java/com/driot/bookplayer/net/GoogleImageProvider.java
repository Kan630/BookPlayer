package com.driot.bookplayer.net;

import android.content.Context;
import com.driot.bookplayer.objects.CoverResult;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.ArrayList;
import java.util.List;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

/**
 * Basic Google Image search provider using JSoup scraping.
 * Designed to get medium-sized thumbnails without requiring an API key.
 */
public class GoogleImageProvider implements CoverSearchProvider {

    private static final String GOOGLE_SEARCH_URL = "https://www.google.com/search?tbm=isch&q=";
    // Using a simpler mobile User-Agent to get more predictable HTML
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; U; Android 4.4.2; en-us; LGMS323 Build/KOT49I.MS32310c) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/30.0.0.0 Mobile Safari/537.36";

    @Override
    public List<CoverResult> search(Context ctx, String query, int max) {
        ArrayList<CoverResult> out = new ArrayList<>();
        try {
            String url = GOOGLE_SEARCH_URL + java.net.URLEncoder.encode(query, "UTF-8");

            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(8000)
                    .get();

            // In very simple mobile layouts, images are often in <img> tags.
            // We want to skip the top logo and other UI elements.
            Elements imgs = doc.select("img");

            for (Element img : imgs) {
                if (out.size() >= max)
                    break;

                String src = img.absUrl("src");
                if (src.isEmpty()) {
                    src = img.attr("data-src");
                }

                if (src.isEmpty())
                    continue;

                // Skip common Google UI/tracking images
                if (src.contains("googlelogo") || src.contains("clear.png") || src.contains("/images/branding/")
                        || src.contains("/productlogos/") || src.contains("google.com/logos/")) {
                    continue;
                }

                // Skip small icons/trackers (often 1x1 or very small)
                String widthStr = img.attr("width");
                String heightStr = img.attr("height");
                try {
                    if (!widthStr.isEmpty() && !heightStr.isEmpty()) {
                        int w = Integer.parseInt(widthStr);
                        int h = Integer.parseInt(heightStr);
                        if (w < 40 || h < 40)
                            continue;
                    }
                } catch (NumberFormatException ignored) {
                }

                String title = img.attr("alt");
                if (title.isEmpty()) {
                    title = query;
                }

                out.add(new CoverResult(title, src, "GoogleImage"));
            }

        } catch (Exception e) {
            myLogEE(e, "GoogleImage search failed");
        }
        return out;
    }
}
