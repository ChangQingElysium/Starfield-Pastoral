package com.stardew.craft.platform.mixin.event.entity.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.event.entity.EntityDispatch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerLevel.class)
public abstract class ServerEntityTickMixin {
    @WrapOperation(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"), require = 1)
    private void stardewcraft$tick(Entity entity, Operation<Void> original) {
        EntityDispatch.tick(entity, () -> original.call(entity));
    }
}
