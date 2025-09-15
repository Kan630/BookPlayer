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

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.ModifyFolderActivity;
import com.driot.bookplayer.activities.PlayActivity;
import com.driot.bookplayer.activities.PodcastEpisodeActivity;
import com.driot.bookplayer.activities.ZikFileActivity;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.objects.PlayList;
import com.driot.bookplayer.helpers.IconHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingRVAdapter;

import java.util.List;
import java.util.Locale;

import static com.driot.bookplayer.utils.KanLogger.myToastE;
import static com.driot.bookplayer.utils.Tonio.*;

public class FoldersRVAdapter extends LoggingRVAdapter<FoldersRVAdapter.FoldersViewHolder> {

    private final Context mCtx;
    private final List<Folder> folderList;

    public FoldersRVAdapter(Context mCtx, List<Folder> FolderList) {
        this.mCtx = mCtx;
        this.folderList = FolderList;
    }

    @NonNull
    @Override
    public FoldersViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mCtx).inflate(R.layout.recyclerview_folders, parent, false);
        return new FoldersViewHolder(view);
    }

    @Override
    public void onBindViewHolder(FoldersViewHolder holder, int position) {
        Folder folder = folderList.get(position);
        holder.textViewFileName.setText(folder.getName());
        holder.textViewFilePercent.setText(String.format(folder.getPercentdone().toString(), Locale.getDefault()));

        holder.textViewFilePercent.setText(FormatPercentString(folder.getPercentdone()));

        holder.mProgressBar.setProgress(FormatPercentForProgressBar(folder.getPercentdone()));

        holder.textViewFileLastAccess.setText(Tonio.formatLastAccess(folder.lLastAccess, mCtx));

        holder.textViewDuration.setText(formatTime(folder.getDuration()));

        holder.ivMemory.setImageResource(folder.getMemoryLocationIcon(mCtx));

        if (folder.image != null) {
            holder.ivBookCover.setVisibility(View.VISIBLE);
            Glide.with(holder.ivBookCover.getContext()).load(folder.image).into(holder.ivBookCover);
        } else {
            holder.ivBookCover.setVisibility(View.GONE);
        }
        IconHelper.setSourceIcon(holder.ivSource, folder.getSourceLocation(), folder.playType);
    }

    @Override
    public int getItemCount() {
        return folderList.size();
    }

    class FoldersViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

        TextView textViewFileName, textViewFileLastAccess, textViewFilePercent, textViewDuration;
        ProgressBar mProgressBar;
        ImageView ivBookCover, ivMemory, ivSource;

        public FoldersViewHolder(View itemView) {
            super(itemView);

            textViewFileName = itemView.findViewById(R.id.tvBookName);
            textViewFilePercent = itemView.findViewById(R.id.textViewFilePercent);
            textViewFileLastAccess = itemView.findViewById(R.id.textViewFileLastAccess);
            textViewDuration =  itemView.findViewById(R.id.textViewDuration);
            mProgressBar = itemView.findViewById(R.id.progressBar);
            ivMemory = itemView.findViewById(R.id.imageViewStorageIcon);
            ivBookCover = itemView.findViewById(R.id.ivBookCover);
            ivSource = itemView.findViewById(R.id.ivSource);

            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
        }

        @Override
        public void onClick(View view) {
            Folder folder = folderList.get(getBindingAdapterPosition());
            if (folder == null) {
                myLogEE(null,"onClick folder == null");
                return;
            } else {
                myLogI("onClick - position=" + getBindingAdapterPosition() + " - " + folder.getName());
            }

            new Thread(() -> {
                try {
                    List<ZikFile> zikFilesList = AppDatabase.getDatabase(mCtx).ZikFileDao().getZikFiles(folder.getId());
                    myLog("nb ZikFiles in that Book : " + zikFilesList.size() + " - [" + folder.getName() + "]");
                    if (zikFilesList.isEmpty()) {
                        if (folder.getSourceLocation().equals(Var.SOURCE_LOCATION_PODCAST)) {
                            myToastE(mCtx.getString(R.string.no_episode_all_deleted));
                        } else {
                            myLogE("no ZikFiles in that folder !");
                            myToastE(mCtx.getString(R.string.ErrorCouldNotLoadAudios_emptyfolder));
                        }
                    } else {
                        PlayList.create(mCtx, zikFilesList);
                        if (folder.getSourceLocation().equals(Var.SOURCE_LOCATION_PODCAST)) {
                            AppDatabase.databaseReadExecutor.execute(() -> {
                                Podcast podcast = AppDatabase.getDatabase(mCtx).PodcastDao().getPodcastByFolderId(folder.getId());
                                myLogD("opening PodcastEpisodeActivity for podcast : " + podcast.title);
                                mCtx.startActivity(new Intent(mCtx, PodcastEpisodeActivity.class).putExtra("podcast", podcast));
                            });
                        } else {
                            if (zikFilesList.size() > 1) {
                                mCtx.startActivity(new Intent(mCtx, ZikFileActivity.class).putExtra("folder", folder));
                            } else if (zikFilesList.size() == 1) {
                                PlayList.getInstance().setNumZikFile(0);
                                mCtx.startActivity(new Intent(mCtx, PlayActivity.class).putExtra("ZikFile", zikFilesList.get(0)));
                            }
                        }
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
            Folder folder = folderList.get(getBindingAdapterPosition());
            mCtx.startActivity(new Intent(mCtx, ModifyFolderActivity.class).putExtra("folder", folder));
            return false;
        }


    }


}
