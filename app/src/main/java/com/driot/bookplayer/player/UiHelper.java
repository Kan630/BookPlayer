package com.driot.bookplayer.player;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.utils.Tonio;
import com.google.android.material.slider.Slider;

import java.util.regex.Pattern;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public class UiHelper {

    /* ----------------------------- Slider binding ----------------------------- */

    /** Holds listeners + state, and provides detach() */
    public static final class SliderBinding {
        private final Slider slider;
        private final TextView tvTime;
        private final PlaybackViewModel vm;
        private final Slider.OnChangeListener changeListener;
        private final Slider.OnSliderTouchListener touchListener;

        private SliderBinding(@NonNull Slider slider,
                              @NonNull TextView tvTime,
                              @NonNull PlaybackViewModel vm) {
            this.slider = slider;
            this.tvTime = tvTime;
            this.vm = vm;

            // Bubble formatter (mm:ss) while dragging
            slider.setLabelFormatter(value -> Tonio.formatMmSs((long) value * 1000L));

            // Change listener for live preview while scrubbing
            this.changeListener = (s, value, fromUser) -> {
                if (!fromUser) return;
                long previewMs = (long) value * 1000L;
                PlaybackUiState st = vm.getState().getValue();
                long dur = (st != null) ? st.durationMs : 0L;
                tvTime.setText(Tonio.formatMmSs(previewMs) + " / " + Tonio.formatMmSs(dur));
            };
            slider.addOnChangeListener(changeListener);

            // Touch listener to toggle "userSeeking" and commit seek on release
            this.touchListener = new Slider.OnSliderTouchListener() {
                @Override public void onStartTrackingTouch(@NonNull Slider s) {
                    setUserSeeking(slider, true);
                }
                @Override public void onStopTrackingTouch(@NonNull Slider s) {
                    setUserSeeking(slider, false);
                    myLogI("---- user finished SLIDER seek ----");
                    vm.seekTo((long) s.getValue() * 1000L);
                }
            };
            slider.addOnSliderTouchListener(touchListener);
        }

        @MainThread
        public void detach() {
            try {
                slider.removeOnChangeListener(changeListener);
                slider.removeOnSliderTouchListener(touchListener);
            } catch (Throwable ignore) { }
            slider.setTag(R.id.tag_slider_binding, null);
            setUserSeeking(slider, false);
        }
    }

    /** Attach and return a binding (idempotent per-View). */
    @MainThread
    public static SliderBinding bindSeekBar(@NonNull Slider sb,
                                            @NonNull TextView tvTime,
                                            @NonNull PlaybackViewModel vm) {
        Object existing = sb.getTag(R.id.tag_slider_binding);
        if (existing instanceof SliderBinding) {
            return (SliderBinding) existing; // already bound
        }
        SliderBinding binding = new SliderBinding(sb, tvTime, vm);
        sb.setTag(R.id.tag_slider_binding, binding);
        return binding;
    }

    @MainThread
    public static void unbindSeekBar(@NonNull Slider sb) {
        Object existing = sb.getTag(R.id.tag_slider_binding);
        if (existing instanceof SliderBinding) {
            ((SliderBinding) existing).detach();
        }
    }

    /* Keep the userSeeking flag local to the Slider via a tag */
    private static void setUserSeeking(@NonNull Slider s, boolean val) {
        s.setTag(R.id.tag_user_seeking, val ? Boolean.TRUE : Boolean.FALSE);
    }
    private static boolean isUserSeeking(@NonNull Slider s) {
        Object v = s.getTag(R.id.tag_user_seeking);
        return (v instanceof Boolean) && (Boolean) v;
    }

    /* ----------------------------- UI fill basics ----------------------------- */

    public static void FillUiBasic(
            @NonNull PlaybackUiState s,
            @Nullable ProgressBar progressBar,
            @Nullable ImageButton ibPlayPause,
            @Nullable TextView tvTitle,
            @Nullable TextView tvSubTitle,
            @Nullable TextView tvTime,
            @Nullable ImageView ivCover,
            @Nullable Slider sbSeek
    ) {
        //myLogD(s.toString());

        if (tvTitle!=null && tvSubTitle!=null) {
            setTitleAndSubtitle(tvTitle, tvSubTitle, s.title, s.subTitle);
        }

        if (progressBar != null && ibPlayPause!=null) {
            boolean buffering = !"READY".equalsIgnoreCase(s.loadPhase);
            if (buffering) {
                progressBar.setVisibility(View.VISIBLE);
                ibPlayPause.setVisibility(View.GONE);
            } else {
                progressBar.setVisibility(View.GONE);
                ibPlayPause.setVisibility(View.VISIBLE);
            }
        }
        if (ibPlayPause!=null) {
            ibPlayPause.setImageResource(
                    s.playing ? R.drawable.ic_media_pause_24 : R.drawable.ic_media_play_24
            );
        }

        // Only drive the slider/time when NOT scrubbing
        if (sbSeek==null || !isUserSeeking(sbSeek)) {
            final long pos = s.positionMs;
            final long dur = s.durationMs;

            if (dur > 0) {
                float durSec = dur / 1000f;
                float posSec = Math.min(pos, dur) / 1000f;

                if (sbSeek!=null) if (sbSeek.getValueTo() != durSec) sbSeek.setValueTo(durSec);
                if (sbSeek!=null) if (sbSeek.getValue() != posSec) sbSeek.setValue(posSec);

                if (tvTime!=null) tvTime.setText(Tonio.formatMmSs(pos) + " / " + Tonio.formatMmSs(dur));
            } else {
                // Unknown duration
                if (sbSeek!=null) if (sbSeek.getValueTo() != 1000f) sbSeek.setValueTo(1000f);
                if (sbSeek!=null) if (sbSeek.getValue() != 0f) sbSeek.setValue(0f);
                if (tvTime!=null) tvTime.setText("--:-- / --:--");
            }
        }

        if (ivCover!=null) {
            if (s.cover != null) {
                ivCover.setVisibility(View.VISIBLE);
                Glide.with(ivCover.getContext()).load(s.cover).into(ivCover);
            } else {
                ivCover.setVisibility(View.GONE);
            }
        }
    }

    private static void setTitleAndSubtitle(
            @NonNull TextView tvTitle,
            @NonNull TextView tvSubTitle,
            String folderName,
            String displayName
    ) {
        tvTitle.setText(folderName != null ? folderName : "");

        String subTitle = displayName != null ? displayName : "";
        if (folderName != null && !folderName.isEmpty()) {
            subTitle = subTitle.replaceFirst("^" + Pattern.quote(folderName), "").trim();
        }

        if (subTitle.isEmpty() || (folderName != null && subTitle.equals(folderName))) {
            tvSubTitle.setVisibility(View.GONE);
            // If Title default is single-line, allow 2 lines when no subtitle
            if (tvTitle.getMaxLines() == 1) {
                tvTitle.setSingleLine(false);
                tvTitle.setMaxLines(2);
            }
        } else {
            tvSubTitle.setText(subTitle);
            tvSubTitle.setVisibility(View.VISIBLE);
            tvTitle.setSingleLine(true);
            tvTitle.setMaxLines(1);
        }
    }

    public static PendingIntent navigateToMain(Context context) {
        final int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP;
        return PendingIntent.getActivity(
                context,
                0,
                new Intent(context, com.driot.bookplayer.activities.MainActivity.class)
                        .addFlags(flags),
                flags
        );
    }


    public static PendingIntent navigateToActivity(Context context) {
        final int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        androidx.core.app.TaskStackBuilder tsb = androidx.core.app.TaskStackBuilder.create(context);
        // 1) Always start at Main
        tsb.addNextIntent(new Intent(context, com.driot.bookplayer.activities.MainActivity.class));

        // 2) If multiple tracks, insert the track list screen before PlayActivity
        PlayList pl = PlayList.getInstance();
        ZikFile z = (pl != null) ? pl.getZikFile() : null;
        int folderId = (z != null) ? z.getIdFolder() : -1;
        if (folderId > 0 && pl.getSize() > 1) {
            Intent trackList = new Intent(context, com.driot.bookplayer.activities.ZikFileActivity.class)
                    .putExtra(Intents.EXTRA_FOLDER_ID, folderId);
            tsb.addNextIntent(trackList);
        }

        // 3) Finally PlayActivity (singleTop/clearTop like you already do)
        tsb.addNextIntent(new Intent(context, PlayActivity.class)
                .putExtra(Intents.EXTRA_AUTOPLAY, false));

        return tsb.getPendingIntent(0, flags);
    }

    public static PendingIntent navigateToRadioActivity(Context context) {
            /*
        AppDatabase.databaseReadExecutor.execute(() -> {
            //TODO boolean hasRadioFavorite = AppDatabase.getDatabase(this).radioDao()...
            boolean hasRadioFavorite = false;
            if (hasRadioFavorite) {
                context.startActivity(new Intent(context.getApplicationContext(), RadioFavoritesActivity.class));
            } else {
            }
            );
        });
             */
            final int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
            return PendingIntent.getActivity(
                    context,
                    0,
                    new Intent(context, com.driot.bookplayer.activities.GetRadioActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    flags
            );
    }


}
