package com.stardew.craft.platform.fabric.event.entity.living.damage;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/** Owned by one entity, never a global identity map. Nested hurt calls receive independent ledgers. */
public final class DamageFrames {
    public interface Access { DamageFrames stardewcraft$damageFrames(); }
    public static final class Frame {
        public DamageContainer container;
        public boolean prePosted;
        public float absorbed;
    }
    private final Deque<Frame> frames = new ArrayDeque<>();

    public <T> T within(Supplier<T> body) {
        var frame = new Frame();
        frames.push(frame);
        try { return body.get(); }
        finally {
            if (frames.peek() != frame) throw new IllegalStateException("Damage frame stack is unbalanced");
            frames.pop();
        }
    }

    public Frame current() {
        Frame frame = frames.peek();
        if (frame == null) throw new IllegalStateException("No active native hurt sequence");
        return frame;
    }

    public Frame currentOrNull() { return frames.peek(); }
}
