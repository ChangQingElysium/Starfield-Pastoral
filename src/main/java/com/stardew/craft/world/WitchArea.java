package com.stardew.craft.world;

import net.minecraft.core.BlockPos;

/** Shared coordinates for the Witch's Swamp and Witch's Hut embedded in the valley map. */
public final class WitchArea {
    public static final int SWAMP_MIN_X = 39;
    public static final int SWAMP_MAX_X = 66;
    public static final int SWAMP_MIN_Y = 46;
    public static final int SWAMP_MAX_Y = 59;
    public static final int SWAMP_MIN_Z = -257;
    public static final int SWAMP_MAX_Z = -215;

    public static final int HUT_MIN_X = 44;
    public static final int HUT_MAX_X = 58;
    public static final int HUT_MIN_Y = 39;
    public static final int HUT_MAX_Y = 43;
    public static final int HUT_MIN_Z = -256;
    public static final int HUT_MAX_Z = -242;

    private WitchArea() {
    }

    public static boolean isInSwamp(BlockPos pos) {
        return pos != null && isInSwamp(pos.getX(), pos.getY(), pos.getZ());
    }

    public static boolean isInSwamp(int x, int y, int z) {
        return x >= SWAMP_MIN_X && x <= SWAMP_MAX_X
                && y >= SWAMP_MIN_Y && y <= SWAMP_MAX_Y
                && z >= SWAMP_MIN_Z && z <= SWAMP_MAX_Z;
    }

    public static boolean isInHut(BlockPos pos) {
        return pos != null && isInHut(pos.getX(), pos.getY(), pos.getZ());
    }

    public static boolean isInHut(int x, int y, int z) {
        return x >= HUT_MIN_X && x <= HUT_MAX_X
                && y >= HUT_MIN_Y && y <= HUT_MAX_Y
                && z >= HUT_MIN_Z && z <= HUT_MAX_Z;
    }

    public static boolean swampBiomeCellIntersects(int minX, int minY, int minZ) {
        return minX + 3 >= SWAMP_MIN_X && minX <= SWAMP_MAX_X
                && minY + 3 >= SWAMP_MIN_Y && minY <= SWAMP_MAX_Y
                && minZ + 3 >= SWAMP_MIN_Z && minZ <= SWAMP_MAX_Z;
    }
}
