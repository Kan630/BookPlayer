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

                // Skip common Google UI/tracking images and social media icons
                String srcLower = src.toLowerCase();
                String altLower = img.attr("alt").toLowerCase();

                if (srcLower.contains("googlelogo") || srcLower.contains("clear.png")
                        || srcLower.contains("/images/branding/")
                        || srcLower.contains("/productlogos/") || srcLower.contains("google.com/logos/")
                        || srcLower.contains("facebook") || srcLower.contains("whatsapp")
                        || srcLower.contains("twitter")
                        || srcLower.contains("instagram") || srcLower.contains("linkedin") || srcLower.contains("email")
                        || srcLower.contains("share_icon") || srcLower.contains("profile_icon")) {
                    continue;
                }

                if (altLower.contains("facebook") || altLower.contains("whatsapp") || altLower.contains("twitter")
                        || altLower.contains("instagram") || altLower.contains("linkedin") || altLower.contains("email")
                        || altLower.contains("partager") || altLower.contains("share")) {
                    continue;
                }

                // Skip small icons/trackers (often 1x1 or very small)
                // Note: user requested minimum size to filter them out.
                String widthStr = img.attr("width");
                String heightStr = img.attr("height");
                myLog(src + " width=" + widthStr + " height=" + heightStr + " alt=" + img.attr("alt") + " size=" + src.length()
                        );
                try {
                    if (!widthStr.isEmpty() && !heightStr.isEmpty()) {
                        int w = Integer.parseInt(widthStr);
                        int h = Integer.parseInt(heightStr);
                        if (w < 100 || h < 100)
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
