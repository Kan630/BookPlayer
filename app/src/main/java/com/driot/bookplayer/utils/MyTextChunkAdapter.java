package com.driot.bookplayer.utils;

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
 */
public class MyTextChunkAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final ArrayList<MyTextChunk> myTextChunkArrayList;


    public MyTextChunkAdapter(ArrayList<MyTextChunk> storeMyTextChunkArrayList) {
        this.myTextChunkArrayList = storeMyTextChunkArrayList;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recyclerview_item, parent, false);
        return new MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        final MyTextChunk item = getValueAt(position);
        MyTextChunkAdapter.MyViewHolder myViewHolder = (MyTextChunkAdapter.MyViewHolder) holder;
        if (item != null) {
            setupValuesInWidgets(myViewHolder, item);
        }
    }


    private MyTextChunk getValueAt(int position) {
        return myTextChunkArrayList.get(position);
    }

    @Override
    public int getItemCount() {
        return myTextChunkArrayList.size();
    }

    private void setupValuesInWidgets(MyTextChunkAdapter.MyViewHolder itemHolder, MyTextChunk
            cartMyTextChunk) {
        if (cartMyTextChunk != null) {
            itemHolder.tvText.setText(Html.fromHtml(cartMyTextChunk.getText()));
            itemHolder.tvText.setTextSize(cartMyTextChunk.getCharSize());
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