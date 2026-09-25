package com.driot.bookplayer.player;

import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.lifecycle.ViewModelProvider;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.utils.log.BaseActivity;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Full-screen player: a thin host around {@link PlayerFragment}, which holds the whole player UI
 * (so it can also be shown in a pane next to a book's track list on tablets). What stays here is
 * what only makes sense for a standalone screen: window insets, the orientation lock, back
 * handling and binding the media controller to this Activity.
 */
@AndroidEntryPoint
public class PlayActivity extends BaseActivity implements PlayerFragment.Host {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        if (PlayList.getInstance() == null || PlayList.getInstance().getFolder() == null) {
            myLogEE(null, "nothing to play: PlayList.getInstance() or its folder == null");
            finish();
            return;
        }

        setContentView(R.layout.activity_play_host);
        InsetHelper.apply(this);

        if (Option.getScreenOrientationLock()) {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        }

        if (b == null) {
            getSupportFragmentManager().beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.player_container, PlayerFragment.class, null)
                    .commit();
        }

        PlaybackViewModel vm = new ViewModelProvider(this).get(PlaybackViewModel.class);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                myLogI("--- user press BACK from PLAY ACTIVITY --- just finish, maybe todo : integrate with NavStacks and BaseActivity");
                PlaybackUiState s = vm.getState().getValue();
                if (s == null) {
                    myLog("s == null");
                    vm.stop();
                } else {
                    if (!s.playing) {
                        myLogD("back press and not playing => stop vm");
                        vm.stop();
                    }
                }
                //if we use code below, we could force a return to ZikFileActivity by setting getSectionParent()
                //setEnabled(false); // VERY IMPORTANT
                //getOnBackPressedDispatcher().onBackPressed();
                finish();
            }
        });
    }

    @Override
    public void onPlayerCloseRequested() {
        finish();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Bind controller to this Activity and ensure the browser is up
        MediaControllerHolder.attachTo(this);
        MediaControllerHolder.ensureConnected(getApplicationContext());
    }

    @Override
    protected void onStop() {
        MediaControllerHolder.detachFrom(this);
        super.onStop();
    }
}
