package com.driot.bookplayer.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.player.PlaybackViewModel;
import com.driot.bookplayer.tts.TtsReaderController;
import com.driot.bookplayer.utils.log.LoggingFragment;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Fullscreen TTS reading view - MainActivity hides the bottom nav bar / mini-player while this
 * destination is on top (see its NavController.OnDestinationChangedListener), matching the old
 * standalone TtsReaderActivity's displayAppNavBar()==false.
 */
@AndroidEntryPoint
public class TtsReaderFragment extends LoggingFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_tts_reader, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView rv = view.findViewById(R.id.rvTtsText);

        PlaybackViewModel vm = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);

        TtsReaderController controller = new TtsReaderController(requireContext(), rv);
        controller.bind(getViewLifecycleOwner(), vm);

        View miniNowPlaying = requireActivity().findViewById(R.id.miniNowPlaying);
        if (miniNowPlaying != null) {
            miniNowPlaying.setVisibility(Option.getTtsFullscreenControls() ? View.VISIBLE : View.GONE);
        }
    }

    // Convenience launcher - navigates within MainActivity's own Library graph instead of
    // starting a separate Activity.
    public static void start(android.content.Context ctx) {
        android.content.Intent intent = new android.content.Intent(ctx, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_NAVIGATE_TO_TTS_READER, true);
        ctx.startActivity(intent);
    }
}
