package com.driot.bookplayer.imports;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.Tonio;

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
        holder.tvName.setText(Tonio.formatNameForDisplay(item.name));
        String tracksPart = "";
        // Only show track count for Folders or generic Audio Files (where we know the
        // count)
        // For ZIP, M4B, Ebook, we don't know the count yet, so don't show it.
        if ("Folder".equals(item.type) || "Audio File".equals(item.type)) {
            tracksPart = " - " + item.tracksCount + " "
                    + holder.ivCover.getContext().getString(com.driot.bookplayer.R.string.tracks);
        }
        String txtInfo = item.type
                + " - " + Tonio.getReadableSize(item.size)
                + tracksPart
                + "\n" + item.path;

        holder.tvType.setText(txtInfo);

        // Load cover image if available
        if (item.coverImagePath != null && !item.coverImagePath.isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(item.coverImagePath)
                    .placeholder(R.drawable.no_image_icon)
                    .centerCrop()
                    .into(holder.ivCover);
        } else {
            // No cover - show placeholder
            holder.ivCover.setImageResource(R.drawable.no_image_icon);
        }

        // Set type icon (smaller, overlaid in top-left)
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
        holder.ivTypeIcon.setImageResource(iconRes);

        // Check if already imported
        if (item.isAlreadyImported()) {
            // Apply red tint to type icon
            holder.ivTypeIcon.setColorFilter(0xFFFF0000); // Red color
            // Show "already imported" message
            holder.tvAlreadyImported.setVisibility(android.view.View.VISIBLE);
            String txtError = holder.itemView.getContext()
                    .getString(com.driot.bookplayer.R.string.already_imported_under_name) + item.existingBookName;
            holder.tvAlreadyImported.setText(txtError);
        } else {
            // Clear color filter (normal icon)
            holder.ivTypeIcon.clearColorFilter();
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
        ImageView ivCover;
        ImageView ivTypeIcon;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvType = itemView.findViewById(R.id.tvType);
            tvAlreadyImported = itemView.findViewById(R.id.tvAlreadyImported);
            ivCover = itemView.findViewById(R.id.ivCover);
            ivTypeIcon = itemView.findViewById(R.id.ivTypeIcon);
        }
    }
}
