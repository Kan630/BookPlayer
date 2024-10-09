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

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.PlayList;
import com.driot.bookplayer.utils.KanLogger;

import java.util.List;
import java.util.Locale;

import static com.driot.bookplayer.utils.Tonio.*;

public class FoldersAdapter extends RecyclerView.Adapter<FoldersAdapter.FoldersViewHolder> {

    private final Context mCtx;
    private final List<Folder> FolderList;

    public FoldersAdapter(Context mCtx, List<Folder> FolderList) {
        this.mCtx = mCtx;
        this.FolderList = FolderList;
    }

    @NonNull
    @Override
    public FoldersViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mCtx).inflate(R.layout.recyclerview_folders, parent, false);
        return new FoldersViewHolder(view);
    }

    @Override
    public void onBindViewHolder(FoldersViewHolder holder, int position) {
        Folder folder = FolderList.get(position);
        holder.textViewFileName.setText(folder.getName());
        holder.textViewFilePercent.setText(String.format(folder.getPercentdone().toString(), Locale.getDefault()));

        if (folder.getLastaccess() != null) holder.textViewFileLastAccess.setText(folder.getLastaccess().toString());

        holder.textViewFilePercent.setText(FormatPercentString(folder.getPercentdone()));

        holder.mProgressBar.setProgress(FormatPercentForProgressBar(folder.getPercentdone()));

        if (folder.getLastaccess() != null) holder.textViewFileLastAccess.setText(FormatLastAccess(folder.getLastaccess(),folder.getLastaccessTime(), mCtx.getString(R.string.yesterday)));

        holder.textViewDuration.setText(FormatTime(folder.getDuration()));

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
            Folder folder = FolderList.get(getBindingAdapterPosition());
            new Thread(() -> {
                try {
                    List<ZikFile> zikFilesList = AppDatabase.getDatabase(mCtx).ZikFileDao().getZikFiles(folder.getId());
                    myLog("nb ZikFiles in that Book : " + zikFilesList.size());
                    PlayList.setZikFilesList(zikFilesList);
                    if (zikFilesList.size() > 1) {
                        mCtx.startActivity(new Intent(mCtx, ZikFileActivity.class).putExtra("FolderId", folder.getId()).putExtra("FolderName", folder.getName()));
                    } else {
                        PlayList.setNumZikFile(0);
                        mCtx.startActivity(new Intent(mCtx, PlayActivity.class).putExtra("ZikFile", zikFilesList.get(0)));
                    }
                } catch (Exception e) {
                    myLogE("error getting nb of ZikFiles - " + e.getMessage());
                }
            }).start();
        }

        @Override
        public boolean onLongClick(View view) {
            Folder folder = FolderList.get(getBindingAdapterPosition());
            mCtx.startActivity(new Intent(mCtx, FolderModifyActivity.class).putExtra("FolderName", folder.getName()).putExtra("FolderId", folder.getId()));
            return false;
        }

    }

    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }

}
