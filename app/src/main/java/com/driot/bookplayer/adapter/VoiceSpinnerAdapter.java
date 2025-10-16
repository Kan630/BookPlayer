package com.driot.bookplayer.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;

import com.driot.bookplayer.R;
import com.driot.bookplayer.objects.VoiceItem;
import java.util.List;

public class VoiceSpinnerAdapter extends ArrayAdapter<VoiceItem> {
    public VoiceSpinnerAdapter(@NonNull Context context, @NonNull List<VoiceItem> voices) {
        super(context, 0, voices);
    }

    static class VH {
        ImageView ivLang, ivVoice, ivSource;
        TextView tvName;
    }

    @NonNull @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return bind(convertView, parent, getItem(position));
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return bind(convertView, parent, getItem(position));
    }

    private View bind(View convertView, ViewGroup parent, VoiceItem item) {
        View v = convertView;
        VH vh;
        if (v == null) {
            v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.spinner_voice_item, parent, false);
            vh = new VH();
            vh.ivLang = v.findViewById(R.id.iv_LanguageFlag);
            vh.ivVoice = v.findViewById(R.id.iv_VoiceFlag);
            vh.ivSource = v.findViewById(R.id.iv_sourceFlag);
            vh.tvName = v.findViewById(R.id.tv_VoiceName);
            v.setTag(vh);
        } else {
            vh = (VH) v.getTag();
        }

        if (item != null) {
            vh.tvName.setText(item.displayName);

            // Reset first
            vh.ivLang.setImageDrawable(null);
            vh.ivVoice.setImageDrawable(null);
            vh.ivSource.setImageDrawable(null);

            // Language flag (first)
            if (item.flagResIdLanguage != 0) {
                vh.ivLang.setVisibility(View.VISIBLE);
                vh.ivLang.setImageResource(item.flagResIdLanguage);
            } else {
                vh.ivLang.setVisibility(View.GONE);
            }

            // Country/region flag (second)
            if (item.flagResIdCountry != 0 && item.flagResIdCountry != item.flagResIdLanguage) {
                vh.ivVoice.setVisibility(View.VISIBLE);
                vh.ivVoice.setImageResource(item.flagResIdCountry);
            } else {
                vh.ivVoice.setVisibility(View.GONE);
            }

            if (item.displayName.contains("Online")) {
                vh.ivSource.setImageDrawable(AppCompatResources.getDrawable(this.getContext(), R.drawable.ic_cloud_24));
            } else if (item.displayName.contains("Voice")) {
                vh.ivSource.setImageDrawable(AppCompatResources.getDrawable(this.getContext(), R.drawable.ic_sync_saved_locally_24));
            }
        }
        return v;
    }
}


