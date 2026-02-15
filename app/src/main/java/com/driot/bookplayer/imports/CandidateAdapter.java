package com.driot.bookplayer.imports;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.checkbox.MaterialCheckBox;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.Tonio;

import java.util.ArrayList;
import java.util.List;

public class CandidateAdapter extends RecyclerView.Adapter<CandidateAdapter.ViewHolder> {

    private List<BookCandidate> items = new ArrayList<>();

    /**
     * Called when user checks/unchecks a candidate; use to update selection summary
     * and storage bar.
     */
    private Runnable onSelectionChanged;

    public void setOnSelectionChanged(Runnable onSelectionChanged) {
        this.onSelectionChanged = onSelectionChanged;
    }

    public interface OnItemClickListener {
        void onItemClick(BookCandidate candidate);
    }

    private OnItemClickListener onItemClickListener;

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    public void setItems(List<BookCandidate> items) {
        this.items = items;
        // Do NOT reset selection here. BookCandidate initializes it correctly.
        // Resetting here obliterates user changes during progressive updates.
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
        // Show track count for Folders, generic Audio Files, Archive archives, and M4B
        // files (where we know the count)
        // For Ebook, we don't know the count yet, so don't show it.
        // Show track count for Folders, generic Audio Files, Archive archives, and M4B
        // files (where we know the count)
        // For Ebook, we don't know the count yet, so don't show it.
        if (item.isHeavyLoaded) {
            if ("Folder".equals(item.sourceType) || "Audio File".equals(item.sourceType)
                    || "Archive".equals(item.sourceType)
                    || "M4B".equals(item.sourceType)) {
                tracksPart = " - " + holder.ivCover.getContext().getResources()
                        .getQuantityString(com.driot.bookplayer.R.plurals.tracks_count, item.tracksCount,
                                item.tracksCount);
            }
        } else {
            // Not loaded yet, check if we have partial results (Archive scanning)
            if ("Archive".equals(item.sourceType) && !item.trackList.isEmpty()) {
                int count = item.trackList.size();
                tracksPart = " - counting... " + count + " tracks";
            }
        }
        String txtInfo = item.sourceType
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
        if ("Folder".equals(item.sourceType)) {
            iconRes = R.drawable.ic_folder_24px;
        } else if ("Archive".equals(item.sourceType)) {
            iconRes = R.drawable.ic_folder_zip_24px;
        } else if ("M4B".equals(item.sourceType)) {
            iconRes = R.drawable.ic_file_m4b;
        } else if ("Ebook".equals(item.sourceType)) {
            iconRes = R.drawable.ic_docs_24px;
        } else {
            iconRes = R.drawable.ic_audio_file_24px;
        }
        holder.ivTypeIcon.setImageResource(iconRes);

        // Detach listener first to avoid firing it while setting state
        holder.cbSelect.setOnCheckedChangeListener(null);

        // Check if already imported
        if (item.isAlreadyImported()) {
            // Apply red tint to type icon
            holder.ivTypeIcon.setColorFilter(0xFFFF0000); // Red color
            // Show "already imported" message
            holder.tvAlreadyImported.setVisibility(android.view.View.VISIBLE);
            String txtError = holder.itemView.getContext()
                    .getString(com.driot.bookplayer.R.string.already_imported_under_name) + item.existingBookName;
            holder.tvAlreadyImported.setText(txtError);
            // Checkbox unchecked and disabled for already-imported
            holder.cbSelect.setChecked(false);
            holder.cbSelect.setEnabled(false);
        } else {
            // Clear color filter (normal icon)
            holder.ivTypeIcon.clearColorFilter();
            // Hide "already imported" message
            holder.tvAlreadyImported.setVisibility(android.view.View.GONE);
            // Checkbox reflects selection state, enabled
            holder.cbSelect.setEnabled(true);
            holder.cbSelect.setChecked(item.isSelected());
        }

        // Re-attach listener
        holder.cbSelect.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            item.setSelected(isChecked);
            if (onSelectionChanged != null)
                onSelectionChanged.run();
        });

        holder.itemView.setOnClickListener(v -> {
            if (onItemClickListener != null) {
                onItemClickListener.onItemClick(item);
            }
        });
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
        MaterialCheckBox cbSelect;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvType = itemView.findViewById(R.id.tvType);
            tvAlreadyImported = itemView.findViewById(R.id.tvAlreadyImported);
            ivCover = itemView.findViewById(R.id.ivCover);
            ivTypeIcon = itemView.findViewById(R.id.ivTypeIcon);
            cbSelect = itemView.findViewById(R.id.cbSelect);
        }
    }
}
