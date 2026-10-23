package com.stardew.craft.platform.fabric.event;

import net.minecraft.world.level.LevelAccessor;

/** World lifecycle payload. The common bridge publishes logical-server worlds only. */
public abstract class LevelEvent extends Event {
    private final LevelAccessor level;

    protected LevelEvent(LevelAccessor level) {
        this(level, false);
    }

    protected LevelEvent(LevelAccessor level, boolean cancelable) {
        super(cancelable);
        this.level = level;
    }

    public final LevelAccessor getLevel() {
        return level;
    }

    public static final class Load extends LevelEvent {
        public Load(LevelAccessor level) { super(level); }
    }

    public static final class Unload extends LevelEvent {
        public Unload(LevelAccessor level) { super(level); }
    }
}
