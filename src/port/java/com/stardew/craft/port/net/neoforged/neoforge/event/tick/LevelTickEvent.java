package com.stardew.craft.port.net.neoforged.neoforge.event.tick;

import java.util.function.BooleanSupplier;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.Event;

/**
 * PORT(1.20.1): NeoForge 21.1 level tick event (both logical sides), bridged from Forge
 * {@code TickEvent.LevelTickEvent} ({@code START} -> {@link Pre}, {@code END} -> {@link Post}).
 */
public abstract class LevelTickEvent extends Event {
    private final BooleanSupplier haveTime;
    private final Level level;

    protected LevelTickEvent(BooleanSupplier haveTime, Level level) {
        this.haveTime = haveTime;
        this.level = level;
    }

    /** {@return true if the server has enough time to perform any additional tasks during this tick} */
    public boolean hasTime() {
        return this.haveTime.getAsBoolean();
    }

    public Level getLevel() {
        return this.level;
    }

    public static class Pre extends LevelTickEvent {
        public Pre() {
            this(() -> false, null);
        }

        public Pre(BooleanSupplier haveTime, Level level) {
            super(haveTime, level);
        }
    }

    public static class Post extends LevelTickEvent {
        public Post() {
            this(() -> false, null);
        }

        public Post(BooleanSupplier haveTime, Level level) {
            super(haveTime, level);
        }
    }
}
