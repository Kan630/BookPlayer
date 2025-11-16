package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.helpers.InsetHelper;

public class GetActivity extends BaseBottomNavActivity {

    @Override protected int getNavId() { return R.id.nav_add; }
    @Override protected int getLayoutResId() { return R.layout.activity_get; }
    @Override protected boolean enableOngoingTaskOverlay() { return true; }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        findViewById(R.id.bOpenOther).setOnClickListener(v -> clickOther());
        findViewById(R.id.bOpenOtherIcon).setOnClickListener(v -> clickOther());

        findViewById(R.id.bOpenAudiobooks).setOnClickListener(v -> clickAudiobooks());
        findViewById(R.id.bOpenAudiobooksIcon).setOnClickListener(v -> clickAudiobooks());
    }

    private void clickAudiobooks() {
        myLogI("--- user clicks AUDIOBOOKS ----");
        startActivity(new Intent(this, GetAudiobookActivity.class));
    }
    private void clickOther() {
        myLogI("--- user clicks OTHER ----");
        startActivity(new Intent(this, GetOtherActivity.class));
    }

}
