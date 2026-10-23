package com.stardew.craft.platform.fabric.event;

import java.util.function.BooleanSupplier;
import net.minecraft.world.level.Level;

/** Server levels are bridged here; client level publication requires its client bridge. */
public abstract class LevelTickEvent extends Event {
    private final BooleanSupplier haveTime;
    private final Level level;

    protected LevelTickEvent(BooleanSupplier haveTime, Level level) {
        this.haveTime = haveTime;
        this.level = level;
    }

    public final boolean hasTime() { return haveTime.getAsBoolean(); }
    public final Level getLevel() { return level; }

    public static final class Pre extends LevelTickEvent {
        public Pre(BooleanSupplier haveTime, Level level) { super(haveTime, level); }
    }

    public static final class Post extends LevelTickEvent {
        public Post(BooleanSupplier haveTime, Level level) { super(haveTime, level); }
    }
}
