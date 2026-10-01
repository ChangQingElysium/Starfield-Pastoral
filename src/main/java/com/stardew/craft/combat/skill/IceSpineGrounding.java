package com.stardew.craft.combat.skill;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

/** Local collision probes keep ground waves inside caves and stop at walls or gaps. */
public final class IceSpineGrounding {
    private IceSpineGrounding() {}
    @Nullable
    public static Vec3 step(BlockGetter level, Entity actor, Vec3 from, Vec3 to) {
        return step(level, CollisionContext.of(actor), from, to);
    }
    @Nullable
    static Vec3 step(BlockGetter level, CollisionContext collision, Vec3 from, Vec3 to) {
        var wall = level.clip(new com.stardew.craft.port.PortClipContext(from.add(0, 0.65, 0),
                new Vec3(to.x, from.y + 0.65, to.z), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, collision));
        if (wall.getType() != HitResult.Type.MISS) return null;
        var ground = level.clip(new com.stardew.craft.port.PortClipContext(new Vec3(to.x, from.y + 0.6, to.z),
                new Vec3(to.x, from.y - 0.8, to.z), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, collision));
        return ground.getType() == HitResult.Type.BLOCK && ground.getDirection() == Direction.UP
                ? ground.getLocation().add(0, 0.025, 0) : null;
    }
}
