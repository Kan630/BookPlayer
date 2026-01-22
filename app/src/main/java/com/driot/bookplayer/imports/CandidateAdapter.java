package com.driot.bookplayer.imports;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;

import java.util.ArrayList;
import java.util.List;

public class CandidateAdapter extends RecyclerView.Adapter<CandidateAdapter.ViewHolder> {

    private List<BookCandidate> items = new ArrayList<>();

    public void setItems(List<BookCandidate> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    public List<BookCandidate> getItems() {
        return items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_candidate, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BookCandidate item = items.get(position);
        holder.tvName.setText(com.driot.bookplayer.utils.Tonio.formatNameForDisplay(item.name));
        holder.tvType.setText(
                item.type + " - " + com.driot.bookplayer.utils.Tonio.getReadableSize(item.size) + "\n" + item.path);

        // Simple icon logic
        int iconRes;
        if ("Folder".equals(item.type)) {
            iconRes = R.drawable.ic_folder_24px;
        } else if ("ZIP".equals(item.type)) {
            iconRes = R.drawable.ic_folder_zip_24px;
        } else if ("M4B".equals(item.type)) {
            iconRes = R.drawable.ic_file_m4b;
        } else if ("Ebook".equals(item.type)) {
            iconRes = R.drawable.ic_docs_24px;
        } else {
            iconRes = R.drawable.ic_audio_file_24px;
        }
        holder.ivIcon.setImageResource(iconRes);

        // Check if already imported
        if (item.isAlreadyImported()) {
            // Apply red tint to icon
            holder.ivIcon.setColorFilter(0xFFFF0000); // Red color
            // Show "already imported" message
            holder.tvAlreadyImported.setVisibility(android.view.View.VISIBLE);
            holder.tvAlreadyImported.setText("Already imported under the name: " + item.existingBookName);
        } else {
            // Clear color filter (normal icon)
            holder.ivIcon.clearColorFilter();
            // Hide "already imported" message
            holder.tvAlreadyImported.setVisibility(android.view.View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvType;
        TextView tvAlreadyImported;
        ImageView ivIcon;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvType = itemView.findViewById(R.id.tvType);
            tvAlreadyImported = itemView.findViewById(R.id.tvAlreadyImported);
            ivIcon = itemView.findViewById(R.id.ivIcon);
        }
    }
}
