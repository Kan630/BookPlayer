package com.driot.bookplayer.services.m4b;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Turns the chapters found in a file into the segments to cut, or refuses (then the book is imported unsplit, which
 * always plays). Rules, whatever the chapter source (mp4parser, chapter track, chpl):
 * <ul>
 * <li>at least 2 chapters, starts strictly increasing, all inside the audio;</li>
 * <li>the chapter timeline may not run past the audio end (wrong timescale) nor cover less than half of it;</li>
 * <li>segments cover [0, audio end] with no gap: audio before the first chapter goes into the first one, audio after
 * the last chapter's declared end into the last one - nothing is dropped.</li>
 * </ul>
 * Pure Java (JVM-testable).
 */
public final class ChapterPlan {

    public static final class Segment {
        public final String title;
        public final long startMs;
        public final long endMs;

        Segment(String title, long startMs, long endMs) {
            this.title = title;
            this.startMs = startMs;
            this.endMs = endMs;
        }

        @Override
        public String toString() {
            return "[" + startMs + "-" + endMs + "] " + title;
        }
    }

    /** Segments to cut, or empty with {@link #rejectReason} set. */
    public final List<Segment> segments;
    public final String rejectReason;

    private ChapterPlan(List<Segment> segments, String rejectReason) {
        this.segments = segments;
        this.rejectReason = rejectReason;
    }

    public boolean ok() {
        return rejectReason == null;
    }

    static ChapterPlan reject(String why) {
        return new ChapterPlan(Collections.emptyList(), why);
    }

    /** Slack allowed between the chapter timeline end and the audio end (encoder padding, rounding). */
    static long tolerance(long audioMs) {
        return Math.max(5_000, audioMs / 50); // 2 %
    }

    public static ChapterPlan of(List<Mp4ChapterReader.Chapter> chapters, long audioDurationMs) {
        if (chapters == null || chapters.size() < 2)
            return reject("fewer than 2 chapters");
        if (audioDurationMs <= 0)
            return reject("unknown audio duration");

        long prev = -1;
        for (int i = 0; i < chapters.size(); i++) {
            long s = chapters.get(i).startMs;
            if (s < 0)
                return reject("chapter " + (i + 1) + " starts before 0");
            if (i > 0 && s <= prev)
                return reject("chapter " + (i + 1) + " does not start after chapter " + i + " (" + s + " <= " + prev + ")");
            if (s >= audioDurationMs)
                return reject("chapter " + (i + 1) + " starts at " + s + " ms, after the audio end " + audioDurationMs);
            prev = s;
        }
        Mp4ChapterReader.Chapter last = chapters.get(chapters.size() - 1);
        long timelineEnd = last.startMs + Math.max(0, last.durationMs);
        if (timelineEnd > audioDurationMs + tolerance(audioDurationMs))
            return reject("chapter timeline ends at " + timelineEnd + " ms, past the audio end " + audioDurationMs);
        if (timelineEnd < audioDurationMs / 2)
            return reject("chapter timeline ends at " + timelineEnd + " ms, less than half of the audio " + audioDurationMs);

        List<Segment> out = new ArrayList<>(chapters.size());
        for (int i = 0; i < chapters.size(); i++) {
            long start = (i == 0) ? 0 : chapters.get(i).startMs;
            long end = (i + 1 < chapters.size()) ? chapters.get(i + 1).startMs : audioDurationMs;
            String title = chapters.get(i).title;
            out.add(new Segment(title == null ? "" : title, start, end));
        }
        return new ChapterPlan(out, null);
    }
}
