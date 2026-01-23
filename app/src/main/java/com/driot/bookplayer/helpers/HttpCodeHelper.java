package com.driot.bookplayer.helpers;

import android.content.Context;
import com.driot.bookplayer.R;

public class HttpCodeHelper {

    /**
     * Translate an HTTP code to a user friendly message.
     * 
     * @param context required to get string resources
     * @param code    the http error code (e.g. 404)
     * @return a formatted string like " - Not Found" (or empty string if code is
     *         not known)
     */
    public static String getTranslatedHttpCode(Context context, int code) {
        if (context == null)
            return "";

        int resId = 0;
        switch (code) {
            case 400:
                resId = R.string.http_code_400;
                break;
            case 401:
                resId = R.string.http_code_401;
                break;
            case 403:
                resId = R.string.http_code_403;
                break;
            case 404:
                resId = R.string.http_code_404;
                break;
            case 408:
                resId = R.string.http_code_408;
                break;
            case 429:
                resId = R.string.http_code_429;
                break;
            case 500:
                resId = R.string.http_code_500;
                break;
            case 502:
                resId = R.string.http_code_502;
                break;
            case 503:
                resId = R.string.http_code_503;
                break;
            case 504:
                resId = R.string.http_code_504;
                break;
        }

        if (resId != 0) {
            return " - " + context.getString(resId);
        }
        return "";
    }
}
