package com.stardew.craft.mixin;

import com.stardew.craft.port.PortKnownMovementHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** PORT(1.20.1): 1.21 {@code ServerPlayer#lastKnownClientMovement} (see {@code PortEntities#getKnownMovement}). */
@Mixin(ServerPlayer.class)
public abstract class PortServerPlayerKnownMovementMixin implements PortKnownMovementHolder {
    @Unique
    private Vec3 stardewcraft$lastKnownClientMovement = Vec3.ZERO;

    @Override
    public Vec3 stardewcraft$getLastKnownClientMovement() {
        return stardewcraft$lastKnownClientMovement;
    }

    @Override
    public void stardewcraft$setLastKnownClientMovement(Vec3 movement) {
        stardewcraft$lastKnownClientMovement = movement;
    }
}
