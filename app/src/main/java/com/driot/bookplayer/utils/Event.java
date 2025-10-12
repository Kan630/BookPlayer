package com.driot.bookplayer.utils;

public final class Event<T> {
    private final T content;
    private boolean handled = false;
    public Event(T content) { this.content = content; }
    /** Returns the content once. Next calls return null. */
    public T getContentIfNotHandled() {
        if (handled) return null;
        handled = true;
        return content;
    }
    /** Returns content without consuming (rarely needed). */
    public T peek() { return content; }
}
