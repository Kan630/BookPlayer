package com.driot.bookplayer;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 28/10/20
 */

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

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

    @Override
    public void onBindViewHolder(ZikFilesViewHolder holder, int position) {
        ZikFile t = ZikFileList.get(position);
        holder.textViewFileName.setText(t.getName());
        holder.textViewFilePercent.setText(t.getPercentdone().toString());
        holder.textViewFileLastAccess.setText(t.getLastaccess().toString());
    }

    @Override
    public int getItemCount() {
        return ZikFileList.size();
    }

    class ZikFilesViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        TextView textViewFileName, textViewFilePercent, textViewFileLastAccess;

        public ZikFilesViewHolder(View itemView) {
            super(itemView);

            textViewFileName = itemView.findViewById(R.id.textViewFileName);
            textViewFilePercent = itemView.findViewById(R.id.textViewFilePercent);
            textViewFileLastAccess = itemView.findViewById(R.id.textViewFileLastAccess);

            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            ZikFile ZikFile = ZikFileList.get(getAdapterPosition());

            Intent intent = new Intent(mCtx, PlayActivity.class);
            intent.putExtra("ZikFile", ZikFile);

            mCtx.startActivity(intent);
        }
    }
}
