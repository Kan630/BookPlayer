package com.driot.bookplayer.importexport;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.Tonio;

import java.util.List;
import java.util.Set;

/**
 * One row per book eligible for per-book file inclusion in a partial backup (see
 * FullBackupHelper.listBookFileCandidates - already excludes linked/SAF books). Selection state
 * lives directly in the BackupSelection's own Set&lt;Long&gt; passed in, so toggling a row here
 * needs no separate bookkeeping to stay in sync with what the actual backup call will read.
 */
public class BackupBookListAdapter extends RecyclerView.Adapter<BackupBookListAdapter.ViewHolder> {

    public interface OnSelectionChanged {
        void onSelectionChanged();
    }

    private final List<FullBackupHelper.BookFileCandidate> books;
    private final Set<Long> selectedFolderIds;
    private final OnSelectionChanged listener;

    public BackupBookListAdapter(List<FullBackupHelper.BookFileCandidate> books, Set<Long> selectedFolderIds,
            OnSelectionChanged listener) {
        this.books = books;
        this.selectedFolderIds = selectedFolderIds;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_backup_book, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FullBackupHelper.BookFileCandidate book = books.get(position);
        holder.name.setText(book.name);
        holder.size.setText(Tonio.getReadableSize(book.sizeBytes));
        if (book.copyOrLinkIconRes != 0) {
            holder.copyOrLinkIcon.setVisibility(View.VISIBLE);
            holder.copyOrLinkIcon.setImageResource(book.copyOrLinkIconRes);
        } else {
            holder.copyOrLinkIcon.setVisibility(View.INVISIBLE);
        }

        // Avoid the listener firing from setChecked() itself re-triggering during bind/recycle.
        holder.checkbox.setOnCheckedChangeListener(null);
        holder.checkbox.setChecked(selectedFolderIds.contains(book.folderId));
        holder.checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                selectedFolderIds.add(book.folderId);
            } else {
                selectedFolderIds.remove(book.folderId);
            }
            if (listener != null) {
                listener.onSelectionChanged();
            }
        });
    }

    @Override
    public int getItemCount() {
        return books.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final CheckBox checkbox;
        final ImageView copyOrLinkIcon;
        final TextView name;
        final TextView size;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            checkbox = itemView.findViewById(R.id.cb_backup_book);
            copyOrLinkIcon = itemView.findViewById(R.id.iv_backup_book_copy_or_link);
            name = itemView.findViewById(R.id.tv_backup_book_name);
            size = itemView.findViewById(R.id.tv_backup_book_size);
        }
    }
}
