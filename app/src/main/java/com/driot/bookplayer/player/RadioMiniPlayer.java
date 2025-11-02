package com.driot.bookplayer.player;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.ExoPlayer;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;

public class RadioMiniPlayer {

    public interface Listener {
        void onError(String message);
    }

    private final Context ctx;
    private final View miniRoot;
    private final ImageView ivCover;
    private final TextView tvTitle;
    private final ProgressBar progress;
    private final ImageButton btnPlayPause;

    private ExoPlayer exo;
    private Listener listener;

    public RadioMiniPlayer(@NonNull Context ctx,
                           @NonNull View miniRoot,
                           @NonNull ImageView ivCover,
                           @NonNull TextView tvTitle,
                           @NonNull ProgressBar progress,
                           @NonNull ImageButton btnPlayPause) {
        this.ctx = ctx.getApplicationContext();
        this.miniRoot = miniRoot;
        this.ivCover = ivCover;
        this.tvTitle = tvTitle;
        this.progress = progress;
        this.btnPlayPause = btnPlayPause;

        exo = new ExoPlayer.Builder(this.ctx).build();
        exo.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                /* handleAudioFocus = */ true);

        exo.addListener(new androidx.media3.common.Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == androidx.media3.common.Player.STATE_BUFFERING) {
                    setLoading(true);
                } else if (state == androidx.media3.common.Player.STATE_READY) {
                    setLoading(false);
                    updatePlayIcon(exo.getPlayWhenReady());
                } else if (state == androidx.media3.common.Player.STATE_ENDED) {
                    setLoading(false);
                    updatePlayIcon(false);
                }
            }
            @Override public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayIcon(isPlaying);
            }
            @Override public void onPlayerError(@NonNull PlaybackException error) {
                setLoading(false);
                updatePlayIcon(false);
                if (listener != null) listener.onError(error.getMessage());
            }
        });

        btnPlayPause.setOnClickListener(v -> toggle());
    }

    public void setListener(Listener l) { this.listener = l; }

    @MainThread
    public void play(@NonNull String streamUrl, @NonNull String title, String faviconUrl) {
        if (TextUtils.isEmpty(streamUrl)) return;

        tvTitle.setText(title.isEmpty() ? "Radio" : title);
        if (!TextUtils.isEmpty(faviconUrl)) {
            Glide.with(ivCover).load(faviconUrl)
                    .placeholder(R.drawable.ic_radio_24px)
                    .error(R.drawable.ic_radio_24px)
                    .into(ivCover);
        } else {
            ivCover.setImageResource(R.drawable.ic_radio_24px);
        }

        miniRoot.setVisibility(View.VISIBLE);
        setLoading(true);

        exo.stop();
        exo.clearMediaItems();
        exo.setMediaItem(MediaItem.fromUri(streamUrl));
        exo.prepare();
        exo.play();
    }

    @MainThread
    public void toggle() {
        if (exo == null) return;
        if (exo.isPlaying()) {
            exo.pause();
        } else {
            exo.play();
        }
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnPlayPause.setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
    }

    private void updatePlayIcon(boolean playing) {
        btnPlayPause.setImageResource(playing ? R.drawable.ic_media_pause_24 : R.drawable.ic_media_play_24);
    }

    public void release() {
        if (exo != null) {
            try {
                exo.release();
            } finally {
                exo = null;
            }
        }
        miniRoot.setVisibility(View.GONE);
    }
}
