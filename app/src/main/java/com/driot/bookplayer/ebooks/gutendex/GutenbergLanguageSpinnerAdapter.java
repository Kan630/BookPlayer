// GutenbergLanguageSpinnerAdapter.java
package com.driot.bookplayer.ebooks.gutendex;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.log.LoggingArrayAdapter;

import java.util.List;

public class GutenbergLanguageSpinnerAdapter extends LoggingArrayAdapter<GutenbergLanguageItem> {

    public GutenbergLanguageSpinnerAdapter(@NonNull Context context, @NonNull List<GutenbergLanguageItem> items) {
        super(context, items);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return createView(position, convertView, parent);
    }

    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return createView(position, convertView, parent);
    }

    private View createView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_language_spinner, parent, false);
        }

        GutenbergLanguageItem item = getItem(position);
        TextView tvName = convertView.findViewById(R.id.tvLanguageName);
        TextView tvNative = convertView.findViewById(R.id.tvLanguageNative);
        TextView tvCount = convertView.findViewById(R.id.tvLanguageCount);
        ImageView ivFlag = convertView.findViewById(R.id.ivFlag);

        if (item == null) {
            myLogE("item is null");
            return convertView;
        }
        tvName.setText(item.name);
        tvNative.setText(item.nativeName != null && !item.nativeName.isEmpty() ? item.nativeName : "");
        tvCount.setText(item.bookCount != null ? item.bookCount : "+0");
        ivFlag.setImageResource(item.flagRes);

        return convertView;
    }
}
