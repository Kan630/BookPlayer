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
import com.driot.bookplayer.activities.ZikFileModifyActivity;
import com.driot.bookplayer.global.PlayList;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.utils.KanLogger;
import com.driot.bookplayer.utils.Tonio;

import java.util.List;

import static com.driot.bookplayer.utils.Tonio.FormatLastAccess;
import static com.driot.bookplayer.utils.Tonio.FormatPercentForProgressBar;
import static com.driot.bookplayer.utils.Tonio.FormatPercentString;
import static com.driot.bookplayer.utils.Tonio.formatTime;

public class ZikFilesAdapter extends RecyclerView.Adapter<ZikFilesAdapter.ZikFilesViewHolder> {

    private final Context mCtx;
    private final List<ZikFile> zikFileList;

    public ZikFilesAdapter(Context mCtx, List<ZikFile> zikFileList) {
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
        holder.textViewFilePercent.setText(FormatPercentString(t.getPercentdone()));
        holder.mProgressBar.setProgress(FormatPercentForProgressBar(t.getPercentdone()));
        holder.textViewFileLastAccess.setText(FormatLastAccess(t.getLastaccess(), t.getLastaccessTime(), mCtx.getString(R.string.yesterday)));
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

            textViewFileName = itemView.findViewById(R.id.textViewFileName);
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
            myLog("onClick() : [" + zikFile.getName() + "] - [" + zikFile.getPath() + "/" + zikFile.getName() + "]");
            PlayList.setNumZikFile(mCtx, position);
            mCtx.startActivity(new Intent(mCtx, PlayActivity.class).putExtra("ZikFile", zikFile));
        }

        @Override
        public boolean onLongClick(View view) {
            ZikFile zikFile = zikFileList.get(getBindingAdapterPosition());
            myLog("onLongClick() : [" + zikFile.getName() + "] - [" + zikFile.getPath() + "/" + zikFile.getName() + "]");

            mCtx.startActivity(new Intent(mCtx, ZikFileModifyActivity.class).putExtra("ZikFile", zikFile));
            return false;
        }
    }


    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
}
