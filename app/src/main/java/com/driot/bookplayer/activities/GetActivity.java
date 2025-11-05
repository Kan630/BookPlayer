package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.objects.OngoingTaskHost;
import com.driot.bookplayer.utils.log.LoggingActivity;

public class GetActivity extends LoggingActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_get);
        InsetHelper.apply(this);

        OngoingTaskHost.attach(
                this,
                R.id.topOverlayContainer,
                new Intent(this, AddResourceActivity.class)
        );

        findViewById(R.id.bOpenOther).setOnClickListener(v -> clickOther());
        findViewById(R.id.bOpenOtherIcon).setOnClickListener(v -> clickOther());

        findViewById(R.id.bOpenAudiobooks).setOnClickListener(v -> clickAudiobooks());
        findViewById(R.id.bOpenAudiobooksIcon).setOnClickListener(v -> clickAudiobooks());

        findViewById(R.id.bOpenPodcasts).setOnClickListener(v -> clickPodcasts());
        findViewById(R.id.bOpenPodcastsIcon).setOnClickListener(v -> clickPodcasts());

        findViewById(R.id.bOpenRadios).setOnClickListener(v -> clickRadios());
        findViewById(R.id.bOpenRadiosIcon).setOnClickListener(v -> clickRadios());

    }

    private void clickPodcasts() {
        myLogI("--- user clicks PODCASTS ----");
        startActivity(new Intent(this, GetPodcastActivity.class));
    }
    private void clickAudiobooks() {
        myLogI("--- user clicks AUDIOBOOKS ----");
        startActivity(new Intent(this, GetAudiobookActivity.class));
    }
    private void clickRadios() {
        myLogI("--- user clicks RADIOS ----");
        startActivity(new Intent(this, GetRadioActivity.class));
    }
    private void clickOther() {
        myLogI("--- user clicks OTHER ----");
        startActivity(new Intent(this, GetOtherActivity.class));
    }

}
