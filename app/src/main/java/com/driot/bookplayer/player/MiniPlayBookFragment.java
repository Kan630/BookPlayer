package com.driot.bookplayer.player;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.TtsReaderActivity;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.utils.log.LoggingFragment;
import com.google.android.material.slider.Slider;

public class MiniPlayBookFragment extends LoggingFragment {
    private PlaybackViewModel vm;
    private ImageView ivCover;
    private TextView tvTitle, tvSubTitle, tvMiniTime;
    private Slider sbMiniSeek;
    private ImageButton ibPrev, ibPlayPause, ibNext, ibClose;
    private Boolean lastIsMusic = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        return inf.inflate(R.layout.fragment_mini_play_book, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        tvTitle = v.findViewById(R.id.tvTitle);
        tvSubTitle = v.findViewById(R.id.tvSubTitle);
        tvMiniTime = v.findViewById(R.id.tvMiniTime);
        sbMiniSeek = v.findViewById(R.id.sbMiniSeek);
        ibPrev = v.findViewById(R.id.bMiniBackward);
        ibPlayPause = v.findViewById(R.id.bMiniPlayPause);
        ibNext = v.findViewById(R.id.bMiniForward);
        ibClose = v.findViewById(R.id.btnClose);
        ivCover = v.findViewById(R.id.ivCover);

        ibPrev.setImageResource(R.drawable.ic_media_fast_rewind_24);
        ibNext.setImageResource(R.drawable.ic_media_fast_forward_24);
        ibClose.setImageResource(R.drawable.ic_media_close_24);

        vm = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);

        UiHelper.bindSeekBar(sbMiniSeek, tvMiniTime, vm);

        vm.getState().observe(getViewLifecycleOwner(), s -> {
            if (s == null)
                return;
            UiHelper.FillUiBasic(s, null, ibPlayPause, tvTitle, tvSubTitle, tvMiniTime, ivCover, sbMiniSeek, null,
                    false);
            updateSkipIcons();
        });

        v.setOnClickListener(_x -> {
            myLogI("---- user clicks on mini player root ----");
            PlaybackCommands.resetLastUserAction(requireContext());
            startActivity(new Intent(requireContext(), PlayActivity.class));
        });

        ibPrev.setOnClickListener(_v -> {
            myLogI("---- user press PREV button ----");
            PlaybackCommands.resetLastUserAction(requireContext());
            vm.prev();
        });
        ibPlayPause.setOnClickListener(_v -> {
            myLogI("---- user press PlayPause button ----");
            PlaybackCommands.resetLastUserAction(requireContext());
            vm.playPause();
        });
        ibNext.setOnClickListener(_v -> {
            myLogI("---- user press NEXT button ----");
            PlaybackCommands.resetLastUserAction(requireContext());
            vm.next();
        });
        ibClose.setOnClickListener(_v -> {
            myLogI("---- user press CLOSE button ----");
            PlaybackCommands.resetLastUserAction(requireContext());
            vm.stop();
            if (getActivity() instanceof TtsReaderActivity) {
                myLog("MiniPlayBookFragment: finish TtsReaderActivity and signal PlayActivity to finish too");
                LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(new Intent(Intents.ACTION_FINISH_PLAYER_ACTIVITIES));
                getActivity().finish();
            }
        });
    }

    /** Swaps the prev/next mini-player icons between seek (fast rewind/forward) and actual
     * track skip, matching the currently loaded folder's type. Cheap no-op once per unchanged
     * value since this runs on every playback state tick. */
    private void updateSkipIcons() {
        PlayList pl = PlayList.getInstance();
        Folder f = (pl != null) ? pl.getFolder() : null;
        boolean isMusic = f != null && Var.PLAY_TYPE_MUSIC.equals(f.playType);
        if (lastIsMusic != null && lastIsMusic == isMusic)
            return;
        lastIsMusic = isMusic;
        ibPrev.setImageResource(isMusic ? R.drawable.ic_skip_previous_24px : R.drawable.ic_media_fast_rewind_24);
        ibNext.setImageResource(isMusic ? R.drawable.ic_skip_next_24px : R.drawable.ic_media_fast_forward_24);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (sbMiniSeek != null) {
            UiHelper.unbindSeekBar(sbMiniSeek);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        MediaControllerHolder.attachTo(this.getActivity());
        MediaControllerHolder.ensureConnected(requireContext().getApplicationContext());
    }

    @Override
    public void onStop() {
        MediaControllerHolder.detachFrom(this.getActivity());
        super.onStop();
    }

}
