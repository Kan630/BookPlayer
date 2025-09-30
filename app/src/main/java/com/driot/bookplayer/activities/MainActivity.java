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
    import androidx.lifecycle.LiveData;
    import androidx.lifecycle.ViewModelProvider;
    import androidx.localbroadcastmanager.content.LocalBroadcastManager;
    import androidx.recyclerview.widget.GridLayoutManager;
    import androidx.recyclerview.widget.LinearLayoutManager;
    import androidx.recyclerview.widget.RecyclerView;

    import com.driot.bookplayer.MyApp;
    import com.driot.bookplayer.R;
    import com.driot.bookplayer.adapter.FoldersRVAdapter;
    import com.driot.bookplayer.db.DatabaseClient;
    import com.driot.bookplayer.db.Folder;
    import com.driot.bookplayer.db.FolderDao;
    import com.driot.bookplayer.global.Option;
    import com.driot.bookplayer.global.Var;
    import com.driot.bookplayer.helpers.FileHelper;
    import com.driot.bookplayer.helpers.InsetHelper;
    import com.driot.bookplayer.helpers.StorageHelper;
    import com.driot.bookplayer.helpers.ViewHelper;
    import com.driot.bookplayer.objects.OngoingTaskHost;
    import com.driot.bookplayer.objects.WorkFlow;
    import com.driot.bookplayer.player.PlayList;
    import com.driot.bookplayer.player.AudioService;
    import com.driot.bookplayer.helpers.InfoHelper;
    import com.driot.bookplayer.player.PlaybackViewModel;
    import com.driot.bookplayer.utils.InAppMsgManager;
    import com.driot.bookplayer.utils.KanMail;
    import com.driot.bookplayer.utils.log.LoggingActivity;

    import java.util.List;

    public class MainActivity extends LoggingActivity {

        private RecyclerView recyclerView;
        private boolean pendingScrollToTop = false;
        private int pendingFolderIdForScroll = -1;
        private int lastScrolledFolderId = -1;

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

        // Just in case we are here while we shouldn't, because isPlaying...
        AudioService audioService;
        private final ServiceConnection audioServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName className, IBinder service) {
                AudioService.BackgroundBinder binder = (AudioService.BackgroundBinder) service;
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

        private final BroadcastReceiver inAppMsgRx = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                myLogD("broadcast received : inAppMsgRx");
                InAppMsgManager.maybeShowBestMessage(MainActivity.this, getString(R.string.app_name));
            }
        };


        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);
            InsetHelper.apply(this);

            if (savedInstanceState == null && !infoAlreadyShown) {
                InfoHelper.printSomeStuffAboutDevice(this);
                infoAlreadyShown = true;
            }

            //WorkManager.getInstance(this).cancelAllWork();
            //WorkFlow.cancelAllOngoingTasks(this);

            //DEBUG SHIT - LIST ALL DISK FILES
            //myLogD("-----------------");FileHelper.listAllFiles(StorageHelper.getUnzipFolder(this, true));myLogD("-----------------");FileHelper.listAllFiles(StorageHelper.getUnzipFolder(this, false));myLogD("-----------------");


            //DEBUG SHIT - LIST ALL DB FOLDERS
            //Sql.log_all_Folders(this);


            OngoingTaskHost.attach(
                    this,
                    R.id.topOverlayContainer,
                    new Intent(this, AddResourceActivity.class)); // tap => open details

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

            FoldersRVAdapter adapter = new FoldersRVAdapter(MainActivity.this);
            recyclerView.setAdapter(adapter);
            PlaybackViewModel playbackVm = new ViewModelProvider(this).get(PlaybackViewModel.class);
            adapter.connectPlayback(this, playbackVm.getState()); // adapter observe playback (highlight)
            getFolders();

            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    myLogI("--- USER CLICK BACK from MAIN --- (system button)");
                    if (Option.getStopAudioIfUserClosesApp()) {
                        Intent i = new Intent(MainActivity.this, AudioService.class)
                                .setAction(AudioService.ACTION_CMD)
                                .putExtra(AudioService.EXTRA_CMD, AudioService.CMD_STOP);
                        startService(i);
                    }
                    finish();
                }
            });


            //InAppMsgManager.deleteInAppMsgCache(this);
            MyApp.getPeriodicTaskManager(this).start(); // safe
            InAppMsgManager.maybeShowBestMessage(this, "message");
            //startActivity(new Intent(this, TtsReadTxtActivity.class));

            //Option.setTtsVoice("system"); //reset
        }

        @Override
        protected void onResume() {
            super.onResume();
            LocalBroadcastManager.getInstance(this).registerReceiver(inAppMsgRx, new IntentFilter(InAppMsgManager.ACTION_CACHE_UPDATED));        // Et tente immédiatement avec le cache courant
            InAppMsgManager.maybeShowBestMessage(this, getString(R.string.app_name));
            WorkFlow.maybeResumeWorkFlow(this);
            PlaybackViewModel playbackVm = new ViewModelProvider(this).get(PlaybackViewModel.class);
            com.driot.bookplayer.player.PlaybackUiState s = playbackVm.getState().getValue();
            if (s != null && s.folderId > 0 && s.folderId != lastScrolledFolderId) {
                pendingFolderIdForScroll = s.folderId;
                pendingScrollToTop = true;
            } else {
                pendingScrollToTop = false; // same book still playing → no auto scroll
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
                myLogI("--- USER clicks MENU : OPTIONS ---");
                this.getSharedPreferences(Option.SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit().putBoolean("ACTIVITY_OPTION_HAS_RESULT", false).apply(); //trick to reload MainActivity if color changed in OptionActivity, by allowing to set Result=OK only if color is changed
                startActivityForResult(new Intent(this, OptionActivity.class), REQUEST_CODE_OPTION);
            } else if (itemId == R.id.menu_manual) {
                startActivity(new Intent(getApplicationContext(), HelpActivity.class));
            } else if (itemId == R.id.menu_seelog) {
                startActivity(new Intent(this, LogListActivity.class));
            } else if (itemId == R.id.menu_stats) {
                startActivity(new Intent(this, StatsActivity.class));
            } else if (itemId == R.id.menu_sendmail) {
                KanMail.sendDaMail(this, "bookplayer@driot.com", "**Bookplayer**", "Dear developer...\n\n");
            } else if (itemId == R.id.menu_cacheFiles) {
                startActivity(new Intent(this, CleanMemoryActivity.class));
            } else if (itemId == R.id.menu_website) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(Var.WEBSITE_URL));
                startActivity(browserIntent);
            } else if (itemId == R.id.menu_open) {
                startActivity(new Intent(getApplicationContext(), GetActivity.class));
            } else if (itemId == R.id.action_menu_addBook) {
                startActivity(new Intent(getApplicationContext(), GetActivity.class));
         // } else if (itemId == R.id.menu_synchro) {
           //     startActivity(new Intent(this, SynchroActivity.class));
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
        private void getFolders() {
            myLogD("getFolders()");
            FolderDao folderDao = DatabaseClient.getInstance(getApplicationContext())
                    .getAppDatabase().FolderDao();
            LiveData<List<Folder>> foldersLiveData = folderDao.getAllLiveData();

            foldersLiveData.observe(this, folders -> {
                FoldersRVAdapter a = (FoldersRVAdapter) recyclerView.getAdapter();
                if (a == null) return;

                if (folders == null || folders.isEmpty()) {
                    if (!HasBeenProposedToOpenFile) startActivity(new Intent(getApplicationContext(), GetActivity.class));
                    HasBeenProposedToOpenFile = true;
                }

                if (pendingScrollToTop) {
                    a.submitList(folders, () -> recyclerView.post(() -> {
                        // folder moved to top due to lLastAccess: jump to top once
                        RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
                        if (lm instanceof LinearLayoutManager) {
                            ((LinearLayoutManager) lm).scrollToPositionWithOffset(0, 0);
                        } else {
                            recyclerView.scrollToPosition(0);
                        }
                        lastScrolledFolderId = pendingFolderIdForScroll; // mark done for this folder
                        pendingScrollToTop = false;                      // one-shot
                    }));
                } else {
                    a.submitList(folders);
                }
            });

        }

    }
