package com.driot.bookplayer.adapter;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;

import com.driot.bookplayer.R;
import com.driot.bookplayer.objects.VoiceItem;
import java.util.List;

public class VoiceSpinnerAdapter extends ArrayAdapter<VoiceItem> {
    private int selectedPosition = -1;
    private final Context context;

    public VoiceSpinnerAdapter(@NonNull Context context, @NonNull List<VoiceItem> voices) {
        super(context, 0, voices);
        this.context = context;
    }

    public void setSelectedPosition(int position) {
        if (selectedPosition != position) {
            selectedPosition = position;
            notifyDataSetChanged();
        }
    }

    static class VH {
        ImageView ivLang, ivVoice, ivSource;
        TextView tvName;
        View rootView;
    }

    @NonNull @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return bind(convertView, parent, getItem(position), position, true);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return bind(convertView, parent, getItem(position), position, false);
    }

    private View bind(View convertView, ViewGroup parent, VoiceItem item, int position, boolean isSelectedView) {
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
            vh.rootView = v;
            v.setTag(vh);
        } else {
            vh = (VH) v.getTag();
        }

        boolean isSelected = (selectedPosition >= 0 && position == selectedPosition);

        if (item != null) {
            vh.tvName.setText(item.displayName);
            
            // Apply visual styling for selected item
            if (isSelected) {
                // Make text bold and use primary color
                vh.tvName.setTypeface(null, android.graphics.Typeface.BOLD);
                // Get primary color from theme
                TypedValue typedValue = new TypedValue();
                boolean resolved = context.getTheme().resolveAttribute(android.R.attr.colorPrimary, typedValue, true);
                if (resolved && typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                    vh.tvName.setTextColor(typedValue.data);
                } else {
                    // Fallback to purple_200 or a visible color
                    try {
                        int primaryColor = ContextCompat.getColor(context, R.color.purple_200);
                        vh.tvName.setTextColor(primaryColor);
                    } catch (Exception e) {
                        // Final fallback
                        vh.tvName.setTextColor(Color.parseColor("#BB86FC"));
                    }
                }
                // Add subtle background highlight for selected item in dropdown
                if (!isSelectedView) {
                    vh.rootView.setBackgroundColor(Color.parseColor("#1A000000")); // Very light gray
                }
            } else {
                // Reset to normal styling
                vh.tvName.setTypeface(null, android.graphics.Typeface.NORMAL);
                // Get default text color from theme
                TypedValue typedValue = new TypedValue();
                boolean resolved = context.getTheme().resolveAttribute(android.R.attr.textColorPrimary, typedValue, true);
                if (resolved && typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                    vh.tvName.setTextColor(typedValue.data);
                } else {
                    // Fallback: try to get color from resources or use default
                    try {
                        int defaultColor = ContextCompat.getColor(context, android.R.color.primary_text_light);
                        vh.tvName.setTextColor(defaultColor);
                    } catch (Exception e) {
                        // Final fallback
                        vh.tvName.setTextColor(Color.parseColor("#1C1B1F")); // Default dark text color
                    }
                }
                if (!isSelectedView) {
                    vh.rootView.setBackgroundColor(Color.TRANSPARENT);
                }
            }

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


