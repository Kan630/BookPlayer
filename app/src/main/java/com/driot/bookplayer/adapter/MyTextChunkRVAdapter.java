package com.driot.bookplayer.adapter;

import android.graphics.Color;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import com.driot.bookplayer.R;
import com.driot.bookplayer.objects.MyTextChunk;
import com.driot.bookplayer.utils.log.LoggingRVAdapter;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 02/12/20
 *
 * Adapter for displaying content of a file
 *
 */
public class MyTextChunkRVAdapter extends LoggingRVAdapter<RecyclerView.ViewHolder> {

    private ArrayList<MyTextChunk> myTextChunkArrayList;

    public MyTextChunkRVAdapter(ArrayList<MyTextChunk> storeMyTextChunkArrayList) {
        this.myTextChunkArrayList = storeMyTextChunkArrayList;
    }

    public void updateData(ArrayList<MyTextChunk> newData) {
        this.myTextChunkArrayList = newData;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // myLog("MyTextChunkAdapter.onCreateViewHolder");
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recyclerview_item, parent, false);
        return new MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        // myLog("MyTextChunkAdapter.onBindViewHolder");
        final MyTextChunk myTextChunk = getValueAt(position);
        MyTextChunkRVAdapter.MyViewHolder myViewHolder = (MyTextChunkRVAdapter.MyViewHolder) holder;
        if (myTextChunk != null) {
            setupValuesInWidgets(myViewHolder, myTextChunk);
        }
    }

    private MyTextChunk getValueAt(int position) {
        return myTextChunkArrayList.get(position);
    }

    @Override
    public int getItemCount() {
        return myTextChunkArrayList.size();
    }

    private void setupValuesInWidgets(MyTextChunkRVAdapter.MyViewHolder itemHolder, MyTextChunk myTextChunk) {
        if (myTextChunk != null) {
            String text = myTextChunk.getText();
            itemHolder.tvText.setText(Html.fromHtml(text));
            itemHolder.tvText.setTextSize(myTextChunk.getCharSize());

            // Apply color based on log level
            if (text.contains("ERR")) {
                itemHolder.tvText.setTextColor(Color.RED);
            } else if (text.contains("WAR")) {
                itemHolder.tvText.setTextColor(Color.parseColor("#FFA500")); // Orange
            } else if (text.contains("DEB")) {
                itemHolder.tvText.setTextColor(Color.GREEN);
            } else if (text.contains("VER")) {
                itemHolder.tvText.setTextColor(Color.WHITE);
            } else {
                // Default color for normal logs
                itemHolder.tvText.setTextColor(Color.LTGRAY);
            }
        }
    }

    public static class MyViewHolder extends RecyclerView.ViewHolder {

        AppCompatTextView tvText;

        public MyViewHolder(View itemView) {
            super(itemView);

            tvText = itemView.findViewById(R.id.tvText);

        }
    }
}