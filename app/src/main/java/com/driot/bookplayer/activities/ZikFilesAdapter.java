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
import com.driot.bookplayer.db.ZikFile;

import java.util.List;

import static com.driot.bookplayer.utils.Tonio.*;




public class ZikFilesAdapter extends RecyclerView.Adapter<ZikFilesAdapter.ZikFilesViewHolder> {

    private Context mCtx;
    private List<ZikFile> ZikFileList;

    public ZikFilesAdapter(Context mCtx, List<ZikFile> ZikFileList) {
        this.mCtx = mCtx;
        this.ZikFileList = ZikFileList;
    }

    @Override
    public ZikFilesViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mCtx).inflate(R.layout.recyclerview_zikfiles, parent, false);
        return new ZikFilesViewHolder(view);
    }

    /********************************************************************************
     ***       SETTING VALUES
     ********************************************************************************
     */
    @Override
    public void onBindViewHolder(ZikFilesViewHolder holder, int position) {
        RedrawViewHolderElements(holder, position);
    }

    public void RedrawViewHolderElements(ZikFilesViewHolder holder, int position) {
        ZikFile t = ZikFileList.get(position);

        holder.textViewFileName.setText(stripExtension(t.getName()));

        holder.textViewFilePercent.setText(FormatPercentString(t.getPercentdone()));

        holder.mProgressBar.setProgress(FormatPercentForProgressBar(t.getPercentdone()));

        holder.textViewFileLastAccess.setText(FormatLastAccess(t.getLastaccess(),t.getLastaccessTime(), mCtx.getString(R.string.yesterday)));

        holder.textViewDuration.setText(FormatTime(t.getDuration()));

    }


    @Override
    public int getItemCount() {
        return ZikFileList.size();
    }

    class ZikFilesViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        TextView textViewFileName, textViewFileLastAccess, textViewFilePercent, textViewDuration;
        ProgressBar mProgressBar;


        public ZikFilesViewHolder(View itemView) {
            super(itemView);

            textViewFileName = itemView.findViewById(R.id.textViewFileName);
            textViewFilePercent = itemView.findViewById(R.id.textViewFilePercent);
            textViewFileLastAccess = itemView.findViewById(R.id.textViewFileLastAccess);
            textViewDuration =  itemView.findViewById(R.id.textViewDuration);
            mProgressBar = itemView.findViewById(R.id.progressBar);

            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            ZikFile zikFile = ZikFileList.get(getAdapterPosition());

            Intent intent = new Intent(mCtx, PlayActivity.class);

            //TODO pass an object, check parcelable
            intent.putExtra("ZikFile", zikFile);

            mCtx.startActivity(intent);
        }
    }
}
