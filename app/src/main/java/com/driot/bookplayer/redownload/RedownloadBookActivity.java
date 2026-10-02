package com.driot.bookplayer.redownload;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;
import android.app.Activity;
import android.os.Bundle;

import com.driot.bookplayer.R;

/** No screen of its own: the "download again" button of an error dialog lands here, queues the
 *  download and closes. */
public class RedownloadBookActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        long folderId = getIntent().getLongExtra(RedownloadHelper.KEY_FOLDER_ID, -1);
        if (folderId >= 0) {
            RedownloadHelper.start(this, folderId);
            myToastLong(getString(R.string.redownload_started));
        }
        finish();
    }
}
