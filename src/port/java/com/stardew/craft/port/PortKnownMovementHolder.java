package com.stardew.craft.port;

import net.minecraft.world.phys.Vec3;

/** Implemented on {@code ServerPlayer} by {@code PortServerPlayerKnownMovementMixin}: 1.21 {@code lastKnownClientMovement}. */
public interface PortKnownMovementHolder {
    Vec3 stardewcraft$getLastKnownClientMovement();

    void stardewcraft$setLastKnownClientMovement(Vec3 movement);
}
