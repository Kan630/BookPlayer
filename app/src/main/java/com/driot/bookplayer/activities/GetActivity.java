package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.nav.BaseBottomNavActivity;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
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

        findViewById(R.id.bDirectLink).setOnClickListener(v -> clickDirectLink());
        findViewById(R.id.bDirectLinkIcon).setOnClickListener(v -> clickDirectLink());

        findViewById(R.id.bOpenEbooks).setOnClickListener(v -> clickEbooks());
        findViewById(R.id.bOpenEbooksIcon).setOnClickListener(v -> clickEbooks());
    }

    private void clickAudiobooks() {
        myLogI("--- user clicks AUDIOBOOKS ----");
        startActivity(new Intent(this, GetLibrivoxActivity.class));
    }
    private void clickOther() {
        myLogI("--- user clicks OTHER ----");
        startActivity(new Intent(this, GetOtherActivity.class));
    }

    private void clickDirectLink() {
        myLogI("--- user clicks DIRECT LINK ----");
        startActivity(new Intent(this, GetDirectLinkActivity.class));
    }

    private void clickEbooks() {
        myLogI("--- user clicks EBOOKS ----");
        startActivity(new Intent(this, GetEbookActivity.class));
    }

}
