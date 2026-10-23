package com.stardew.craft.platform.mixin.event.entity.mixin.client;


import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.event.entity.EntityDispatch;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientLevel.class)
public abstract class ClientEntityLifecycleMixin {
    @WrapOperation(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"), require = 1)
    private void stardewcraft$tick(Entity entity, Operation<Void> original) {
        EntityDispatch.tick(entity, () -> original.call(entity));
    }

    @WrapMethod(method = "addEntity")
    private void stardewcraft$join(Entity entity, Operation<Void> original) {
        // Cancellation precedes removeEntity(oldId): a rejected replacement must retain the old client entity.
        EntityDispatch.join(entity, (ClientLevel) (Object) this, false, () -> {
            original.call(entity);
            return true;
        });
    }
}
