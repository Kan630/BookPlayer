package com.driot.bookplayer.adapter;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 28/10/20
 */

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.ModifyFolderActivity;
import com.driot.bookplayer.activities.PlayActivity;
import com.driot.bookplayer.activities.ZikFileActivity;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.objects.PlayList;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingRVAdapter;

import java.util.List;
import java.util.Locale;

import static com.driot.bookplayer.utils.KanLogger.myToastE;
import static com.driot.bookplayer.utils.Tonio.*;

public class FoldersRVAdapter extends LoggingRVAdapter<FoldersRVAdapter.FoldersViewHolder> {

    private final Context mCtx;
    private final List<Folder> FolderList;

    public FoldersRVAdapter(Context mCtx, List<Folder> FolderList) {
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

        holder.textViewFilePercent.setText(FormatPercentString(folder.getPercentdone()));

        holder.mProgressBar.setProgress(FormatPercentForProgressBar(folder.getPercentdone()));

        holder.textViewFileLastAccess.setText(Tonio.formatLastAccess(folder.lLastAccess, mCtx));

        holder.textViewDuration.setText(formatTime(folder.getDuration()));

        holder.ivMemory.setImageResource(folder.getMemoryLocationIcon(mCtx));

    }

    @Override
    public int getItemCount() {
        return FolderList.size();
    }

    class FoldersViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

        TextView textViewFileName, textViewFileLastAccess, textViewFilePercent, textViewDuration;
        ProgressBar mProgressBar;
        ImageView ivMemory;

        public FoldersViewHolder(View itemView) {
            super(itemView);

            textViewFileName = itemView.findViewById(R.id.textViewFileName);
            textViewFilePercent = itemView.findViewById(R.id.textViewFilePercent);
            textViewFileLastAccess = itemView.findViewById(R.id.textViewFileLastAccess);
            textViewDuration =  itemView.findViewById(R.id.textViewDuration);
            mProgressBar = itemView.findViewById(R.id.progressBar);
            ivMemory = itemView.findViewById(R.id.imageViewStorageIcon);

            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
        }

        @Override
        public void onClick(View view) {
            Folder folder = FolderList.get(getBindingAdapterPosition());
            new Thread(() -> {
                try {
                    List<ZikFile> zikFilesList = AppDatabase.getDatabase(mCtx).ZikFileDao().getZikFiles(folder.getId());
                    myLogI("nb ZikFiles in that Book : " + zikFilesList.size() + " - [" + folder.getName() + "]");
                    PlayList.create(mCtx, zikFilesList);
                    if (zikFilesList.size() > 1) {
                        mCtx.startActivity(new Intent(mCtx, ZikFileActivity.class)
                                .putExtra("FolderId", folder.getId())
                                .putExtra("FolderName", folder.getName())
                        );
                    } else if (zikFilesList.size() == 1) {
                        PlayList.getInstance().setNumZikFile(0);
                        mCtx.startActivity(new Intent(mCtx, PlayActivity.class).putExtra("ZikFile", zikFilesList.get(0)));
                    } else {
                        myLogE("no ZikFiles in that folder !");
                        myToastE(mCtx.getString(R.string.ErrorCouldNotLoadAudios_emptyfolder));
                    }
                } catch (Exception e) {
                    myLogEE(e,"error getting nb of ZikFiles");
                    myToastE(mCtx.getString(R.string.ErrorCouldNotLoadAudios));
                }
            }).start();
        }


        @Override
        public boolean onLongClick(View view) {
            myLogI("onLongClick");
            Folder folder = FolderList.get(getBindingAdapterPosition());
            mCtx.startActivity(new Intent(mCtx, ModifyFolderActivity.class).putExtra("folder", folder));
            return false;
        }



    }



}
