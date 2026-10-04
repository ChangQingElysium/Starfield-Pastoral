package com.stardew.craft.gingerisland;

import net.minecraft.world.phys.AABB;

/** One simplified resting-body box from the approved model, independent of entity/registry setup. */
public final class GiantTurtleBounds {
    public static final float WIDTH = 3F;
    public static final float HEIGHT = 27F / 16;
    public static final float DEPTH = 3.5F;

    private GiantTurtleBounds() { }

    /** Generic z extents are -27..29 pixels; yaw zero faces Minecraft south. */
    public static AABB at(double x, double y, double z, float yaw) {
        double angle = Math.toRadians(yaw), sin = Math.sin(angle), cos = Math.cos(angle);
        double halfX = (Math.abs(cos) * WIDTH + Math.abs(sin) * DEPTH) / 2;
        double halfZ = (Math.abs(sin) * WIDTH + Math.abs(cos) * DEPTH) / 2;
        double centerX = x + sin / 16, centerZ = z - cos / 16;
        return new AABB(centerX - halfX, y, centerZ - halfZ,
                centerX + halfX, y + HEIGHT, centerZ + halfZ);
    }
}
