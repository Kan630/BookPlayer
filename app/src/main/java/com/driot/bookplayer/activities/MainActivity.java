    package com.driot.bookplayer.activities;

    import android.annotation.SuppressLint;
    import android.app.Activity;
    import android.content.BroadcastReceiver;
    import android.content.ComponentName;
    import android.content.Context;
    import android.content.Intent;

    import android.content.IntentFilter;
    import android.content.ServiceConnection;
    import android.net.Uri;
    import android.os.Bundle;
    import android.os.IBinder;
    import android.view.Menu;
    import android.view.MenuItem;

    import androidx.activity.OnBackPressedCallback;
    import androidx.annotation.NonNull;

    import androidx.annotation.Nullable;
    import androidx.appcompat.widget.Toolbar;
    import androidx.lifecycle.ViewModelProvider;
    import androidx.localbroadcastmanager.content.LocalBroadcastManager;
    import androidx.recyclerview.widget.GridLayoutManager;
    import androidx.recyclerview.widget.RecyclerView;

    import com.driot.bookplayer.MyApp;
    import com.driot.bookplayer.R;
    import com.driot.bookplayer.adapter.FoldersRVAdapter;
    import com.driot.bookplayer.db.AppDatabase;
    import com.driot.bookplayer.global.Intents;
    import com.driot.bookplayer.global.Option;
    import com.driot.bookplayer.global.Var;
    import com.driot.bookplayer.helpers.InsetHelper;
    import com.driot.bookplayer.helpers.ViewHelper;
    import com.driot.bookplayer.objects.OngoingTaskHost;
    import com.driot.bookplayer.player.PlayList;
    import com.driot.bookplayer.player.MediaService;
    import com.driot.bookplayer.helpers.InfoHelper;
    import com.driot.bookplayer.player.PlaybackUiState;
    import com.driot.bookplayer.player.PlaybackViewModel;
    import com.driot.bookplayer.utils.InAppMsgManager;
    import com.driot.bookplayer.utils.KanMail;
    import com.driot.bookplayer.utils.log.LoggingActivity;

    public class MainActivity extends BaseBottomNavActivity {

        private RecyclerView recyclerView;
        private FoldersRVAdapter adapter;
        private MainViewModel mainVm;

        Toolbar toolbar;
        private static final int REQUEST_CODE_OPTION = 34343;

        private boolean HasBeenProposedToOpenFile;
        private static boolean infoAlreadyShown = false;

        @Override
        protected void onSaveInstanceState(@NonNull Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putBoolean("HasBeenProposedToOpenFile", HasBeenProposedToOpenFile);
        }

        @Override
        protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
            super.onRestoreInstanceState(savedInstanceState);
            HasBeenProposedToOpenFile = savedInstanceState.getBoolean("HasBeenProposedToOpenFile", false);
        }
