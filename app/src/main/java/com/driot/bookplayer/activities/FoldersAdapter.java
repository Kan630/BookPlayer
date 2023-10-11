package com.driot.bookplayer.activities;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 28/10/20
 */

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.Folder;
import com.driot.tonylib.KanLogger;

import java.util.List;

import static com.driot.bookplayer.utils.Tonio.*;

public class FoldersAdapter extends RecyclerView.Adapter<FoldersAdapter.FoldersViewHolder> {

    private final Context mCtx;
    private final List<Folder> FolderList;

    public FoldersAdapter(Context mCtx, List<Folder> FolderList) {
        this.mCtx = mCtx;
        this.FolderList = FolderList;
    }

    @Override
    public FoldersViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mCtx).inflate(R.layout.recyclerview_folders, parent, false);
        return new FoldersViewHolder(view);
    }

    @Override
    public void onBindViewHolder(FoldersViewHolder holder, int position) {
        Folder t = FolderList.get(position);
        holder.textViewFileName.setText(t.getName());
        holder.textViewFilePercent.setText(t.getPercentdone().toString());

        if (t.getLastaccess() != null) holder.textViewFileLastAccess.setText(t.getLastaccess().toString());

        holder.textViewFilePercent.setText(FormatPercentString(t.getPercentdone()));

        holder.mProgressBar.setProgress(FormatPercentForProgressBar(t.getPercentdone()));

        if (t.getLastaccess() != null) holder.textViewFileLastAccess.setText(FormatLastAccess(t.getLastaccess(),t.getLastaccessTime(), mCtx.getString(R.string.yesterday)));

        holder.textViewDuration.setText(FormatTime(t.getDuration()));


    }

    @Override
    public int getItemCount() {
        return FolderList.size();
    }

    class FoldersViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

        TextView textViewFileName, textViewFileLastAccess, textViewFilePercent, textViewDuration;
        ProgressBar mProgressBar;

        public FoldersViewHolder(View itemView) {
            super(itemView);

            textViewFileName = itemView.findViewById(R.id.textViewFileName);
            textViewFilePercent = itemView.findViewById(R.id.textViewFilePercent);
            textViewFileLastAccess = itemView.findViewById(R.id.textViewFileLastAccess);
            textViewDuration =  itemView.findViewById(R.id.textViewDuration);
            mProgressBar = itemView.findViewById(R.id.progressBar);

            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
        }

        @Override
        public void onClick(View view) {
            Folder folder = FolderList.get(getAdapterPosition());

            Intent intent = new Intent(mCtx, ZikFileActivity.class);
            intent.putExtra("FolderId", folder.getId());
            intent.putExtra("FolderName", folder.getName());
            mCtx.startActivity(intent);
        }

        @Override
        public boolean onLongClick(View view) {
            Folder folder = FolderList.get(getAdapterPosition());

            Intent intent = new Intent(mCtx, FolderModifyActivity.class);
            intent.putExtra("FolderName", folder.getName());
            intent.putExtra("FolderId", folder.getId());

            mCtx.startActivity(intent);
            return false;
        }

    }
}
