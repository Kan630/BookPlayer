package com.driot.bookplayer.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.helpers.IconHelper;

import java.util.List;

public class FolderSpinnerAdapter extends ArrayAdapter<Folder> {

    private Context context;
    private List<Folder> folders;

    public FolderSpinnerAdapter(Context context, List<Folder> folders) {
        super(context, 0, folders);
        this.context = context;
        this.folders = folders;
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
        View view = LayoutInflater.from(context).inflate(R.layout.spinner_folder_item, parent, false);
        Folder folder = folders.get(position);

        ImageView ivCover = view.findViewById(R.id.ivCover);
        Glide.with(ivCover.getContext()).load(folder.image).into(ivCover);

        ImageView ivPlayMode = view.findViewById(R.id.ivPlayMode);
        IconHelper.setSourceIcon(ivPlayMode, folder.getSourceLocation(), folder.playType);

        TextView tvFolderName = view.findViewById(R.id.tvFolderName);
        tvFolderName.setText(folder.getName());

        TextView tvCount = view.findViewById(R.id.tvCount);
        tvCount.setText(String.valueOf(folder.nbZikFile).replace("0", ""));


        return view;
    }
}






