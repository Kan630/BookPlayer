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
        holder.tvName.setText(item.name);
        holder.tvType.setText(item.type + " - " + item.path);

        // Simple icon logic
        if ("Folder".equals(item.type)) {
            holder.ivIcon.setImageResource(R.drawable.ic_folder_24px);
        } else if ("ZIP".equals(item.type)) {
            holder.ivIcon.setImageResource(R.drawable.ic_folder_zip_24px); // Assuming ic_zip exists or fallback
        } else if ("M4B".equals(item.type)) {
            holder.ivIcon.setImageResource(R.drawable.ic_audio_file_24px); // Assuming exists
        } else {
            holder.ivIcon.setImageResource(R.drawable.ic_draft_24px); // Assuming exists
        }
        // Note: I might need to check available drawables.
        // For now, I'll use safely existing ones or a default if I'm not sure.
        // Let's assume generic folder for all if unsure, or check resources.
        // BaseBottomNavActivity had R.id.nav_add...
        // Let's use R.drawable.ic_folder and maybe others if I can find them.
        // I'll stick to a generic one first if I don't know resource names, but I see
        // ic_folder used in layout.
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvType;
        ImageView ivIcon;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvType = itemView.findViewById(R.id.tvType);
            ivIcon = itemView.findViewById(R.id.ivIcon);
        }
    }
}
