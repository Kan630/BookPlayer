package com.driot.bookplayer.tts;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.adapter.TtsReaderAdapter;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.helpers.ColorHelper;
import com.driot.bookplayer.player.PlaybackUiState;
import com.driot.bookplayer.player.PlaybackViewModel;
import com.driot.bookplayer.views.TtsTextView;

import java.util.ArrayList;
import java.util.List;
import java.text.BreakIterator;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public class TtsReaderController {

    private final Context context;
    private final RecyclerView recyclerView;
    private final TtsReaderAdapter adapter;
    private final LinearLayoutManager layoutManager;

    private String fullText = "";
    private final List<String> chunks = new ArrayList<>();
    private final List<Integer> chunkOffsets = new ArrayList<>();

    private int lastHighlightedChunkIdx = -1;
    private int lastHighlightedLineIdx = -1;
    private boolean isFirstHighlight = true;

    private boolean suppressAutoScroll = false;
    private float lastDownY;
    private final int touchSlop;
    private final ScaleGestureDetector scaleDetector;
    private float currentTextSize;

    private long lastTtsTrackId = -1;
    private boolean lastTtsPlaying = false;
    private long lastTtsPositionMs = -1;
    private static final long SEEK_DETECTION_THRESHOLD_MS = 10000;

    public TtsReaderController(@NonNull Context context, @NonNull RecyclerView recyclerView) {
        this.context = context;
        this.recyclerView = recyclerView;
        this.layoutManager = new LinearLayoutManager(context);
        this.recyclerView.setLayoutManager(this.layoutManager);

        int colorPrimary = ColorHelper.getColorPrimaryForTtsCursor(context);
        int colorOnPrimary = ColorHelper.getColorOnPrimaryForTtsCursor(context);
        this.adapter = new TtsReaderAdapter(colorPrimary, colorOnPrimary);
        this.recyclerView.setAdapter(this.adapter);

        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        this.recyclerView.setItemAnimator(null);
        this.currentTextSize = (float) Option.getTtsFullscreenTextSize();
        this.scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                float newSize = currentTextSize * factor;
                // Clamp to reasonable limits (10 to 60 sp)
                newSize = Math.max(10f, Math.min(60f, newSize));
                if (Math.abs(newSize - currentTextSize) > 0.1f) {
                    currentTextSize = newSize;
                    adapter.setTextSize(currentTextSize);
                    Option.setTtsFullscreenTextSize((int) currentTextSize);
                }
                return true;
            }
        });
        attachTouchLogic();
    }

    public void bind(@NonNull LifecycleOwner owner, @NonNull PlaybackViewModel vm) {
        vm.getTtsText().observe(owner, text -> {
            if (text != null && !text.equals(fullText)) {
                updateText(text);
                isFirstHighlight = true;
            }
        });

        vm.getTtsRange().observe(owner, range -> {
            if (range != null) {
                highlightRange(range.first, range.second);
            }
        });

        vm.getState().observe(owner, s -> {
            onPlaybackStateChanged(s);
        });

        adapter.setTextSize((float) Option.getTtsFullscreenTextSize());
        adapter.setOnWordClickListener(globalOffset -> {
            suppressAutoScroll = false;
            vm.setTtsStartOffsetChars(globalOffset);
        });
    }

    private void updateText(String text) {
        this.fullText = text;
        this.chunks.clear();
        this.chunkOffsets.clear();

        if (text == null || text.isEmpty()) {
            adapter.setData(chunks, chunkOffsets);
            return;
        }

        // Use same logic as TtsHelper to split into chunks
        BreakIterator it = BreakIterator.getSentenceInstance();
        it.setText(text);
        int sentStart = it.first();
        int sentEnd;
        int maxLen = Option.getTtsChunkSize();
        
        StringBuilder buf = new StringBuilder();
        int currentChunkStart = sentStart;

        while ((sentEnd = it.next()) != BreakIterator.DONE) {
            String s = text.substring(sentStart, sentEnd);
            if (buf.length() + s.length() > maxLen && buf.length() > 0) {
                chunks.add(buf.toString());
                chunkOffsets.add(currentChunkStart);
                buf.setLength(0);
                currentChunkStart = sentStart;
            }
            buf.append(s);
            sentStart = sentEnd;
        }
        if (buf.length() > 0) {
            chunks.add(buf.toString());
            chunkOffsets.add(currentChunkStart);
        }

        adapter.setData(chunks, chunkOffsets);
    }

    private void highlightRange(int start, int end) {
        int chunkIdx = findChunkIndexForOffset(start);
        if (chunkIdx != -1) {
            int relStart = start - chunkOffsets.get(chunkIdx);
            int relEnd = end - chunkOffsets.get(chunkIdx);
            adapter.updateHighlight(chunkIdx, relStart, relEnd);

            if (!suppressAutoScroll) {
                applyAutoScroll(chunkIdx, relStart);
            }
        }
    }

    private void applyAutoScroll(int chunkIdx, int relStart) {
        if (isFirstHighlight) {
            // Immediate jump on first load or track change
            int offset = recyclerView.getHeight() / 3;
            layoutManager.scrollToPositionWithOffset(chunkIdx, offset);
            isFirstHighlight = false;
            lastHighlightedChunkIdx = chunkIdx;
            return;
        }

        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(chunkIdx);
        if (holder != null && holder.itemView instanceof TtsTextView) {
            TtsTextView tv = (TtsTextView) holder.itemView;
            Layout layout = tv.getLayout();
            if (layout != null) {
                int line = layout.getLineForOffset(relStart);
                if (line != lastHighlightedLineIdx || chunkIdx != lastHighlightedChunkIdx) {
                    int lineTop = layout.getLineTop(line);
                    int viewTop = holder.itemView.getTop();
                    int currentY = viewTop + lineTop;
                    int targetY = recyclerView.getHeight() / 3;
                    int diff = currentY - targetY;

                    // Only scroll if the difference is more than a tiny amount to prevent jitter
                    if (Math.abs(diff) > 2) {
                        recyclerView.stopScroll();
                        recyclerView.smoothScrollBy(0, diff);
                    }
                    
                    lastHighlightedLineIdx = line;
                    lastHighlightedChunkIdx = chunkIdx;
                }
            }
        } else {
            // View not bound, fall back to simple scroll
            recyclerView.smoothScrollToPosition(chunkIdx);
        }
    }

    private int findChunkIndexForOffset(int offset) {
        for (int i = 0; i < chunkOffsets.size(); i++) {
            int start = chunkOffsets.get(i);
            int end = start + chunks.get(i).length();
            if (offset >= start && offset < end) {
                return i;
            }
        }
        return -1;
    }


    private void onPlaybackStateChanged(PlaybackUiState s) {
        if (s == null) return;
        boolean isTts = "tts".equals(s.playMode);
        
        if (isTts && lastTtsPositionMs >= 0 && s.positionMs > 0) {
            long delta = Math.abs(s.positionMs - lastTtsPositionMs);
            if (delta > SEEK_DETECTION_THRESHOLD_MS) {
                suppressAutoScroll = false;
            }
        }

        if (s.trackId != lastTtsTrackId) {
            lastTtsTrackId = s.trackId;
            suppressAutoScroll = false;
            isFirstHighlight = true;
        }

        boolean isSpeak = Intents.PHASE_SPEAKING.equals(s.loadPhase);
        if (isSpeak != lastTtsPlaying) {
            lastTtsPlaying = isSpeak;
            suppressAutoScroll = false;
        }
        lastTtsPositionMs = s.positionMs;
    }

    private void attachTouchLogic() {
        recyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                scaleDetector.onTouchEvent(e);
                if (e.getPointerCount() > 1) {
                    // It's a multi-touch gesture (like pinch), suppress autoscroll
                    suppressAutoScroll = true;
                    return false; 
                }
                
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    lastDownY = e.getY();
                } else if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    if (Math.abs(e.getY() - lastDownY) > touchSlop) {
                        suppressAutoScroll = true;
                    }
                }
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                scaleDetector.onTouchEvent(e);
            }
        });
    }
}
