package com.driot.bookplayer.adapter;

import android.graphics.Canvas;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

public class FavoritesTouchHelperCallback extends ItemTouchHelper.Callback {

    private final RecyclerView rv;
    private final View dropZone;
    private final ItemTouchHelperAdapter adapter;

    private boolean overTrash = false;
    private @Nullable String draggingUuid = null;

    public FavoritesTouchHelperCallback(RecyclerView rv, View dropZone, ItemTouchHelperAdapter adapter) {
        this.rv = rv;
        this.dropZone = dropZone;
        this.adapter = adapter;
    }

    @Override public boolean isLongPressDragEnabled() { return true; }
    @Override public boolean isItemViewSwipeEnabled() { return false; }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
    }

    @Override public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        int pos = viewHolder.getBindingAdapterPosition();
        if (pos == 0) return 0; // no drag for header
        int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
        return makeMovementFlags(dragFlags, 0);
    }

    @Override public boolean onMove(@NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder from,
                                    @NonNull RecyclerView.ViewHolder to) {
        // prevent dropping onto header
        if (to.getBindingAdapterPosition() == 0) return false;
        return adapter.onItemMove(from.getBindingAdapterPosition(), to.getBindingAdapterPosition());
    }


    @Override public void onSelectedChanged(@Nullable RecyclerView.ViewHolder vh, int actionState) {
        super.onSelectedChanged(vh, actionState);
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && vh != null) {
            dropZone.setVisibility(View.VISIBLE);
            dropZone.setActivated(false);
            overTrash = false;

            int pos = vh.getBindingAdapterPosition();
            draggingUuid = adapter.getUuidForAdapterPosition(pos); // capture UUID NOW
        } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
            dropZone.setVisibility(View.GONE);
            dropZone.setActivated(false);
            adapter.onItemDropped();
            draggingUuid = null;
        }
    }

    @Override public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder vh) {
        super.clearView(recyclerView, vh);
        if (overTrash) {
            // Use the UUID captured at drag start (position can be -1 now)
            if (draggingUuid != null) {
                adapter.onDroppedInTrashUuid(draggingUuid);
            } else {
                // fallback to position if we somehow missed the uuid
                adapter.onDroppedInTrash(vh.getBindingAdapterPosition());
            }
        }
        overTrash = false;
        draggingUuid = null;
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder,
                            float dX, float dY, int actionState, boolean isCurrentlyActive) {
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);

        if (actionState != ItemTouchHelper.ACTION_STATE_DRAG || !isCurrentlyActive) return;

        // Compute the dragged item’s center Y on screen
        View item = viewHolder.itemView;
        int[] itemLoc = new int[2];
        int[] zoneLoc = new int[2];
        item.getLocationOnScreen(itemLoc);
        dropZone.getLocationOnScreen(zoneLoc);

        int itemCenterY = itemLoc[1] + item.getHeight() / 2;
        int zoneTop = zoneLoc[1];

        boolean nowOver = itemCenterY >= zoneTop;
        if (nowOver != overTrash) {
            overTrash = nowOver;
            dropZone.setActivated(overTrash); // switch bg color
            dropZone.animate().scaleX(overTrash ? 1.02f : 1f).scaleY(overTrash ? 1.02f : 1f).setDuration(120).start();
        }
    }
}
