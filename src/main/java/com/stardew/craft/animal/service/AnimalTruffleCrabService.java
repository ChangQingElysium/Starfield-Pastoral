package com.stardew.craft.animal.service;

import com.stardew.craft.event.MineMonsterSpawnHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** Projects Stardew's Truffle Crab through the existing Rock Crab entity representation. */
public final class AnimalTruffleCrabService {
    private static final int SOURCE_SEARCH_RADIUS = 50;
    private AnimalTruffleCrabService() {
    }

    public static boolean spawnNear(ServerLevel level, BlockPos anchor) {
        BlockPos spawnPos = findOpenPosition(level, anchor);
        if (spawnPos == null) {
            return false;
        }

        Mob spawned = MineMonsterSpawnHandler.spawnConfiguredMonster(
                level,
                "truffle_crab",
                Vec3.atBottomCenterOf(spawnPos),
                0.0F,
                1,
                mob -> {
                    mob.setPersistenceRequired();
                });
        return spawned != null;
    }

    @Nullable
    private static BlockPos findOpenPosition(
            ServerLevel level,
            BlockPos anchor
    ) {
        for (int radius = 1; radius <= SOURCE_SEARCH_RADIUS; radius++) {
            for (int x = -radius; x <= radius; x++) {
                BlockPos north = findOpenAt(level, anchor.offset(x, 0, -radius));
                if (north != null) {
                    return north;
                }
                BlockPos south = findOpenAt(level, anchor.offset(x, 0, radius));
                if (south != null) {
                    return south;
                }
            }
            for (int z = -radius + 1; z < radius; z++) {
                BlockPos west = findOpenAt(level, anchor.offset(-radius, 0, z));
                if (west != null) {
                    return west;
                }
                BlockPos east = findOpenAt(level, anchor.offset(radius, 0, z));
                if (east != null) {
                    return east;
                }
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos findOpenAt(ServerLevel level, BlockPos horizontal) {
        for (int yOffset : new int[]{0, 1, -1}) {
            BlockPos feet = horizontal.offset(0, yOffset, 0);
            if (!level.hasChunkAt(feet)
                    || !level.getBlockState(feet)
                            .getCollisionShape(level, feet).isEmpty()
                    || !level.getBlockState(feet.above())
                            .getCollisionShape(level, feet.above()).isEmpty()) {
                continue;
            }
            BlockPos support = feet.below();
            if (!level.getBlockState(support)
                    .isFaceSturdy(level, support, Direction.UP)) {
                continue;
            }
            AABB body = new AABB(
                    feet.getX() + 0.1D,
                    feet.getY(),
                    feet.getZ() + 0.1D,
                    feet.getX() + 0.9D,
                    feet.getY() + 1.8D,
                    feet.getZ() + 0.9D);
            if (level.getEntitiesOfClass(
                    Entity.class,
                    body,
                    Entity::isAlive).isEmpty()) {
                return feet.immutable();
            }
        }
        return null;
    }
}
