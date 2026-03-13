package com.driot.bookplayer.adapter;

import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.Layout;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.helpers.ColorHelper;
import com.driot.bookplayer.tts.TtsHelper;
import com.driot.bookplayer.views.TtsTextView;

import java.util.ArrayList;
import java.util.List;

public class TtsReaderAdapter extends RecyclerView.Adapter<TtsReaderAdapter.ViewHolder> {

    public interface OnWordClickListener {
        void onWordClick(int globalOffset);
    }

    private final List<Chunk> chunks = new ArrayList<>();
    private int activeChunkIndex = -1;
    private int activeWordStart = -1;
    private int activeWordEnd = -1;

    private float textSizeSp = 18f;
    private OnWordClickListener listener;

    private final BackgroundColorSpan bgSpan;
    private final ForegroundColorSpan fgSpan;

    public TtsReaderAdapter(int cursorBgColor, int cursorFgColor) {
        this.bgSpan = new BackgroundColorSpan(cursorBgColor);
        this.fgSpan = new ForegroundColorSpan(cursorFgColor);
    }

    public void setOnWordClickListener(OnWordClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<String> rawChunks, List<Integer> globalOffsets) {
        this.chunks.clear();
        for (int i = 0; i < rawChunks.size(); i++) {
            this.chunks.add(new Chunk(rawChunks.get(i), globalOffsets.get(i)));
        }
        notifyDataSetChanged();
    }

    public void setTextSize(float sp) {
        this.textSizeSp = sp;
        notifyDataSetChanged();
    }

    public void updateHighlight(int chunkIndex, int wordStart, int wordEnd) {
        int oldActive = activeChunkIndex;
        activeChunkIndex = chunkIndex;
        activeWordStart = wordStart;
        activeWordEnd = wordEnd;

        if (oldActive != -1) notifyItemChanged(oldActive);
        if (activeChunkIndex != -1 && activeChunkIndex != oldActive) {
            notifyItemChanged(activeChunkIndex);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tts_chunk, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Chunk chunk = chunks.get(position);
        holder.tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp);

        SpannableStringBuilder sb = new SpannableStringBuilder(chunk.text);
        if (position == activeChunkIndex && activeWordStart >= 0 && activeWordEnd > activeWordStart) {
            int s = Math.max(0, Math.min(activeWordStart, chunk.text.length()));
            int e = Math.max(s, Math.min(activeWordEnd, chunk.text.length()));
            sb.setSpan(bgSpan, s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(fgSpan, s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        holder.tv.setText(sb);

        final GestureDetector tapDetector = new GestureDetector(holder.tv.getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(@NonNull MotionEvent e) {
                        return true;
                    }

                    @Override
                    public boolean onSingleTapUp(@NonNull MotionEvent e) {
                        Layout layout = holder.tv.getLayout();
                        if (layout == null) return false;

                        int x = (int) e.getX() - holder.tv.getTotalPaddingLeft() + holder.tv.getScrollX();
                        int y = (int) e.getY() - holder.tv.getTotalPaddingTop() + holder.tv.getScrollY();
                        int line = layout.getLineForVertical(y);
                        int off = layout.getOffsetForHorizontal(line, x);
                        off = Math.max(0, Math.min(off, chunk.text.length()));

                        int[] word = TtsHelper.findWordBounds(chunk.text, off);
                        if (listener != null) {
                            listener.onWordClick(chunk.globalStartOffset + word[0]);
                        }
                        return true;
                    }
                });

        holder.tv.setOnTouchListener((v, ev) -> {
            boolean handled = tapDetector.onTouchEvent(ev);
            if (ev.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
            }
            // If it's a tap, we handled it. Otherwise, we let it through for scrolling.
            // But actually we want to return false if it's not a tap to allow RecyclerView to scroll.
            return handled;
        });
    }

    @Override
    public int getItemCount() {
        return chunks.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final TtsTextView tv;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tv = (TtsTextView) itemView;
        }
    }

    private static class Chunk {
        final String text;
        final int globalStartOffset;
        Chunk(String text, int globalStartOffset) {
            this.text = text;
            this.globalStartOffset = globalStartOffset;
        }
    }

    public int getGlobalOffsetAt(int position) {
        if (position >= 0 && position < chunks.size()) {
            return chunks.get(position).globalStartOffset;
        }
        return 0;
    }
}
