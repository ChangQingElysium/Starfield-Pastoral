package com.stardew.craft.block.mine;

/** Tick-based hinge travel, interpolated for rendering; reversals keep the current pose. */
public final class MineChestLidMotion {
    public static final float OPEN_ANGLE = 90;
    public static final int TRAVEL_TICKS = 10;
    private float previous;
    private float progress;

    public void snap(boolean open) { previous = progress = open ? 1 : 0; }

    public void tick(boolean open) {
        previous = progress;
        progress = com.stardew.craft.port.PortJava.clamp(progress + (open ? 1f : -1f) / TRAVEL_TICKS, 0f, 1f);
    }

    public float angle(float partialTick) {
        float value = previous + (progress - previous) * com.stardew.craft.port.PortJava.clamp(partialTick, 0f, 1f);
        float remaining = 1 - value;
        return OPEN_ANGLE * (1 - remaining * remaining * remaining);
    }
}
