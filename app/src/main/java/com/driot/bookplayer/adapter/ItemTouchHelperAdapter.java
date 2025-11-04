package com.driot.bookplayer.adapter;

public interface ItemTouchHelperAdapter {
    boolean onItemMove(int fromPos, int toPos);

    void onItemDropped();

    void onDroppedInTrash(int adapterPosition);

    void onDroppedInTrashUuid(@androidx.annotation.NonNull String uuid);

    @androidx.annotation.Nullable
    String getUuidForAdapterPosition(int adapterPosition);
}
