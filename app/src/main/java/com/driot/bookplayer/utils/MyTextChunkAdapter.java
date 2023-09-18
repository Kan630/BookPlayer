package com.driot.bookplayer.utils;

import static com.driot.tonylib.KanLogger.myLog;

import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import com.driot.bookplayer.R;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 02/12/20
 *
 * Adapter for displaying content of a file
 *
 */
public class MyTextChunkAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final ArrayList<MyTextChunk> myTextChunkArrayList;


    public MyTextChunkAdapter(ArrayList<MyTextChunk> storeMyTextChunkArrayList) {
        this.myTextChunkArrayList = storeMyTextChunkArrayList;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        //myLog("MyTextChunkAdapter.onCreateViewHolder");
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recyclerview_item, parent, false);
        return new MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        //myLog("MyTextChunkAdapter.onBindViewHolder");
        final MyTextChunk myTextChunk = getValueAt(position);
        MyTextChunkAdapter.MyViewHolder myViewHolder = (MyTextChunkAdapter.MyViewHolder) holder;
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

    private void setupValuesInWidgets(MyTextChunkAdapter.MyViewHolder itemHolder, MyTextChunk myTextChunk) {
        if (myTextChunk != null) {
            //myLog("zetext : " + myTextChunk.getText());
            //itemHolder.tvText.setText(myTextChunk.getText());
            itemHolder.tvText.setText(Html.fromHtml(myTextChunk.getText()));
            itemHolder.tvText.setTextSize(myTextChunk.getCharSize());
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