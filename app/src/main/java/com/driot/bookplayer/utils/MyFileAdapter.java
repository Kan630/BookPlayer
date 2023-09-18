package com.driot.bookplayer.utils;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import com.driot.bookplayer.R;

import static com.driot.bookplayer.utils.Tonio2.loadBiggerText;
import static com.driot.tonylib.KanLogger.myLog;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 02/12/20
 */
public class MyFileAdapter extends RecyclerView.Adapter<MyFileAdapter.MyFileViewHolder> {

    private final Context mContext;
    private final ArrayList<MyFile> myFileArrayList;

    public MyFileAdapter(Activity activity, ArrayList<MyFile> storeMyFileArrayList) {
        this.mContext = activity;
        this.myFileArrayList = storeMyFileArrayList;

    }

    @NonNull
    @Override
    public MyFileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.recyclerview_file, parent, false);
        return new MyFileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(MyFileViewHolder holder, int position) {
        MyFile item = myFileArrayList.get(position);
        holder.tvDate.setText(item.getDate());
        holder.tvTitle.setText(item.getTitle());
    }

    @Override
    public int getItemCount() {
        return myFileArrayList.size();
    }

    public class MyFileViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        AppCompatTextView tvDate, tvTitle, tvAutorite;

        public MyFileViewHolder(View itemView) {
            super(itemView);

            tvDate = itemView.findViewById(R.id.tvDate);
            tvTitle = itemView.findViewById(R.id.tvTitle);

            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            myLog("MyFileAdapter.onClick()");
            MyFile myFile = myFileArrayList.get(getAdapterPosition());
            loadBiggerText(mContext, "classic", myFile.getFileName(), "Log");
        }
    }
}