package com.driot.bookplayer.podcasts;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.nav.FullActivity;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.views.SettingSwitchRow;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class PodcastDownloadSettingsActivity extends FullActivity {

    private Podcast podcast;
    private PodcastDownloadSettingsViewModel viewModel;

    private TextView tvPodcastName, tvTotalStorageValue, tvDownloadLastN, tvDownloadLastNSize;
    private TextView tvStatusDownloadedCount, tvStatusOrphanCount, tvStatusNeverDownloadedCount,
            tvStatusDeletedCount, tvStatusTotal;
    private View rowStatusDownloaded, rowStatusOrphan, rowStatusNeverDownloaded, rowStatusDeleted;
    private ImageView ivPodcastCover;
    private SettingSwitchRow rowAutoDownload;
    private SeekBar seekbarDownloadLastN;
    private EditText etDownloadLastN;
    private Button btnDownloadLastN;

    private int pendingDownloadLastN = 10;
    private int downloadLastNMax = 10;
    private long[] undownloadedSizesPrefixSum = new long[0];
    private boolean syncingDownloadLastNUI = false;
    private TextWatcher downloadLastNWatcher;

    @Override
    protected int getNavSectionId() {
        return R.id.nav_podcast;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_podcast_download_settings;
    }

    @Override
    protected boolean enableOngoingTaskOverlay() {
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        podcast = getIntent().getParcelableExtra("podcast");
        if (podcast == null) {
            myToastE("error loading podcast");
            myLogEE(null, "parcelable extra podcast is null");
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(PodcastDownloadSettingsViewModel.class);

        tvPodcastName = findViewById(R.id.tvDownloadSettingsPodcastName);
        tvTotalStorageValue = findViewById(R.id.tvTotalStorageValue);
        tvDownloadLastN = findViewById(R.id.tvDownloadLastN);
        tvDownloadLastNSize = findViewById(R.id.tvDownloadLastNSize);
        ivPodcastCover = findViewById(R.id.ivDownloadSettingsPodcastCover);
        rowAutoDownload = findViewById(R.id.rowAutoDownload);
        seekbarDownloadLastN = findViewById(R.id.seekbarDownloadLastN);
        etDownloadLastN = findViewById(R.id.etDownloadLastN);
        btnDownloadLastN = findViewById(R.id.btnDownloadLastN);
        tvStatusDownloadedCount = findViewById(R.id.tvStatusDownloadedCount);
        tvStatusOrphanCount = findViewById(R.id.tvStatusOrphanCount);
        tvStatusNeverDownloadedCount = findViewById(R.id.tvStatusNeverDownloadedCount);
        tvStatusDeletedCount = findViewById(R.id.tvStatusDeletedCount);
        tvStatusTotal = findViewById(R.id.tvStatusTotal);
        rowStatusDownloaded = findViewById(R.id.rowStatusDownloaded);
        rowStatusOrphan = findViewById(R.id.rowStatusOrphan);
        rowStatusNeverDownloaded = findViewById(R.id.rowStatusNeverDownloaded);
        rowStatusDeleted = findViewById(R.id.rowStatusDeleted);

        tvPodcastName.setText(podcast.title);
        Glide.with(ivPodcastCover.getContext()).load(podcast.image).into(ivPodcastCover);

        rowAutoDownload.setChecked(podcast.autoDownload);
        rowAutoDownload.setOnCheckedChangeListener((buttonView, isChecked) -> {
            myLogI("--- USER TOGGLES auto-download --- isChecked=" + isChecked);
            podcast.autoDownload = isChecked;
            viewModel.setAutoDownload(this, podcast, isChecked);
        });

        viewModel.getTotalStorageBytesLive().observe(this, bytes -> {
            if (bytes != null) {
                tvTotalStorageValue.setText(Tonio.getReadableSize(bytes));
            }
        });

        viewModel.getUndownloadedCountLive().observe(this, count -> {
            setupDownloadLastNControl(count != null ? count : 0);
        });

        viewModel.getUndownloadedSizesPrefixSumLive().observe(this, prefixSums -> {
            undownloadedSizesPrefixSum = prefixSums != null ? prefixSums : new long[0];
            setDownloadLastNValue(pendingDownloadLastN);
        });

        viewModel.getEpisodeStatusCountsLive().observe(this, counts -> {
            if (counts == null)
                return;
            tvStatusDownloadedCount.setText(String.valueOf(counts.downloadedTracked));
            tvStatusOrphanCount.setText(String.valueOf(counts.orphanOnDisk));
            tvStatusNeverDownloadedCount.setText(String.valueOf(counts.neverDownloaded));
            tvStatusDeletedCount.setText(String.valueOf(counts.deleted));
            tvStatusTotal.setText(getString(R.string.podcast_episode_status_total, counts.total));

            rowStatusDownloaded.setVisibility(counts.downloadedTracked > 0 ? View.VISIBLE : View.GONE);
            rowStatusOrphan.setVisibility(counts.orphanOnDisk > 0 ? View.VISIBLE : View.GONE);
            rowStatusNeverDownloaded.setVisibility(counts.neverDownloaded > 0 ? View.VISIBLE : View.GONE);
            rowStatusDeleted.setVisibility(counts.deleted > 0 ? View.VISIBLE : View.GONE);
        });

        btnDownloadLastN.setOnClickListener(v -> {
            myLogI("--- USER CLICKS download last N --- N=" + pendingDownloadLastN);
            btnDownloadLastN.setEnabled(false);
            viewModel.downloadLastN(this, podcast, pendingDownloadLastN, () -> {
                myToast(getString(R.string.podcast_download_last_n_started, pendingDownloadLastN));
                viewModel.refreshStats(this, podcast);
                btnDownloadLastN.setEnabled(true);
            });
        });

        viewModel.refreshStats(this, podcast);
    }

    private void setupDownloadLastNControl(int undownloadedCount) {
        if (undownloadedCount <= 0) {
            tvDownloadLastN.setText(getString(R.string.podcast_no_undownloaded_episodes));
            seekbarDownloadLastN.setVisibility(View.GONE);
            etDownloadLastN.setVisibility(View.GONE);
            btnDownloadLastN.setVisibility(View.GONE);
            return;
        }

        seekbarDownloadLastN.setVisibility(View.VISIBLE);
        etDownloadLastN.setVisibility(View.VISIBLE);
        btnDownloadLastN.setVisibility(View.VISIBLE);

        downloadLastNMax = undownloadedCount;
        seekbarDownloadLastN.setMin(1); // minSdk is 26, setMin() always available
        seekbarDownloadLastN.setMax(downloadLastNMax);
        setDownloadLastNValue(Math.min(10, downloadLastNMax));

        seekbarDownloadLastN.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser)
                    return; // ignore programmatic setProgress() from setDownloadLastNValue()
                setDownloadLastNValue(Math.max(1, progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // Dragging the slider is impractical once there are hundreds of undownloaded episodes,
        // so let the user type the value directly - the two stay in sync either way.
        if (downloadLastNWatcher != null) {
            etDownloadLastN.removeTextChangedListener(downloadLastNWatcher);
        }
        downloadLastNWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (syncingDownloadLastNUI)
                    return;
                String typedStr = s.toString();
                if (typedStr.isEmpty())
                    return;
                int typed;
                try {
                    typed = Integer.parseInt(typedStr);
                } catch (NumberFormatException e) {
                    return;
                }
                setDownloadLastNValue(Math.max(1, Math.min(typed, downloadLastNMax)));
            }
        };
        etDownloadLastN.addTextChangedListener(downloadLastNWatcher);
    }

    /** Single source of truth for N: updates the slider, the EditText and the summary label together. */
    private void setDownloadLastNValue(int value) {
        pendingDownloadLastN = value;
        syncingDownloadLastNUI = true;
        seekbarDownloadLastN.setProgress(value);
        String valueStr = String.valueOf(value);
        if (!valueStr.contentEquals(etDownloadLastN.getText())) {
            etDownloadLastN.setText(valueStr);
            etDownloadLastN.setSelection(valueStr.length());
        }
        syncingDownloadLastNUI = false;
        tvDownloadLastN.setText(getString(R.string.podcast_download_last_n_episodes, value));

        int idx = value - 1;
        if (idx >= 0 && idx < undownloadedSizesPrefixSum.length) {
            tvDownloadLastNSize.setText(getString(R.string.podcast_download_last_n_size,
                    Tonio.getReadableSize(undownloadedSizesPrefixSum[idx])));
        } else {
            tvDownloadLastNSize.setText("");
        }
    }
}
