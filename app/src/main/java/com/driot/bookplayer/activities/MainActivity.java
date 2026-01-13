    package com.driot.bookplayer.activities;

    import android.annotation.SuppressLint;
    import android.app.Activity;
    import android.content.BroadcastReceiver;
    import android.content.Context;
    import android.content.Intent;

    import android.content.IntentFilter;
    import android.net.Uri;
    import android.os.Bundle;
    import android.view.Menu;
    import android.view.MenuItem;
    import android.view.View;
    import android.widget.Button;

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
    import com.driot.bookplayer.global.Intents;
    import com.driot.bookplayer.global.Option;
    import com.driot.bookplayer.global.Var;
    import com.driot.bookplayer.helpers.InsetHelper;
    import com.driot.bookplayer.helpers.ViewHelper;
    import com.driot.bookplayer.player.MediaService;
    import com.driot.bookplayer.helpers.InfoHelper;
    import com.driot.bookplayer.player.NavHelper;
    import com.driot.bookplayer.player.PlaybackUiState;
    import com.driot.bookplayer.player.PlaybackViewModel;
    import com.driot.bookplayer.player.StartPlayHelper;
    import com.driot.bookplayer.radio.RadioStationActivity;
    import com.driot.bookplayer.utils.InAppMsgManager;
    import com.driot.bookplayer.utils.KanMail;
    import com.google.android.material.dialog.MaterialAlertDialogBuilder;

    import dagger.hilt.android.AndroidEntryPoint;

    @AndroidEntryPoint
    public class MainActivity extends BaseBottomNavActivity {

        private RecyclerView recyclerView;
        private FoldersRVAdapter adapter;
        private MainViewModel mainVm;

        Toolbar toolbar;
        private static final int REQUEST_CODE_OPTION = 34343;

        public static final String EXTRA_REQUESTED_NAV_ID = "EXTRA_REQUESTED_NAV_ID";

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

        private final BroadcastReceiver inAppMsgRx = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                myLogD("broadcast received : inAppMsgRx");
                InAppMsgManager.maybeShowBestMessage(MainActivity.this, getString(R.string.app_name));
            }
        };

        @Override protected int getNavId() { return R.id.nav_library; }
        @Override protected int getLayoutResId() { return R.layout.activity_main; }
        @Override protected boolean enableOngoingTaskOverlay() { return true; }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            InsetHelper.apply(this);

            if (savedInstanceState == null && !infoAlreadyShown) {
                InfoHelper.printSomeStuffAboutDevice(this);
                infoAlreadyShown = true;
            }

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


            mainVm = new ViewModelProvider(this).get(MainViewModel.class);
            mainVm.getFolders().observe(this, folders -> {
                if (folders == null) return;
                boolean isEmpty = folders.isEmpty();

                // WELCOME MESSAGE or Folders
                View emptyView = findViewById(R.id.emptyView);
                recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                Button btnWelcomeAddBook = emptyView.findViewById(R.id.btnWelcomeAddBook);
                btnWelcomeAddBook.setOnClickListener(v -> {
                    startActivity(new Intent(getApplicationContext(), GetActivity.class));
                });

                adapter.submitList(folders);
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
                    myLogI("--- USER CLICK BACK from MAIN --- (system button) -- EXIT APP --");
                    if (Option.getStopAudioIfUserClosesApp()) {
                        startService(
                                new Intent(MainActivity.this, MediaService.class)
                                        .setAction(Intents.EXTRA_CMD_STOP)
                                        .putExtra(Intents.EXTRA_CALLER, "press back from MainActivity"));
                    }
                    finish();
                }
            });

            handleDeepLink(getIntent());

            //InAppMsgManager.deleteInAppMsgCache(this);
            MyApp.getPeriodicTaskManager(this).start(); // safe
            InAppMsgManager.maybeShowBestMessage(this, "message");
        }

        @Override protected void onNewIntent(Intent intent) {
            super.onNewIntent(intent);
            setIntent(intent);
            handleRequestedNavFromIntent(intent); // just routing/navigation, cheap
            if (intent.getBooleanExtra("forceRefresh", false) && mainVm != null) {
                myLog("forceRefresh");
                mainVm.forceRefresh();
            }
            if (intent.getBooleanExtra("scrollToTop", false) && mainVm != null) {
                myLog("scrollToTop");
                mainVm.requestScrollToTopNow();
            }
            handleDeepLink(intent);
        }

        @Override
        protected void onResume() {
            super.onResume();
            handleRequestedNavFromIntent(getIntent());
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
            } else if (itemId == R.id.menu_sort) {
                myLogI("--- USER clicks MENU : SORT ---");
                showSortOrderDialog();
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
            } else if (itemId == R.id.menu_cleanMemory) {
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

        private void handleRequestedNavFromIntent(Intent intent) {
            if (intent == null) return;

            int requestedNavId = intent.getIntExtra(EXTRA_REQUESTED_NAV_ID, 0);
            if (requestedNavId == 0) return; // nothing requested

            // Consume the extra so it won't re-trigger next time
            intent.removeExtra(EXTRA_REQUESTED_NAV_ID);

            if (requestedNavId == R.id.nav_library) {
                // Already on the Library tab, nothing else to do
                return;
            } else if (requestedNavId == R.id.nav_radio) {
                NavHelper.navigateToRadioSection(this, true);
            } else if (requestedNavId == R.id.nav_podcast) {
                NavHelper.navigateToPodcastSection(this, true);
            } else if (requestedNavId == R.id.nav_add) {
                startActivity(new Intent(this, GetActivity.class));
            } else if (requestedNavId == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
            }
        }

        private void handleDeepLink(Intent intent) {
            myLogI("=== handleDeepLink called ===");

            if (intent == null) {
                myLogI("Intent is NULL");
                return;
            }

            myLogI("Intent action: " + intent.getAction());

            Uri data = intent.getData();

            if (data == null) {
                myLogI("URI data is NULL");
                return;
            }

            if (data != null) {
                String host = data.getHost();
                String path = data.getPath();

                myLogI("DeepLink: host=[" + host + "] - path=[" + path + "] - data=[" + data.toString() + "]");

                if (host != null) {

                    switch (path) {

                        case "/share/radio":
                            String url = data.getQueryParameter("url");
                            String uuid = data.getQueryParameter("uuid");
                            myLog("url=[" + url + "] - uuid=[" + uuid + "]");

                            if (uuid != null) {
                                Intent i = new Intent(this, RadioStationActivity.class);
                                i.putExtra(Intents.EXTRA_STATION_UUID, uuid);
                                i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                startActivity(i);
                            }
                            if (url != null) {
                                StartPlayHelper.playRadioFromUuidAndUrl(this, uuid, url, "DeepLink");
                            }

                            break;


                    }
                }
            }
        }


        private void showSortOrderDialog() {
            String currentMode = Option.getSortMode();
            String currentDir  = Option.getSortDirection();

            String[] options = new String[] {
                    getString(R.string.sort_last_played)
                            + (currentMode.equals("last_played") ? getDirectionLabel(currentDir) : ""),

                    getString(R.string.sort_alphabetically)
                            + (currentMode.equals("alpha") || currentMode.equals("alphabetical") ? getDirectionLabel(currentDir) : ""),

                    getString(R.string.sort_last_added)
                            + (currentMode.equals("added") || currentMode.equals("last_added") ? getDirectionLabel(currentDir) : "")
            };

            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.sort_by)
                    .setItems(options, (dialog, which) -> {
                        String selectedMode;
                        switch (which) {
                            case 0: selectedMode = "last_played"; break;
                            case 1: selectedMode = "alpha";       break;
                            case 2: selectedMode = "added";       break;
                            default: return;
                        }

                        if (selectedMode.equals(currentMode)) {
                            // same mode → toggle direction
                            String newDir = "asc".equals(currentDir) ? "desc" : "asc";
                            Option.setSortDirection(newDir);
                        } else {
                            // new mode → start with descending (recent / Z first)
                            Option.setSortMode(selectedMode);
                            Option.setSortDirection("desc");
                        }

                        // Tell ViewModel to re-sort and refresh UI
                        if (mainVm != null) {
                            mainVm.forceRefresh();
                        }

                        myLogI("Sort changed → mode=" + Option.getSortMode() + " dir=" + Option.getSortDirection());
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }

        private String getDirectionLabel(String dir) {
            if ("asc".equals(dir)) {
                return "   (↑)";
            } else {
                return "   (↓)";
            }
        }


    }
