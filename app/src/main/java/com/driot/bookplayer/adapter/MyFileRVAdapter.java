package com.driot.bookplayer.adapter;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.KanMail;
import com.driot.bookplayer.objects.MyFile;
import com.driot.bookplayer.utils.log.LoggingRVAdapter;

import static com.driot.bookplayer.utils.Tonio2.loadBiggerText;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 02/12/20
 *
 * Adapter for listing the logs ( =listing the files in log folder)
 *
 */
public class MyFileRVAdapter extends LoggingRVAdapter<MyFileRVAdapter.MyFileViewHolder> {

    private final Context mContext;
    private final ArrayList<MyFile> myFileArrayList;

    public MyFileRVAdapter(Activity activity, ArrayList<MyFile> storeMyFileArrayList) {
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

        AppCompatTextView tvDate, tvTitle;

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

            // send it by mail also !!
            Uri fileUri = MyFile.getUriFromMyFile(mContext, myFile);
            if (fileUri != null) {
                KanMail.sendDaMail(mContext, "bookplayer@driot.com", "**BookplayerLog**", myFile.getFileName() , fileUri);
            } else {
                myLogE("File not found, cannot attach.... [" + myFile.getFileName() + "]");
                myToast("File not found, cannot attach.");
            }

        }
    }

}