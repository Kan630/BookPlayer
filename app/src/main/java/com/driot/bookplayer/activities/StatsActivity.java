package com.driot.bookplayer.activities;

import static com.driot.bookplayer.utils.Tonio.formatMem;
import static com.driot.bookplayer.utils.Tonio.getAppSize;
import static com.driot.bookplayer.utils.Tonio.getAvailableInternalMemorySize;
import static com.driot.bookplayer.utils.Tonio.getFolderSize;

import android.os.Bundle;
import android.widget.TextView;

import com.driot.bookplayer.R;

public class StatsActivity extends LifecycleLoggingActivity {

    private TextView tv1;
    private TextView tv2;
    private String zeText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        tv1 = findViewById(R.id.tv1);
        tv2 = findViewById(R.id.tv2);

        long availableMegs2 = getAvailableInternalMemorySize() / 1048576L;
        long currentAppSize = getAppSize(this) / 1048576L;
        //long currentAudiosSize = getFolderSize(this, "cache") / 1048576L;
        long currentAudiosSize = getFolderSize(this, "/data/data/com.driot.bookplayer/files/unzipped") / 1048576L;
        //long currentLogsSize = getFolderSize(this, "log") / 1048576L;
        long currentLogsSize = getFolderSize(this, "/data/data/com.driot.bookplayer/files/log") / 1048576L;


        zeText = formatMem(currentAppSize) + " Mo : taken by BookPlayer app" + "\n" + "\n" +
                formatMem(currentAudiosSize) + " Mo : taken by Audios" + "\n" + "\n" +
                //"---" + " Mo : taken by Audios" + "\n" + "\n" +
                formatMem(currentLogsSize) + " Mo :  taken by Logs" + "\n" + "\n" +
                //"---" + " Mo :  taken by Logs" + "\n" + "\n" +
                formatMem(availableMegs2) + " Mo : left on the device"
                ;

        tv2.setText("Memory");
        tv1.setText(zeText);

    }

}
