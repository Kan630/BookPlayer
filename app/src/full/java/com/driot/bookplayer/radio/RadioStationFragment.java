package com.driot.bookplayer.radio;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.RadioStation;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.NetworkStatusRowController;
import com.driot.bookplayer.utils.NetworkStatusViewModel;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingFragment;
import com.google.android.material.color.MaterialColors;

import java.util.HashMap;
import java.util.Map;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Station detail/player screen - the deep-link target inside radio_nav_graph.xml.
 * Converted from the former RadioStationActivity - see [[radio_deeplink_applinks_fix]].
 */
@AndroidEntryPoint
public class RadioStationFragment extends LoggingFragment {

    private ImageView ivCover;
    private TextView tvName;
    private TextView tvSubtitle;
    private TextView tvTags, tvTagLine;
    private TextView tvUrl;
    private TextView tvHomepage;
    private TextView tvStats;
    private ImageButton ibFavorite, ibVote;

    private RadioStationViewModel vm;

    private final Map<String, String> faviconCache = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_radio_station, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String stationUuid = requireArguments().getString(Intents.EXTRA_STATION_UUID);
        if (stationUuid == null || stationUuid.isEmpty()) {
            myLogE("RadioStationFragment started without stationuuid arg");
            NavHostFragment.findNavController(this).popBackStack();
            return;
        }

        ivCover = view.findViewById(R.id.ivCover);
        tvName = view.findViewById(R.id.tvName);
        tvSubtitle = view.findViewById(R.id.tvSubtitle);
        tvTagLine = view.findViewById(R.id.tvTagLine);
        tvTags = view.findViewById(R.id.tvTags);
        tvUrl = view.findViewById(R.id.tvUrl);
        tvHomepage = view.findViewById(R.id.tvHomepage);
        tvStats = view.findViewById(R.id.tvStats);
        ibFavorite = view.findViewById(R.id.ibFavorite);
        ibVote = view.findViewById(R.id.ibVote);

        // Load station

        vm = new ViewModelProvider(this).get(RadioStationViewModel.class);
        vm.loadStation(stationUuid);
        vm.getStation().observe(getViewLifecycleOwner(), this::bindStation);

        vm.refreshStationFromApi(stationUuid);

        View networkRowView = view.findViewById(R.id.includeNetworkStatus);
        NetworkStatusViewModel netVm = new ViewModelProvider(this).get(NetworkStatusViewModel.class);
        new NetworkStatusRowController(requireContext(), networkRowView, getViewLifecycleOwner(), netVm);

        ImageButton ibShare = view.findViewById(R.id.ibShare);
        ibShare.setOnClickListener(v -> {
            myLogI("--- user clicks share ----  ");
            RadioHelper.shareRadioStation(requireContext(), stationUuid);
        });

    }

    private void bindStation(@Nullable RadioStation radioStation) {
        if (radioStation == null) {
            myLogW("RadioStationFragment.bindStation: station not found in DB");
            tvName.setText(getString(R.string.no_result));
            return;
        }

        // Title
        tvName.setText(safe(radioStation.name));

        // Favorite
        int onSurface = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnSurface, "onSurface");
        int colorControlNormal = MaterialColors.getColor(requireContext(), androidx.appcompat.R.attr.colorControlNormal,
                "colorControlNormal");

        int tint = radioStation.isFavorite ? ContextCompat.getColor(requireContext(), R.color.red_500) : colorControlNormal;
        ibFavorite.setColorFilter(tint);

        ibFavorite.setOnClickListener(v -> {
            myLogI("--- user clicks favorite --- ");
            vm.toggleFavorite(radioStation);
            // Update icon immediately
            int tint2 = radioStation.isFavorite ? ContextCompat.getColor(requireContext(), R.color.red_500) : colorControlNormal;
            ibFavorite.setColorFilter(tint2);
        });

        ibVote.setOnClickListener(v -> {
            myLogI("--- user clicks VOTE --- ");
            if (vm.getStation().getValue() == null)
                return;
            String uuid = vm.getStation().getValue().stationuuid;
            vm.voteStation(uuid);
        });

        // Subtitle: Country · Language
        String country = safe(radioStation.country);
        String state = safe(radioStation.state);
        String language = safe(radioStation.language);

        if (!country.isEmpty() || !state.isEmpty() || !language.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            if (!country.isEmpty())
                sb.append(country);
            if (!state.isEmpty()) {
                if (sb.length() > 0)
                    sb.append(" · ");
                sb.append(state);
            }
            if (!language.isEmpty()) {
                if (sb.length() > 0)
                    sb.append(" · ");
                sb.append(language);
            }
            tvSubtitle.setText(sb.toString());
            tvSubtitle.setVisibility(View.VISIBLE);
        } else {
            tvSubtitle.setVisibility(View.GONE);
        }

        // Cover
        if (!radioStation.stationuuid.equals(ivCover.getTag(R.id.tag_station_uuid))) {
            ivCover.setTag(R.id.tag_station_uuid, radioStation.stationuuid);
            RadioFaviconHelper.loadRadioFavicon(radioStation, ivCover, R.drawable.ic_radio_24px, faviconCache);
        }

        // Tags
        String tags = safe(radioStation.tags);
        if (!tags.isEmpty()) {
            tvTags.setText(tags.replace(",", " • "));
            tvTagLine.setVisibility(View.VISIBLE);
            tvTags.setVisibility(View.VISIBLE);

        } else {
            tvTags.setText("");
            tvTagLine.setVisibility(View.GONE);
            tvTags.setVisibility(View.GONE);
        }

        // URL + homepage
        tvUrl.setText(safe(
                radioStation.url_resolved != null && !radioStation.url_resolved.isEmpty() ? radioStation.url_resolved
                        : radioStation.url));
        tvHomepage.setText(radioStation.homepage == null ? "not found" : radioStation.homepage);

        // Simple stats line (clickcount, etc. – adapt to your fields)
        StringBuilder stats = new StringBuilder();

        stats.append(getString(R.string.clicks_2pt, radioStation.clickcount)); // e.g. "Clicks: %d"
        int nbVotes = radioStation.votes == null ? 0 : Integer.parseInt(radioStation.votes);
        stats.append("\n");
        stats.append(getString(R.string.votes_2pt, nbVotes));

        stats.append("\n");
        stats.append("\ncodec : " + radioStation.codec);
        stats.append("\nbitrate : " + (radioStation.bitrate == 0 ? "unknown" : radioStation.bitrate + " kbps"));

        stats.append("\n");
        stats.append("\n"  + getString(R.string.listened) + " : " + Tonio.formatTime(radioStation.timeListened*1000));
        stats.append("\n");
        stats.append("\n" + getString(R.string.Added_in_app) + " : " + Tonio.formatDateForDisplay(radioStation.date_added));
        stats.append("\n" + getString(R.string.Last_played) + " : " + (radioStation.date_last_played == null ? getString(R.string.never)
                : Tonio.formatDateForDisplay(radioStation.date_last_played)));

        if (!TextUtils.isEmpty(stats.toString())) {
            tvStats.setText(stats.toString());
            tvStats.setVisibility(View.VISIBLE);
        } else {
            tvStats.setVisibility(View.GONE);
        }

    }

    private static String safe(@Nullable String s) {
        return s == null ? "" : s.trim();
    }

}
