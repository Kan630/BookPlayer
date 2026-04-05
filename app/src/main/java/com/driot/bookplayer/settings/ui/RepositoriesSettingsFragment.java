package com.driot.bookplayer.settings.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;

import java.util.Arrays;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.utils.log.LoggingFragment;

public class RepositoriesSettingsFragment extends LoggingFragment {

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
        chk_use_google_images.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setUseGoogleImages(isChecked));

        // Gutendex
        CheckBox chk_option_gutendex_use_mirror = root.findViewById(R.id.chk_option_gutendex_use_mirror);
        LinearLayout ll_option_gutendex_use_mirror = root.findViewById(R.id.ll_option_gutendex_use_mirror);
        chk_option_gutendex_use_mirror.setChecked(Option.getGutendexUseMirror());
        ll_option_gutendex_use_mirror.setOnClickListener(v -> chk_option_gutendex_use_mirror.toggle());
        chk_option_gutendex_use_mirror.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setGutendexUseMirror(isChecked));

        // Gutenberg ebook Download mirror
        Spinner spMirror = root.findViewById(R.id.sp_gutenberg_mirror);
        ArrayAdapter<String> mirrorAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, Option.GUTENBERG_MIRROR_NAMES);
        mirrorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spMirror.setAdapter(mirrorAdapter);
        int mirrorIdx = Arrays.asList(Option.GUTENBERG_MIRROR_URLS).indexOf(Option.getGutenbergMirrorUrl());
        if (mirrorIdx >= 0) spMirror.setSelection(mirrorIdx);
        spMirror.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Option.setGutenbergMirrorUrl(Option.GUTENBERG_MIRROR_URLS[position]);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        return root;
    }

}
