package com.driot.bookplayer.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;

/**
 * A horizontal bar view that displays storage/memory usage with colored sections.
 * Sections are drawn from left to right:
 * 1. Used storage space (by others) - gray
 * 2. BookPlayer used storage space - blue
 * 3. Expected added memory needed (optional) - yellow/orange
 * 4. Remaining free storage space - transparent
 */
public class StorageBarView extends View {

    private Paint paint;
    private Paint borderPaint;
    private long totalStorage = 0;
    private long usedByOthers = 0;
    private long usedByBookPlayer = 0;
    private long expectedAddedMemory = 0;
    private int colorUsedByOthers;
    private int colorUsedByBookPlayer;
    private int colorExpectedMemory;
    private int borderColor;

    public StorageBarView(Context context) {
        super(context);
        init();
    }

    public StorageBarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public StorageBarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f);

        // Load colors from resources
        colorUsedByOthers = getContext().getColor(R.color.gray_500);
        colorUsedByBookPlayer = getContext().getColor(R.color.pastel_blue_500); // Blue for better visibility
        colorExpectedMemory = getContext().getColor(R.color.yellow_500);
        borderColor = getContext().getColor(R.color.gray_500);
        borderPaint.setColor(borderColor);
    }

    /**
     * Set storage values in bytes
     * @param totalStorage Total storage space
     * @param usedByOthers Storage used by others (not BookPlayer)
     * @param usedByBookPlayer Storage used by BookPlayer
     */
    public void setStorageValues(long totalStorage, long usedByOthers, long usedByBookPlayer) {
        this.totalStorage = totalStorage;
        this.usedByOthers = usedByOthers;
        this.usedByBookPlayer = usedByBookPlayer;
        this.expectedAddedMemory = 0;
        invalidate();
    }

    /**
     * Set storage values including expected added memory (for MassImportActivity)
     * @param totalStorage Total storage space
     * @param usedByOthers Storage used by others (not BookPlayer)
     * @param usedByBookPlayer Storage used by BookPlayer
     * @param expectedAddedMemory Expected memory that will be added
     */
    public void setStorageValues(long totalStorage, long usedByOthers, long usedByBookPlayer, long expectedAddedMemory) {
        this.totalStorage = totalStorage;
        this.usedByOthers = usedByOthers;
        this.usedByBookPlayer = usedByBookPlayer;
        this.expectedAddedMemory = expectedAddedMemory;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float barWidth = getWidth();
        float barHeight = getHeight();
        float currentX = 0;

        // Draw border around the entire bar
        canvas.drawRect(0, 0, barWidth, barHeight, borderPaint);

        if (totalStorage <= 0) {
            // Draw empty bar with just border
            return;
        }

        // Calculate proportions
        float usedByOthersRatio = (float) usedByOthers / totalStorage;
        float usedByBookPlayerRatio = (float) usedByBookPlayer / totalStorage;
        float expectedRatio = (float) expectedAddedMemory / totalStorage;

        // Clamp ratios to ensure they don't exceed 1.0
        float totalUsedRatio = usedByOthersRatio + usedByBookPlayerRatio + expectedRatio;
        if (totalUsedRatio > 1.0f) {
            // Scale down proportionally
            float scale = 1.0f / totalUsedRatio;
            usedByOthersRatio *= scale;
            usedByBookPlayerRatio *= scale;
            expectedRatio *= scale;
        }

        // Draw section 1: Used by others (gray)
        if (usedByOthersRatio > 0) {
            float sectionWidth = barWidth * usedByOthersRatio;
            paint.setColor(colorUsedByOthers);
            canvas.drawRect(currentX, 0, currentX + sectionWidth, barHeight, paint);
            currentX += sectionWidth;
        }

        // Draw section 2: Used by BookPlayer (blue)
        if (usedByBookPlayerRatio > 0) {
            float sectionWidth = barWidth * usedByBookPlayerRatio;
            paint.setColor(colorUsedByBookPlayer);
            canvas.drawRect(currentX, 0, currentX + sectionWidth, barHeight, paint);
            currentX += sectionWidth;
        }

        // Draw section 3: Expected added memory (yellow/orange) - only if > 0
        if (expectedAddedMemory > 0 && expectedRatio > 0) {
            float sectionWidth = barWidth * expectedRatio;
            paint.setColor(colorExpectedMemory);
            canvas.drawRect(currentX, 0, currentX + sectionWidth, barHeight, paint);
            currentX += sectionWidth;
        }

        // Remaining space is transparent (not drawn, shows parent background)
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        // Ensure minimum height
        int minHeight = (int) (getContext().getResources().getDisplayMetrics().density * 24); // 24dp
        if (height < minHeight) {
            height = minHeight;
        }

        setMeasuredDimension(width, height);
    }
}
