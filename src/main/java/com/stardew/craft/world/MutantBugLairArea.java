package com.stardew.craft.world;

import net.minecraft.core.BlockPos;

/** Shared coordinates for the Mutant Bug Lair embedded in the Stardew Valley map. */
public final class MutantBugLairArea {
    public static final int DOMAIN_MIN_X = -19;
    public static final int DOMAIN_MAX_X = 53;
    public static final int DOMAIN_MIN_Y = -15;
    public static final int DOMAIN_MAX_Y = 6;
    public static final int DOMAIN_MIN_Z = -46;
    public static final int DOMAIN_MAX_Z = 48;

    public static final int SPAWN_MIN_X = -7;
    public static final int SPAWN_MAX_X = 44;
    public static final int SPAWN_FLOOR_Y = -11;
    public static final int SPAWN_Y = -10;
    public static final int SPAWN_MIN_Z = -21;
    public static final int SPAWN_MAX_Z = 27;

    private MutantBugLairArea() {
    }

    public static boolean contains(BlockPos pos) {
        return pos != null && contains(pos.getX(), pos.getY(), pos.getZ());
    }

    public static boolean contains(int x, int y, int z) {
        return x >= DOMAIN_MIN_X && x <= DOMAIN_MAX_X
                && y >= DOMAIN_MIN_Y && y <= DOMAIN_MAX_Y
                && z >= DOMAIN_MIN_Z && z <= DOMAIN_MAX_Z;
    }

    public static boolean biomeCellIntersects(int minX, int minY, int minZ) {
        return minX + 3 >= DOMAIN_MIN_X && minX <= DOMAIN_MAX_X
                && minY + 3 >= DOMAIN_MIN_Y && minY <= DOMAIN_MAX_Y
                && minZ + 3 >= DOMAIN_MIN_Z && minZ <= DOMAIN_MAX_Z;
    }
}
