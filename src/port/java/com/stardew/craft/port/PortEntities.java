package com.stardew.craft.port;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 1.20.2+ entity attachment API pieces used by the mod, expressed with 1.20.1 entity hooks. */
public final class PortEntities {
    /** 1.21 {@code Player.DEFAULT_VEHICLE_ATTACHMENT}. */
    public static final Vec3 PLAYER_VEHICLE_ATTACHMENT = new Vec3(0.0, 0.6, 0.0);

    private PortEntities() {}

    /**
     * 1.21 {@code Entity#getVehicleAttachmentPoint(vehicle)}: players use {@link #PLAYER_VEHICLE_ATTACHMENT}; every
     * other entity falls back to its feet (the 1.21 {@code EntityAttachment.VEHICLE} default). Vanilla mobs that
     * declare their own 1.21 vehicle attachment (zombies, skeletons, ...) are not reproduced.
     */
    public static Vec3 vehicleAttachmentPoint(Entity passenger) {
        return passenger instanceof Player ? PLAYER_VEHICLE_ATTACHMENT : Vec3.ZERO;
    }

    /**
     * 1.21 {@code Entity#positionRider(Entity, MoveFunction)}: the rider's vehicle attachment point is placed on
     * {@code ridingPosition} (the vehicle's {@code getPassengerRidingPosition(passenger)}). Call it from a 1.20.1
     * {@code positionRider} override after the {@code hasPassenger} check.
     */
    public static void positionRider(Entity passenger, Vec3 ridingPosition, Entity.MoveFunction callback) {
        Vec3 attachment = vehicleAttachmentPoint(passenger);
        callback.accept(passenger, ridingPosition.x - attachment.x, ridingPosition.y - attachment.y,
                ridingPosition.z - attachment.z);
    }

    /**
     * 1.20.5+ {@code Player#canInteractWithBlock(pos, distance)}. 1.20.1 Forge's block reach attribute plays the role
     * of {@code blockInteractionRange()} (4.5, +0.5 in creative in both versions); 1.21 measures to the block's box,
     * not its centre like Forge's {@code canReach}.
     */
    public static boolean canInteractWithBlock(Player player, BlockPos pos, double distance) {
        double range = player.getBlockReach() + distance;
        return new AABB(pos).distanceToSqr(player.getEyePosition()) < range * range;
    }

    /** 1.20.5+ {@code Player#canInteractWithEntity(entity, distance)}, using Forge's entity reach. */
    public static boolean canInteractWithEntity(Player player, Entity entity, double distance) {
        if (entity.isRemoved()) return false;
        double range = player.getEntityReach() + distance;
        return entity.getBoundingBox().distanceToSqr(player.getEyePosition()) < range * range;
    }
}