/*
        // Just in case we are here while we shouldn't, because isPlaying...
        MediaService audioService;
        private final ServiceConnection audioServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName className, IBinder service) {
                MediaService.BackgroundBinder binder = (MediaService.BackgroundBinder) service;
                audioService = binder.getService();
                if (audioService.isPlaying()) {
                    myLogW("AudioService.isPlaying => return to PlayActivity");
                    if (PlayList.getInstance() == null) {
                        myLogEE(null,"AudioService.isPlaying => return to PlayActivity.... PlayList.getInstance() == null");
                    } else {
                        startActivity(new Intent(MainActivity.this, PlayActivity.class));
                    }
                }
            }
            @Override
            public void onServiceDisconnected(ComponentName arg0) {
            }
        };

 */

        private final BroadcastReceiver inAppMsgRx = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                myLogD("broadcast received : inAppMsgRx");
                InAppMsgManager.maybeShowBestMessage(MainActivity.this, getString(R.string.app_name));
            }
        };

        @Override protected int getNavId() { return R.id.nav_library; }
        @Override protected int getLayoutResId() { return R.layout.activity_main; }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            //setContentView(R.layout.activity_main);
            InsetHelper.apply(this);

            if (savedInstanceState == null && !infoAlreadyShown) {
                InfoHelper.printSomeStuffAboutDevice(this);
                infoAlreadyShown = true;
            }

            //ongoing book load ?
            OngoingTaskHost.attach(
                    this,
                    R.id.topOverlayContainer,
                    new Intent(this, AddResourceActivity.class)); // tap => open details

            //toolbar
            toolbar = findViewById(R.id.toolbar);
            try {
                setSupportActionBar(toolbar); //si ca plante, check le color theme saved ???
            } catch (Exception e) {
                myLogEE(e,"Action bar error"); // on a Samsung S20 FE, android 13
            }
            toolbar.setLogo(R.mipmap.ic_launcher);
            toolbar.setLogo(R.mipmap.ic_launcher);

            recyclerView = findViewById(R.id.recyclerview_folders);
            if (recyclerView != null) {
                int span = getResources().getInteger(R.integer.classic_grid_span);
                GridLayoutManager glm = new GridLayoutManager(this, span);
                recyclerView.setLayoutManager(glm);
                recyclerView.setHasFixedSize(true);
                recyclerView.addItemDecoration(new ViewHelper.SpacesItemDecoration(ViewHelper.dp(this,Var.GRID_LAYOUT_SPACER)));
            }

            adapter = new FoldersRVAdapter(this);
            recyclerView.setAdapter(adapter);

            PlaybackViewModel playbackVm = new ViewModelProvider(this).get(PlaybackViewModel.class);
            adapter.connectPlayback(this, playbackVm.getState()); // adapter observe playback (highlight)
            //getFolders();

            mainVm = new ViewModelProvider(this).get(MainViewModel.class);
            mainVm.getFolders().observe(this, folders -> {
                if (folders == null) return;
                adapter.submitList(folders);
            });
            // Show empty prompt exactly once
            mainVm.getShowEmptyPrompt().observe(this, show -> {
                if (Boolean.TRUE.equals(show)) {
                    startActivity(new Intent(getApplicationContext(), GetActivity.class));
                    mainVm.markEmptyPromptShown();
                }
            });
            // One-shot scroll-to-top request
            mainVm.getScrollToTopEvent().observe(this, evt -> {
                if (evt == null) return;
                if (evt.getContentIfNotHandled() == null) return;
                recyclerView.post(() -> {
                    recyclerView.post(() -> {
                        recyclerView.smoothScrollToPosition(0);
                    });
                });
            });
            boolean wantScroll = getIntent() != null && getIntent().getBooleanExtra("scrollToTop", false);
            if (wantScroll && mainVm != null) {
                // either emit now or the list observer will run soon; both are fine
                mainVm.requestScrollToTopNow();
            }

            //if we quit app, check option => should let music continue =>if no, kill service
            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    myLogI("--- USER CLICK BACK from MAIN --- (system button)");
                    if (Option.getStopAudioIfUserClosesApp()) {
                        startService(
                                new Intent(MainActivity.this, MediaService.class)
                                        .setAction(Intents.EXTRA_CMD_STOP)
                                        .putExtra(Intents.EXTRA_CALLER, "press back from MainActivity"));
                    }
                    finish();
                }
            });

            //InAppMsgManager.deleteInAppMsgCache(this);
            MyApp.getPeriodicTaskManager(this).start(); // safe
            InAppMsgManager.maybeShowBestMessage(this, "message");

            //Option.setTtsVoice("system"); //reset
        }

        @Override protected void onNewIntent(Intent intent) {
            super.onNewIntent(intent);
            setIntent(intent);
            if (intent.getBooleanExtra("forceRefresh", false) && mainVm != null) {
                myLog("forceRefresh");
                mainVm.forceRefresh();
            }
            if (intent.getBooleanExtra("scrollToTop", false) && mainVm != null) {
                myLog("scrollToTop");
                mainVm.requestScrollToTopNow();
            }
        }

        @Override
        protected void onResume() {
            super.onResume();
            sendBroadcast(new Intent(Intents.ACTION_PING_UI));
            LocalBroadcastManager.getInstance(this).registerReceiver(inAppMsgRx, new IntentFilter(InAppMsgManager.ACTION_CACHE_UPDATED));        // Et tente immédiatement avec le cache courant
            InAppMsgManager.maybeShowBestMessage(this, getString(R.string.app_name));
            PlaybackViewModel playbackVm = new ViewModelProvider(this).get(PlaybackViewModel.class);
            PlaybackUiState s = playbackVm.getState().getValue();
            if (s != null && s.folderId > 0) {
                mainVm.requestScrollToTopForFolder(s.folderId);
            }
        }

        @Override protected void onPause() {
            super.onPause();
            LocalBroadcastManager.getInstance(this).unregisterReceiver(inAppMsgRx);
        }

        @SuppressLint("RestrictedApi")
        @Override
        public boolean onCreateOptionsMenu(Menu menu) {
            myLogD("onCreateOptionsMenu()");
            getMenuInflater().inflate(R.menu.action_bar, menu);
            return super.onCreateOptionsMenu(menu);
        }

        @Override
        public boolean onPrepareOptionsMenu(Menu menu) {
            MenuItem seeLogItem = menu.findItem(R.id.menu_seelog);
            if (seeLogItem != null) {
                boolean showLog = Option.getTechLog();
                seeLogItem.setVisible(showLog);
            }
            return super.onPrepareOptionsMenu(menu);
        }

        @Override
        public boolean onOptionsItemSelected(@NonNull MenuItem item) {
            int itemId = item.getItemId();
            if (itemId == R.id.action_menu_three_dot) {
            } else if (itemId == R.id.menu_settings) {
                myLogI("--- USER clicks MENU : SETTINGS ---");
                this.getSharedPreferences(Option.SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit().putBoolean("ACTIVITY_OPTION_HAS_RESULT", false).apply(); //trick to reload MainActivity if color changed in OptionActivity, by allowing to set Result=OK only if color is changed
                startActivityForResult(new Intent(this, SettingsActivity.class), REQUEST_CODE_OPTION);
            } else if (itemId == R.id.menu_manual) {
                myLogI("--- USER clicks MENU : MANUAL ---");
                startActivity(new Intent(getApplicationContext(), HelpActivity.class));
            } else if (itemId == R.id.menu_seelog) {
                myLogI("--- USER clicks MENU : SEE LOGS ---");
                startActivity(new Intent(this, LogListActivity.class));
            } else if (itemId == R.id.menu_stats) {
                myLogI("--- USER clicks MENU : STATS ---");
                startActivity(new Intent(this, StatsActivity.class));
            } else if (itemId == R.id.menu_sendmail) {
                myLogI("--- USER clicks MENU : SEND MAIL ---");
                KanMail.sendDaMail(this, "bookplayer@driot.com", "**Bookplayer**", "Dear developer...\n\n");
            } else if (itemId == R.id.menu_cacheFiles) {
                myLogI("--- USER clicks MENU : CLEAN ---");
                startActivity(new Intent(this, CleanMemoryActivity.class));
            } else if (itemId == R.id.menu_website) {
                myLogI("--- USER clicks MENU : WEBSITE ---");
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(Var.WEBSITE_URL));
                startActivity(browserIntent);
            } else if (itemId == R.id.menu_open) {
                myLogI("--- USER clicks MENU : OPEN ---");
                startActivity(new Intent(getApplicationContext(), GetActivity.class));
            } else {
                myLogEE(null,"MainActivity.onOptionsItemSelected : unknown Item selected in Menu");
            }
            return super.onOptionsItemSelected(item);
        }


        @Override
        protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == REQUEST_CODE_OPTION) {
                myLog("coming back from OptionActivity - resultCode=[" + resultCode + "] Activity.RESULT_OK=[" + Activity.RESULT_OK + "]");
                if (resultCode == Activity.RESULT_OK) {
                    recreate();
                }
            }
        }


    }
