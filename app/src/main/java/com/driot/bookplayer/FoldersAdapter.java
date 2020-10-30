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

public class FoldersAdapter extends RecyclerView.Adapter<FoldersAdapter.FoldersViewHolder> {

    private Context mCtx;
    private List<Folder> FolderList;

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
        holder.textViewFileLastAccess.setText(t.getLastaccess().toString());
    }

    @Override
    public int getItemCount() {
        return FolderList.size();
    }

    class FoldersViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        TextView textViewFileName, textViewFilePercent, textViewFileLastAccess;

        public FoldersViewHolder(View itemView) {
            super(itemView);

            textViewFileName = itemView.findViewById(R.id.textViewFileName);
            textViewFilePercent = itemView.findViewById(R.id.textViewFilePercent);
            textViewFileLastAccess = itemView.findViewById(R.id.textViewFileLastAccess);

            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            Folder Folder = FolderList.get(getAdapterPosition());

            //Intent intent = new Intent(mCtx, PlayActivity.class);
            Intent intent = new Intent(mCtx, FolderContentActivity.class);
            intent.putExtra("Folder", Folder);

            mCtx.startActivity(intent);
        }
    }
}
