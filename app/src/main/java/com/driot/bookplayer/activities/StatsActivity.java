package com.driot.bookplayer.activities;

import static com.driot.bookplayer.utils.Tonio.formatMem;
import static com.driot.bookplayer.utils.Tonio.getAppSize;
import static com.driot.bookplayer.utils.Tonio.getAvailableInternalMemorySize;

import android.os.Bundle;
import android.widget.TextView;

import com.driot.bookplayer.R;

public class StatsActivity extends LifecycleLoggingActivity {

    private TextView tv1;
    private TextView tv2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        tv1 = findViewById(R.id.tv1);
        tv2 = findViewById(R.id.tv2);

        long availableMegs2 = getAvailableInternalMemorySize() / 1048576L;
        long currentAppSize = getAppSize(this) / 1024L;

        tv2.setText(formatMem(currentAppSize) + " : Memory taken by BookPlayer and its stored audios" + "\n"
                + "\n" + formatMem(availableMegs2) + " : Memory left on the device");

    }

}
