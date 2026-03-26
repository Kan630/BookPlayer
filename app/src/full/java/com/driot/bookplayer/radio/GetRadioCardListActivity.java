package com.driot.bookplayer.radio;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.appcompat.app.AlertDialog;

import com.driot.bookplayer.R;
import com.driot.bookplayer.nav.BaseBottomNavActivity;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.LoadingProgressHelper;
import com.driot.bookplayer.helpers.ViewHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class GetRadioCardListActivity extends BaseBottomNavActivity {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    public static final String EXTRA_FACET_MODE = "EXTRA_FACET_MODE";
    public static final int MODE_TAG      = 0;
    public static final int MODE_COUNTRY  = 1;
    public static final int MODE_LANGUAGE = 2;

    @IntDef({ MODE_TAG, MODE_COUNTRY, MODE_LANGUAGE })
    @Retention(RetentionPolicy.SOURCE)
    public @interface FacetMode {}

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private GetRadioCardListViewModel viewModel;
    private TagCardAdapter adapter;

    private RecyclerView  recyclerView;
    private ProgressBar   progressBar;
    private TextView      tvProgressMessage;
    private LinearLayout  layoutSearch;
    private EditText      etSearch;
    private ImageButton   btnClearSearch;
    private ImageButton   btnSort;

    private int  backPressCount    = 0;
    private long lastBackPressTime = 0;

    private final LoadingProgressHelper progressHelper = new LoadingProgressHelper();

    // -------------------------------------------------------------------------
    // Static launcher
    // -------------------------------------------------------------------------

    public static void start(Context ctx, @FacetMode int mode) {
        ctx.startActivity(
                new Intent(ctx, GetRadioCardListActivity.class)
                        .putExtra(EXTRA_FACET_MODE, mode));
    }

    // -------------------------------------------------------------------------
    // BaseBottomNavActivity overrides
    // -------------------------------------------------------------------------

    @Override protected int getNavId()         { return R.id.nav_radio; }
    @Override protected int getLayoutResId()   { return R.layout.activity_get_radio_by_tag; }
    @Override protected boolean enableOngoingTaskOverlay() { return true; }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        bindViews();

        @FacetMode int mode = getIntent().getIntExtra(EXTRA_FACET_MODE, MODE_TAG);

        setupRecyclerView(mode);
        setupAdapter(mode);
        setupViewModel(mode);
        setupSearchBar();

        if (savedInstanceState == null) {
            viewModel.seedFromCache(RadioCacheHelper.loadCache(this, mode));
            viewModel.loadFacetItems();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        progressHelper.stop();
    }

    // -------------------------------------------------------------------------
    // Setup helpers
    // -------------------------------------------------------------------------

    private void bindViews() {
        recyclerView      = findViewById(R.id.recyclerView);
        progressBar       = findViewById(R.id.progressBar);
        tvProgressMessage = findViewById(R.id.tvProgressMessage);
        layoutSearch      = findViewById(R.id.layoutSearch);
        etSearch          = findViewById(R.id.etSearch);
        btnClearSearch    = findViewById(R.id.btnClearSearch);
        btnSort           = findViewById(R.id.btnSort);
    }

    private void setupRecyclerView(@FacetMode int mode) {
        int span = mode == MODE_TAG
                ? getResources().getInteger(R.integer.radio_grid_span_card_tag)
                : getResources().getInteger(R.integer.radio_grid_span_card_country);
        if (span < 2) span = 2;

        recyclerView.setLayoutManager(new GridLayoutManager(this, span));
        recyclerView.addItemDecoration(
                new ViewHelper.SpacesItemDecoration(ViewHelper.dp(this, Var.GRID_LAYOUT_SPACER)));
    }

    private void setupAdapter(@FacetMode int mode) {
        adapter = new TagCardAdapter(tagItem -> {
            myLogI("---- user clicks facet item, name=[" + tagItem.name
                    + "] country=[" + tagItem.iso_3166_1
                    + "] lang=[" + tagItem.iso_639 + "]");

            Intent i = new Intent(this, RadioResultsActivity.class);
            switch (mode) {
                case MODE_COUNTRY:
                    i.putExtra(GetRadioActivity.EXTRA_RADIO_STATION_SEARCH_MODE, "MODE_COUNTRY")
                            .putExtra("country", tagItem.name)
                            .putExtra("lang", "").putExtra("tag", "").putExtra("query", "");
                    break;
                case MODE_LANGUAGE:
                    i.putExtra(GetRadioActivity.EXTRA_RADIO_STATION_SEARCH_MODE, "MODE_LANGUAGE")
                            .putExtra("lang", tagItem.name)
                            .putExtra("country", "").putExtra("tag", "").putExtra("query", "");
                    break;
                case MODE_TAG:
                default:
                    i.putExtra(GetRadioActivity.EXTRA_RADIO_STATION_SEARCH_MODE, "MODE_TAG")
                            .putExtra("tag", tagItem.name)
                            .putExtra("lang", "").putExtra("country", "").putExtra("query", "");
                    break;
            }
            startActivity(i);
        });
        recyclerView.setAdapter(adapter);
    }

    private void setupViewModel(@FacetMode int mode) {
        viewModel = new ViewModelProvider(this).get(GetRadioCardListViewModel.class);
        viewModel.init(mode, new RadioBrowserRepository(
                this, false, Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL));

        // Search bar is only useful (and visible) for tag mode
        if (mode == MODE_TAG) {
            viewModel.setSearchVisible(true);
        }

        // Filtered list → adapter
        viewModel.getFilteredItemsLive().observe(this, items -> adapter.setItems(items));

        // Loading state → progress bar + animated message
        viewModel.getLoadingStateLive().observe(this, state -> {
            boolean loading = state == GetRadioCardListViewModel.LoadingState.LOADING;
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            if (loading) {
                progressHelper.start(tvProgressMessage, new LoadingProgressHelper.MessageProvider() {
                    @NonNull @Override public String getInitialMessage() {
                        return getString(R.string.radio_browser_contacting);
                    }
                    @NonNull @Override public String getTickMessage(long elapsedSec) {
                        return getString(R.string.radio_browser_wait_elapsed,
                                (int) elapsedSec, Var.RADIO_BROWSER_TIMEOUT_SEC);
                    }
                });
            } else {
                progressHelper.stop();
            }
        });

    }

    private void setupSearchBar() {
        // Text changes → ViewModel filter
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                viewModel.setSearchQuery(s.toString());
                btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }
        });

        // Clear button wipes the query
        btnClearSearch.setOnClickListener(v -> {
            etSearch.setText("");
            viewModel.setSearchQuery("");
        });

        // Sort button
        btnSort.setOnClickListener(v -> showSortOrderDialog());

        // Restore search bar visibility across rotation
        viewModel.getSearchVisibleLive().observe(this, visible ->
                layoutSearch.setVisibility(visible ? View.VISIBLE : View.GONE));
    }

    // -------------------------------------------------------------------------
    // Sort dialog (tag mode only)
    // -------------------------------------------------------------------------

    private void showSortOrderDialog() {
        String currentMode = viewModel.getSortMode();
        String currentDir  = viewModel.getSortDir();

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_sort_radio_tags, null);
        MaterialButtonToggleGroup toggleGroup      = dialogView.findViewById(R.id.toggle_group_sort);
        MaterialButton            btnStationCount  = dialogView.findViewById(R.id.btn_sort_station_count);
        MaterialButton            btnAlpha         = dialogView.findViewById(R.id.btn_sort_alpha);

        // Show direction arrow on the currently active button (▲ asc, ▼ desc)
        String suffix      = "desc".equals(currentDir) ? " \u25BC" : " \u25B2";
        String suffixAlpha = "desc".equals(currentDir) ? " \u25B2" : " \u25BC"; // reversed for alpha

        if ("alpha".equals(currentMode)) {
            toggleGroup.check(R.id.btn_sort_alpha);
            btnAlpha.setText(getString(R.string.Alphabetically) + suffixAlpha);
        } else {
            toggleGroup.check(R.id.btn_sort_station_count);
            btnStationCount.setText(getString(R.string.sort_station_count) + suffix);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();

        View.OnClickListener sortClick = v -> {
            String newMode = (v.getId() == R.id.btn_sort_alpha) ? "alpha" : "station_count";
            String newDir;
            if (newMode.equals(currentMode)) {
                newDir = "asc".equals(currentDir) ? "desc" : "asc";
            } else {
                newDir = "station_count".equals(newMode) ? "desc" : "asc";
            }
            viewModel.setSortOrder(newMode, newDir);
            dialog.dismiss();
        };

        btnStationCount.setOnClickListener(sortClick);
        btnAlpha.setOnClickListener(sortClick);
    }

    // -------------------------------------------------------------------------
    // Search toggle (called from toolbar button / menu item)
    // -------------------------------------------------------------------------

    private void toggleSearch() {
        View bottomNav      = findViewById(R.id.bottomNav);
        View miniNowPlaying = findViewById(R.id.miniNowPlaying);

        if (viewModel.isSearchVisible()) {
            // --- Close search ---
            viewModel.setSearchVisible(false);   // also clears the query
            if (bottomNav      != null) bottomNav.setVisibility(View.VISIBLE);
            if (miniNowPlaying != null) miniNowPlaying.setVisibility(View.VISIBLE);
            etSearch.setText("");
            ViewHelper.hideKeyboard(this, etSearch);
            backPressCount    = 0;
            lastBackPressTime = 0;
        } else {
            // --- Open search ---
            viewModel.setSearchVisible(true);
            if (bottomNav      != null) bottomNav.setVisibility(View.GONE);
            if (miniNowPlaying != null) miniNowPlaying.setVisibility(View.GONE);
            // Restore any previously typed query (survives rotation)
            String current = viewModel.getSearchQueryLive().getValue();
            etSearch.setText(current);
            if (current != null) etSearch.setSelection(current.length());
            etSearch.requestFocus();
            ViewHelper.showKeyboard(this, etSearch);
        }
    }
}