package com.stardew.craft.combat.skill;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

/** Cosmetic ground probes use collision shapes, so slabs work and air never becomes a floor. */
public final class WeaponGroundContact {
    private WeaponGroundContact() {}

    @Nullable
    public static BlockHitResult find(BlockGetter level, Entity actor, Vec3 feet) {
        return find(level, CollisionContext.of(actor), feet);
    }

    @Nullable
    static BlockHitResult find(BlockGetter level, CollisionContext collision, Vec3 feet) {
        BlockHitResult hit = level.clip(new com.stardew.craft.port.PortClipContext(feet.add(0, 0.3, 0), feet.add(0, -0.65, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, collision));
        return hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.UP ? hit : null;
    }
}
