package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.port.event.PortEventHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * PORT(1.20.1): fires MinecraftForge's {@code EntityTickEvent.Pre/Post} around {@code Entity#tick()} for non-passengers and
 * {@code Entity#rideTick()} for passengers, where MinecraftForge 21.1 fires them. A cancelled Pre skips the tick and Post.
 */
@Mixin(ClientLevel.class)
public abstract class PortClientLevelEntityTickMixin {
    @WrapOperation(method = "tickNonPassenger",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void stardewcraft$entityTickEvent(Entity entity, Operation<Void> original) {
        if (PortEventHooks.fireEntityTickPre(entity)) return;
        original.call(entity);
        PortEventHooks.fireEntityTickPost(entity);
    }

    @WrapOperation(method = "tickPassenger",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;rideTick()V"))
    private void stardewcraft$passengerTickEvent(Entity passenger, Operation<Void> original) {
        if (PortEventHooks.fireEntityTickPre(passenger)) return;
        original.call(passenger);
        PortEventHooks.fireEntityTickPost(passenger);
    }
}
