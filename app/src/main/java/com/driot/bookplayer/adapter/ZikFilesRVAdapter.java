package com.driot.bookplayer.adapter;

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
import com.driot.bookplayer.activities.PlayActivity;
import com.driot.bookplayer.activities.ModifyZikFileActivity;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.objects.PlayList;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingRVAdapter;

import java.util.List;

public class ZikFilesRVAdapter extends LoggingRVAdapter<ZikFilesRVAdapter.ZikFilesViewHolder> {

    private final Context mCtx;
    private final List<ZikFile> zikFileList;

    public ZikFilesRVAdapter(Context mCtx, List<ZikFile> zikFileList) {
        this.mCtx = mCtx;
        this.zikFileList = zikFileList;
    }

    @NonNull
    @Override
    public ZikFilesViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mCtx).inflate(R.layout.recyclerview_zikfiles, parent, false);
        return new ZikFilesViewHolder(view);
    }

    /********************************************************************************
     ***       SETTING VALUES
     ********************************************************************************
     */
    @Override
    public void onBindViewHolder(@NonNull ZikFilesViewHolder holder, int position) {
        RedrawViewHolderElements(holder, position);
    }

    public void RedrawViewHolderElements(ZikFilesViewHolder holder, int position) {
        ZikFile t = zikFileList.get(position);

        holder.textViewFileName.setText(t.getDisplayName());
        Option.applyUserTextAppearance(holder.textViewFileName);

        holder.textViewFilePercent.setText(Tonio.FormatPercentString(t.getPercentdone()));
        holder.mProgressBar.setProgress(Tonio.FormatPercentForProgressBar(t.getPercentdone()));
        holder.textViewFileLastAccess.setText(Tonio.formatLastAccess(t.lLastAccess, mCtx));
        holder.textViewDuration.setText(Tonio.formatTime(t.getDuration()));
    }


    @Override
    public int getItemCount() {
        return zikFileList.size();
    }

    class ZikFilesViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

        TextView textViewFileName, textViewFileLastAccess, textViewFilePercent, textViewDuration;
        ProgressBar mProgressBar;


        public ZikFilesViewHolder(View itemView) {
            super(itemView);

            textViewFileName = itemView.findViewById(R.id.tvBookName);
            textViewFilePercent = itemView.findViewById(R.id.textViewFilePercent);
            textViewFileLastAccess = itemView.findViewById(R.id.textViewFileLastAccess);
            textViewDuration = itemView.findViewById(R.id.textViewDuration);
            mProgressBar = itemView.findViewById(R.id.progressBar);

            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
        }

        @Override
        public void onClick(View view) {
            int position = getBindingAdapterPosition();
            ZikFile zikFile = zikFileList.get(position);
            myLogI("USER CLICKS ZIKFILE : [" + zikFile.getName() + "] - [" + zikFile.getPath() + "]");
            if (PlayList.getInstance()!=null) {
                PlayList.getInstance().setNumZikFile(position);
            } else {
                myToastEE(null, mCtx.getString(R.string.error_reading_track));
                return;
            }
            mCtx.startActivity(new Intent(mCtx, PlayActivity.class));
        }

        @Override
        public boolean onLongClick(View view) {
            ZikFile zikFile = zikFileList.get(getBindingAdapterPosition());
            myLogI("onLongClick() : [" + zikFile.getName() + "] - [" + zikFile.getPath() + "/" + zikFile.getName() + "]");

            mCtx.startActivity(new Intent(mCtx, ModifyZikFileActivity.class).putExtra("ZikFile", zikFile));
            return false;
        }
    }

}
