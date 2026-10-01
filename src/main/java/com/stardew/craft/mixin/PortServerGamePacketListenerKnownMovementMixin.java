package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.port.PortEntities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * PORT(1.20.1): 1.21 records the accepted client movement as the player's known movement
 * ({@code ServerPlayer#setKnownMovement}) at the points where movement statistics are checked:
 * <ul>
 * <li>{@code handleMovePlayer}: the player's position delta of this packet (the same delta passed to
 * {@code doCheckFallDamage}).</li>
 * <li>{@code handleMoveVehicle}: the root vehicle's position delta of this packet ({@code entity - d0/d1/d2}).
 * 1.20.1's statistics call passes the player's delta instead, which is kept unchanged.</li>
 * </ul>
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PortServerGamePacketListenerKnownMovementMixin {
    @Shadow
    public ServerPlayer player;

    @WrapOperation(method = "handleMovePlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;doCheckFallDamage(DDDZ)V"))
    private void stardewcraft$recordPlayerMovement(ServerPlayer target, double dx, double dy, double dz,
            boolean onGround, Operation<Void> original) {
        original.call(target, dx, dy, dz, onGround);
        PortEntities.setKnownMovement(target, new Vec3(dx, dy, dz));
    }

    @WrapOperation(method = "handleMoveVehicle",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;checkMovementStatistics(DDD)V"))
    private void stardewcraft$recordVehicleMovement(ServerPlayer target, double dx, double dy, double dz,
            Operation<Void> original, @Local Entity vehicle, @Local(ordinal = 0) double startX,
            @Local(ordinal = 1) double startY, @Local(ordinal = 2) double startZ) {
        PortEntities.setKnownMovement(target,
                new Vec3(vehicle.getX() - startX, vehicle.getY() - startY, vehicle.getZ() - startZ));
        original.call(target, dx, dy, dz);
    }
}
