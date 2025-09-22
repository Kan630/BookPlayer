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

        /*
        View topContainer = findViewById(R.id.topContainer);
        View bottomBar = findViewById(R.id.bottomBar);
        View contentContainer = findViewById(R.id.contentContainer);
        InsetHelper.applyEdgeToEdge(this, topContainer, bottomBar, contentContainer);

 */
       // InsetHelper.applyEdgeToEdge(this, null, null, null);


        Button bPod = findViewById(R.id.bOpenPodcasts);
        ImageButton bPodcastIcon = findViewById(R.id.bOpenPodcastsIcon);

        Button bAudio = findViewById(R.id.bOpenAudiobooks);
        ImageButton bAudiobookIcon = findViewById(R.id.bOpenAudiobooksIcon);

        Button bOther = findViewById(R.id.bOpenOther);
        ImageButton bOtherIcon = findViewById(R.id.bOpenOtherIcon);

        bPodcastIcon.setOnClickListener(v ->
                startActivity(new Intent(this, GetPodcastActivity.class)));
        bPod.setOnClickListener(v ->
                startActivity(new Intent(this, GetPodcastActivity.class)));

        bAudiobookIcon.setOnClickListener(v ->
                startActivity(new Intent(this, GetAudiobookActivity.class)));
        bAudio.setOnClickListener(v ->
                startActivity(new Intent(this, GetAudiobookActivity.class)));

        bOtherIcon.setOnClickListener(v ->
                startActivity(new Intent(this, GetOtherActivity.class)));
        bOther.setOnClickListener(v ->
                startActivity(new Intent(this, GetOtherActivity.class)));
    }
}
