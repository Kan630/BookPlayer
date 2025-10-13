package com.driot.bookplayer.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.FrameLayout;

public class ClickInterceptFrameLayout extends FrameLayout {
    private final GestureDetector detector;

    public interface Callbacks {
        void onSingleTap();
        void onDoubleTap();
        void onLongPress();
    }
    private Callbacks callbacks;

    public ClickInterceptFrameLayout(Context c, AttributeSet a) {
        super(c, a);
        setClickable(true);
        setLongClickable(true);
        detector = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                if (callbacks != null) callbacks.onSingleTap();
                return true;
            }
            @Override public boolean onDoubleTap(MotionEvent e) {
                if (callbacks != null) callbacks.onDoubleTap();
                return true;
            }
            @Override public void onLongPress(MotionEvent e) {
                if (callbacks != null) callbacks.onLongPress();
            }
            @Override public boolean onDown(MotionEvent e) { return true; }
        });
    }

    public void setCallbacks(Callbacks cb) { this.callbacks = cb; }

    @Override public boolean onInterceptTouchEvent(MotionEvent ev) {
        // Intercept so children don't consume; detector still receives events in onTouchEvent
        return true;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        return detector.onTouchEvent(event) || super.onTouchEvent(event);
    }
}
