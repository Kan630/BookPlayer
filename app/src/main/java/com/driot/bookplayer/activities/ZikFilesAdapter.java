package com.driot.bookplayer.activities;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 28/10/20
 */

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.ZikFile;

import java.sql.Date;
import java.sql.Time;
import java.text.SimpleDateFormat;
import java.util.List;

import static com.driot.bookplayer.utils.Tonio.FormatTime;

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
     */    @Override
    public void onBindViewHolder(ZikFilesViewHolder holder, int position) {
        ZikFile t = ZikFileList.get(position);

        holder.textViewFileName.setText(t.getName());

        holder.textViewFilePercent.setText(FormatPercentString(t.getPercentdone()));

        holder.mProgressBar.setProgress(FormatPercentInt(t.getPercentdone()));

        holder.textViewFileLastAccess.setText(FormatLastAccess(t.getLastaccess(),t.getLastaccessTime()));

        holder.textViewLength.setText(FormatTime(t.getLength()));
    }

    private String FormatPercentString(Double d) {
        String str;
        if (d != null) {
            d = d*100;
            str = d.toString().substring(0,2);
            if (str.substring(1).equals(".")) {str=str.substring(0,1);}
            str = str + " %";
        } else {
          str = "";
         }
         return str;
    }
    private int FormatPercentInt(Double d) {
        int i;
        if (d != null) {
            d = d * 100;
            i = d.intValue();
            if (i < 0) {
                i = 0;
            }
            if (i > 100) {
                i = 100;
            }
        } else {
            i = 0;
        }
        return i;
    }
    private String FormatLastAccess(Date d, Time t) {
        String s;
         if (d != null && t != null) {
             Date d2 = new Date(System.currentTimeMillis());
             String s1 = d.toString();
             String s2 = d2.toString();
             Log.d("toto","d : --" + s1 + "--");
             Log.d("toto","new date : --" + s2 + "--");
             // check if date same as today
             if (s1.equals(s2)) {
                 // Give time :
                 s = t.toString();
                 s = s.substring(0, 5);
             } else {
                 //give date :
                 @SuppressLint("SimpleDateFormat") SimpleDateFormat simpleDate =  new SimpleDateFormat("dd/MM/yyyy");
                 s = simpleDate.format(d);
             }
        } else {
            s = " ";
        }
        return s;
    }

    @Override
    public int getItemCount() {
        return ZikFileList.size();
    }

    class ZikFilesViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        TextView textViewFileName, textViewFileLastAccess, textViewFilePercent, textViewLength;
        ProgressBar mProgressBar;


        public ZikFilesViewHolder(View itemView) {
            super(itemView);

            textViewFileName = itemView.findViewById(R.id.textViewFileName);
            textViewFilePercent = itemView.findViewById(R.id.textViewFilePercent);
            textViewFileLastAccess = itemView.findViewById(R.id.textViewFileLastAccess);
            textViewLength =  itemView.findViewById(R.id.textViewLength);
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
