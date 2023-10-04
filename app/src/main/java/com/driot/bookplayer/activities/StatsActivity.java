package com.driot.bookplayer.activities;

import static com.driot.bookplayer.utils.Tonio.formatMem;
import static com.driot.bookplayer.utils.Tonio.getAppSize;
import static com.driot.bookplayer.utils.Tonio.getAvailableInternalMemorySize;
import static com.driot.bookplayer.utils.Tonio.getFolderSize;
import static com.driot.bookplayer.utils.Tonio.getTotaLInternalMemorySize;
import static com.driot.tonylib.KanLogger.isMyPhoneDev;
import static com.driot.tonylib.TonioCommonStuff.MD5;

import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

import com.driot.bookplayer.BuildConfig;
import com.driot.bookplayer.R;
import com.driot.tonylib.KanLogger;

public class StatsActivity extends LifecycleLoggingActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        long totalMemory = getTotaLInternalMemorySize() / 1048576L;
        long availableMegs2 = getAvailableInternalMemorySize() / 1048576L;
        long currentAppSize = getAppSize(this) / 1048576L;
        long currentAudiosSize = getFolderSize(this, "/data/data/com.driot.bookplayer/files/unzipped") / 1048576L;
        long currentLogsSize = getFolderSize(this, "/data/data/com.driot.bookplayer/files/log") / 1048576L;

        String zeText;
        TextView tv_head;
        TextView tv_body;

        zeText = formatMem(currentAppSize) + " Mo : taken by BookPlayer app" + "\n" + "\n" +
                formatMem(currentAudiosSize) + " Mo : taken by Audios" + "\n" + "\n" +
                formatMem(currentLogsSize) + " Mo :  taken by Logs" + "\n" + "\n" +
                "----" + "\n" +
                formatMem(availableMegs2) + " Mo : left on the device" + "\n" + "\n" +
                formatMem(totalMemory) + " Mo : Total memory on the device."
                ;

        tv_head = findViewById(R.id.tv1_head);
        tv_body = findViewById(R.id.tv1_body);
        tv_head.setText("Physical Storage Memory");
        tv_body.setText(zeText);

        zeText =
                "Android SDK version = "  + Build.VERSION.SDK_INT + "\n" + "\n"
                        + "Android version = " + Build.VERSION.RELEASE + "\n" + "\n"
                        + "Android version name = " + getVersionName(Build.VERSION.SDK_INT) + "\n" + "\n"
                        + "---" + "\n" + "\n"
                        + "App version = " + BuildConfig.VERSION_CODE + "\n" + "\n"
                        + "App label = " + BuildConfig.VERSION_NAME
                ;

        tv_head = findViewById(R.id.tv2_head);
        tv_body = findViewById(R.id.tv2_body);
        tv_head.setText("Version");
        tv_body.setText(zeText);



    }

    public static String getVersionName(int sdkVersion) {
        switch (sdkVersion) {
            case Build.VERSION_CODES.BASE:
                return "Base";
            case Build.VERSION_CODES.BASE_1_1:
                return "Base 1.1";
            case Build.VERSION_CODES.CUPCAKE:
                return "Cupcake";
            case Build.VERSION_CODES.DONUT:
                return "Donut";
            case Build.VERSION_CODES.ECLAIR:
                return "Eclair";
            case Build.VERSION_CODES.ECLAIR_0_1:
                return "Eclair 0.1";
            case Build.VERSION_CODES.ECLAIR_MR1:
                return "Eclair MR1";
            case Build.VERSION_CODES.FROYO:
                return "Froyo";
            case Build.VERSION_CODES.GINGERBREAD:
                return "Gingerbread";
            case Build.VERSION_CODES.GINGERBREAD_MR1:
                return "Gingerbread MR1";
            case Build.VERSION_CODES.HONEYCOMB:
                return "Honeycomb";
            case Build.VERSION_CODES.HONEYCOMB_MR1:
                return "Honeycomb MR1";
            case Build.VERSION_CODES.HONEYCOMB_MR2:
                return "Honeycomb MR2";
            case Build.VERSION_CODES.ICE_CREAM_SANDWICH:
                return "Ice Cream Sandwich";
            case Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1:
                return "Ice Cream Sandwich MR1";
            case Build.VERSION_CODES.JELLY_BEAN:
                return "Jelly Bean";
            case Build.VERSION_CODES.JELLY_BEAN_MR1:
                return "Jelly Bean MR1";
            case Build.VERSION_CODES.JELLY_BEAN_MR2:
                return "Jelly Bean MR2";
            case Build.VERSION_CODES.KITKAT:
                return "KitKat";
            case Build.VERSION_CODES.KITKAT_WATCH:
                return "KitKat Watch";
            case Build.VERSION_CODES.LOLLIPOP:
                return "Lollipop";
            case Build.VERSION_CODES.LOLLIPOP_MR1:
                return "Lollipop MR1";
            case Build.VERSION_CODES.M:
                return "Marshmallow";
            case Build.VERSION_CODES.N:
                return "Nougat";
            case Build.VERSION_CODES.N_MR1:
                return "Nougat MR1";
            case Build.VERSION_CODES.O:
                return "Oreo";
            case Build.VERSION_CODES.O_MR1:
                return "Oreo MR1";
            case Build.VERSION_CODES.P:
                return "Pie";
            case Build.VERSION_CODES.Q:
                return "Android 10";
            case Build.VERSION_CODES.R:
                return "Android 11";
            case Build.VERSION_CODES.S:
                return "Android 12";
            case Build.VERSION_CODES.S_V2:
                return "Android 12.1";
            case Build.VERSION_CODES.TIRAMISU:
                return "Tiramisu";
            case Build.VERSION_CODES.UPSIDE_DOWN_CAKE:
                return "Upside Down Cake";
            default:
                return "Unknown";
        }
    }

}
