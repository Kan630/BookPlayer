package com.driot.bookplayer.helpers;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.global.Option;

public class GridScaleGestureHelper {

    private final RecyclerView recyclerView;
    private final GridLayoutManager gridLayoutManager;
    private final int minSpan;
    private final int maxSpan;
    private final String preferenceKey;

    private final ScaleGestureDetector scaleDetector;
    private final int[] currentSpan;

    /**
     * @param recyclerView   The RecyclerView using GridLayoutManager
     * @param minSpan        Minimum number of columns
     * @param maxSpan        Maximum number of columns
     * @param defaultSpan    Default span count (usually from resources)
     * @param preferenceKey  Key used in Option to save/load span (e.g. "RADIO_GRID_LAYOUT_SPAN")
     */
    public GridScaleGestureHelper(@NonNull RecyclerView recyclerView,
                                  int minSpan,
                                  int maxSpan,
                                  int defaultSpan,
                                  @NonNull String preferenceKey) {

        this.recyclerView = recyclerView;
        this.gridLayoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
        this.minSpan = minSpan;
        this.maxSpan = maxSpan;
        this.preferenceKey = preferenceKey;

        // Load saved span or use default + clamp
        int saved = Option.getGridSpan(preferenceKey, -1);
        int initialSpan = Math.max(minSpan, Math.min(maxSpan, saved != -1 ? saved : defaultSpan));

        this.currentSpan = new int[]{initialSpan};

        // Apply initial span
        applySpan(initialSpan);

        // Create scale detector
        this.scaleDetector = new ScaleGestureDetector(recyclerView.getContext(),
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {

                    private float accumulatedScale = 1f;

                    @Override
                    public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
                        accumulatedScale = 1f;
                        return true;
                    }

                    @Override
                    public boolean onScale(@NonNull ScaleGestureDetector detector) {
                        accumulatedScale *= detector.getScaleFactor();

                        if (accumulatedScale > 1.15f) { // Pinch out → bigger items (fewer columns)
                            int newSpan = currentSpan[0] - 1;
                            newSpan = Math.max(minSpan, Math.min(maxSpan, newSpan));

                            if (newSpan != currentSpan[0]) {
                                currentSpan[0] = newSpan;
                                applySpan(newSpan);
                                accumulatedScale = 1f;
                            }
                        }
                        else if (accumulatedScale < 0.85f) { // Pinch in → smaller items (more columns)
                            int newSpan = currentSpan[0] + 1;
                            newSpan = Math.max(minSpan, Math.min(maxSpan, newSpan));

                            if (newSpan != currentSpan[0]) {
                                currentSpan[0] = newSpan;
                                applySpan(newSpan);
                                accumulatedScale = 1f;
                            }
                        }
                        return true;
                    }

                    @Override
                    public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
                        // Save preference
                        Option.setGridSpan(preferenceKey, currentSpan[0]);
                    }
                });
    }

    private void applySpan(int span) {
        gridLayoutManager.setSpanCount(span);
        gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return position == 0 ? span : 1; // Header takes full row
            }
        });
        recyclerView.requestLayout();
    }

    /**
     * Call this from your RecyclerView's OnItemTouchListener
     */
    public boolean onTouchEvent(MotionEvent event) {
        return scaleDetector.onTouchEvent(event);
    }

    /**
     * Returns the current span count
     */
    public int getCurrentSpan() {
        return currentSpan[0];
    }
}