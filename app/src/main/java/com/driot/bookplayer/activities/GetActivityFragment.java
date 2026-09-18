package com.driot.bookplayer.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.Navigation;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.log.LoggingFragment;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class GetActivityFragment extends LoggingFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_get, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.bOpenOther).setOnClickListener(v -> clickOther(view));
        view.findViewById(R.id.bOpenOtherIcon).setOnClickListener(v -> clickOther(view));

        view.findViewById(R.id.bOpenAudiobooks).setOnClickListener(v -> clickAudiobooks(view));
        view.findViewById(R.id.bOpenAudiobooksIcon).setOnClickListener(v -> clickAudiobooks(view));

        view.findViewById(R.id.bDirectLink).setOnClickListener(v -> clickDirectLink(view));
        view.findViewById(R.id.bDirectLinkIcon).setOnClickListener(v -> clickDirectLink(view));

        view.findViewById(R.id.bOpenEbooks).setOnClickListener(v -> clickEbooks(view));
        view.findViewById(R.id.bOpenEbooksIcon).setOnClickListener(v -> clickEbooks(view));
    }

    private void clickAudiobooks(View view) {
        myLogI("--- user clicks AUDIOBOOKS ----");
        Navigation.findNavController(view).navigate(R.id.getLibrivoxFragment);
    }
    private void clickOther(View view) {
        myLogI("--- user clicks OTHER ----");
        Navigation.findNavController(view).navigate(R.id.getOtherFragment);
    }

    private void clickDirectLink(View view) {
        myLogI("--- user clicks DIRECT LINK ----");
        Navigation.findNavController(view).navigate(R.id.getDirectLinkFragment);
    }

    private void clickEbooks(View view) {
        myLogI("--- user clicks EBOOKS ----");
        Navigation.findNavController(view).navigate(R.id.getEbookFragment);
    }

}
