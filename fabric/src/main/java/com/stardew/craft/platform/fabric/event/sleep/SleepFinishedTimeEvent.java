package com.stardew.craft.platform.fabric.event.sleep;

import com.stardew.craft.platform.fabric.event.LevelEvent;
import net.minecraft.server.level.ServerLevel;

public final class SleepFinishedTimeEvent extends LevelEvent {
    private long time;
    private final long minimum;
    public SleepFinishedTimeEvent(ServerLevel level, long time, long minimum) { super(level); this.time = time; this.minimum = minimum; }
    public long getNewTime() { return time; }
    public boolean setTimeAddition(long time) {
        if (time < minimum) return false;
        this.time = time; return true;
    }
}
