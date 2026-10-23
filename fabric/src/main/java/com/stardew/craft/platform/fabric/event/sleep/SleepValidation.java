package com.stardew.craft.platform.fabric.event.sleep;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/** Validation is scoped to one sleeping attempt; nested listeners cannot borrow another frame. */
public final class SleepValidation {
    private static final ThreadLocal<Deque<Scope>> FRAMES = new ThreadLocal<>();
    private SleepValidation() {}
    public static Scope open(Object player) {
        var stack = FRAMES.get(); if (stack == null) { stack = new ArrayDeque<>(); FRAMES.set(stack); }
        var frame = new Scope(Objects.requireNonNull(player)); stack.push(frame); return frame;
    }
    public static boolean probing(Object player) {
        var stack = FRAMES.get(); var top = stack == null ? null : stack.peek();
        return top != null && top.player == player && top.probing;
    }
    public static final class Scope implements AutoCloseable {
        private final Object player;
        private final Thread thread = Thread.currentThread();
        private boolean probing = true, closed;
        private Scope(Object player) { this.player = player; }
        public void finishValidation() { checkTop(); probing = false; }
        private void checkTop() {
            if (closed || Thread.currentThread() != thread || FRAMES.get() == null || FRAMES.get().peek() != this)
                throw new IllegalStateException("Sleep validation scopes must close on their thread in stack order");
        }
        @Override public void close() {
            checkTop(); var stack = FRAMES.get(); stack.pop(); closed = true;
            if (stack.isEmpty()) FRAMES.remove();
        }
    }
}
