package com.driot.bookplayer.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.LanguageItem;

import java.util.List;

public class LanguageSpinnerAdapter extends ArrayAdapter<LanguageItem> {

    private Context context;
    private List<LanguageItem> languages;

    public LanguageSpinnerAdapter(Context context, List<LanguageItem> languages) {
        super(context, 0, languages);
        this.context = context;
        this.languages = languages;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return createView(position, convertView, parent);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return createView(position, convertView, parent);
    }

    private View createView(int position, View convertView, ViewGroup parent) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_language_spinner, parent, false);
        LanguageItem item = languages.get(position);

        TextView textView = view.findViewById(R.id.textViewLanguage);
        ImageView imageView = view.findViewById(R.id.imageViewFlag);

        textView.setText(item.displayName);
        imageView.setImageResource(item.flagResId);

        return view;
    }
}

