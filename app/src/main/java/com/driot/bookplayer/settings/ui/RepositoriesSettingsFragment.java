package com.driot.bookplayer.settings.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.utils.log.LoggingFragment;

import java.util.concurrent.Executors;

public class RepositoriesSettingsFragment extends LoggingFragment {

    private EditText etLibrivoxNbResults;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_settings_repositories, container, false);

        boolean showLocalTitle = true;
        Bundle args = getArguments();
        if (args != null)
            showLocalTitle = args.getBoolean("ARG_SHOW_LOCAL_TITLE", true);
        View titleContainer = root.findViewById(R.id.ll_title);
        if (titleContainer != null) {
            titleContainer.setVisibility(showLocalTitle ? View.VISIBLE : View.GONE);
        }

        etLibrivoxNbResults = root.findViewById(R.id.et_librivox_api_nb_results);
        etLibrivoxNbResults.setText(String.valueOf(Option.getLibrivoxApiNbResults()));

        CheckBox chk_gutenberg_use_cloudfare = root.findViewById(R.id.chk_option_gutenberg_use_cloudfare);
        LinearLayout ll_gutenberg_use_cloudfare = root.findViewById(R.id.ll_option_gutenberg_use_cloudfare);
        chk_gutenberg_use_cloudfare.setChecked(Option.getGutenbergUseCloudflare());
        ll_gutenberg_use_cloudfare.setOnClickListener(v -> chk_gutenberg_use_cloudfare.toggle());
        chk_gutenberg_use_cloudfare
                .setOnCheckedChangeListener((buttonView, isChecked) -> Option.setGutenbergUseCloudflare(isChecked));

        // Pixabay
        CheckBox chk_use_pixabay = root.findViewById(R.id.chk_option_use_pixabay);
        LinearLayout ll_use_pixabay = root.findViewById(R.id.ll_option_use_pixabay);
        chk_use_pixabay.setChecked(Option.getUsePixabay());
        ll_use_pixabay.setOnClickListener(v -> chk_use_pixabay.toggle());
        chk_use_pixabay.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setUsePixabay(isChecked));

        // Google Books
        CheckBox chk_use_google_books = root.findViewById(R.id.chk_option_use_google_books);
        LinearLayout ll_use_google_books = root.findViewById(R.id.ll_option_use_google_books);
        chk_use_google_books.setChecked(Option.getUseGoogleBooks());
        ll_use_google_books.setOnClickListener(v -> chk_use_google_books.toggle());
        chk_use_google_books.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setUseGoogleBooks(isChecked));

        // Open Library
        CheckBox chk_use_open_library = root.findViewById(R.id.chk_option_use_open_library);
        LinearLayout ll_use_open_library = root.findViewById(R.id.ll_option_use_open_library);
        chk_use_open_library.setChecked(Option.getUseOpenLibrary());
        ll_use_open_library.setOnClickListener(v -> chk_use_open_library.toggle());
        chk_use_open_library.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setUseOpenLibrary(isChecked));

        // Google Images
        CheckBox chk_use_google_images = root.findViewById(R.id.chk_option_use_google_images);
        LinearLayout ll_use_google_images = root.findViewById(R.id.ll_option_use_google_images);
        chk_use_google_images.setChecked(Option.getUseGoogleImages());
        ll_use_google_images.setOnClickListener(v -> chk_use_google_images.toggle());
        chk_use_google_images
                .setOnCheckedChangeListener((buttonView, isChecked) -> Option.setUseGoogleImages(isChecked));

        return root;
    }

    @Override
    public void onPause() {
        super.onPause();
        saveEditTextValues();
    }

    private void saveEditTextValues() {
        if (etLibrivoxNbResults != null) {
            final int value = Option.clampInt(this.getContext(),
                    etLibrivoxNbResults,
                    Var.LIBRIVOX_API_MIN_RESULTS,
                    Var.LIBRIVOX_API_MAX_RESULTS,
                    Option.DEFAULT_LIBRIVOX_API_NB_RESULTS,
                    getString(R.string.librivox));
            Executors.newSingleThreadExecutor().execute(() -> {
                Option.setLibrivoxApiNbResults(value);
            });
        }
    }
}
