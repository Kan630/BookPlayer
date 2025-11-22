package com.driot.bookplayer.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.NetworkStatusRowController;
import com.driot.bookplayer.radio.RadioStation;
import com.driot.bookplayer.utils.Tonio;

public class RadioStationActivity extends BaseBottomNavActivity {

    public static final String EXTRA_STATION_UUID = "stationuuid";

    private NetworkStatusRowController networkStatusController;

    private ImageView ivCover;
    private TextView tvName;
    private TextView tvSubtitle;
    private TextView tvTags;
    private TextView tvUrl;
    private TextView tvHomepage;
    private TextView tvStats;

    @Override protected int getNavId() { return R.id.nav_radio; }
    @Override protected int getLayoutResId() { return R.layout.activity_radio_station; }
    @Override protected boolean enableOngoingTaskOverlay() { return true; }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        String stationUuid = getIntent().getStringExtra(EXTRA_STATION_UUID);
        if (stationUuid == null || stationUuid.isEmpty()) {
            myLogE("RadioStationActivity started without stationuuid extra");
            finish();
            return;
        }

        ivCover    = findViewById(R.id.ivCover);
        tvName     = findViewById(R.id.tvName);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        tvTags     = findViewById(R.id.tvTags);
        tvUrl      = findViewById(R.id.tvUrl);
        tvHomepage = findViewById(R.id.tvHomepage);
        tvStats    = findViewById(R.id.tvStats);

        AppDatabase.databaseReadExecutor.execute(() -> {
            RadioStation radioStation = AppDatabase.getDatabase(this).radioStationDao().findByUuid(stationUuid);
            runOnUiThread(() -> {
                if (radioStation==null) {
                    myLogE("RadioStationActivity started with stationuuid extra, but not found in DB");
                    finish();
                } else {
                    bindStation(radioStation);
                }
            });
        });

        View networkRow = findViewById(R.id.includeNetworkStatus);
        networkStatusController = new NetworkStatusRowController(this, networkRow);

    }

    private void bindStation(@Nullable RadioStation f) {
        if (f == null) {
            myLogW("RadioStationActivity.bindStation: station not found in DB");
            tvName.setText(getString(R.string.no_result));
            return;
        }

        // Title
        tvName.setText(safe(f.name));

        // Subtitle: Country · Language
        String country  = safe(f.country);
        String language = safe(f.language);
        if (!country.isEmpty() || !language.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            if (!country.isEmpty()) sb.append(country);
            if (!country.isEmpty() && !language.isEmpty()) sb.append(" · ");
            if (!language.isEmpty()) sb.append(language);
            tvSubtitle.setText(sb.toString());
            tvSubtitle.setVisibility(View.VISIBLE);
        } else {
            tvSubtitle.setVisibility(View.GONE);
        }

        // Cover
        String favicon = safe(f.favicon);
        if (!favicon.isEmpty()) {
            Glide.with(this)
                    .load(favicon)
                    .placeholder(R.drawable.ic_radio_24px)
                    .error(R.drawable.ic_radio_24px)
                    .into(ivCover);
        } else {
            ivCover.setImageResource(R.drawable.ic_radio_24px);
        }

        // Tags
        String tags = safe(f.tags);
        if (!tags.isEmpty()) {
            tvTags.setText(tags);
            tvTags.setVisibility(View.VISIBLE);
        } else {
            tvTags.setText("");
            tvTags.setVisibility(View.GONE);
        }

        // URL + homepage
        tvUrl.setText(safe(f.url_resolved != null && !f.url_resolved.isEmpty() ? f.url_resolved : f.url));
        //tvHomepage.setText(safe(f.homepage));
        tvHomepage.setVisibility(View.GONE);

        // Simple stats line (clickcount, etc. – adapt to your fields)
        StringBuilder stats = new StringBuilder();
        if (f.clickcount > 0) {
            stats.append(getString(R.string.clicks_2pt, f.clickcount)); // e.g. "Clicks: %d"
        }
        stats.append("\ncodec : " + f.codec);
        stats.append("\nbitrate : " + f.bitrate);
        stats.append("\n");
        stats.append("\nadded in app : " + Tonio.formatDateForDisplay(f.date_added));
        stats.append("\nlast played : " + (f.date_last_played==null ? "never" : Tonio.formatDateForDisplay(f.date_last_played)));
        if (!TextUtils.isEmpty(stats.toString())) {
            tvStats.setText(stats.toString());
            tvStats.setVisibility(View.VISIBLE);
        } else {
            tvStats.setVisibility(View.GONE);
        }
    }

    private static String safe(@Nullable String s) {
        return s == null ? "" : s.trim();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (networkStatusController != null) {
            networkStatusController.start();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (networkStatusController != null) {
            networkStatusController.stop();
        }
    }
}
