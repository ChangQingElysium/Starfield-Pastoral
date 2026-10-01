package com.stardew.craft.port;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/** 1.20.2+ level query helpers missing from 1.20.1. */
public final class PortLevels {
    private PortLevels() {}

    /** 1.21 {@code CollisionGetter#noBlockCollision}: block shapes only, unlike {@code noCollision}. */
    public static boolean noBlockCollision(CollisionGetter level, @Nullable Entity entity, AABB box) {
        for (VoxelShape shape : level.getBlockCollisions(entity, box)) {
            if (!shape.isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
