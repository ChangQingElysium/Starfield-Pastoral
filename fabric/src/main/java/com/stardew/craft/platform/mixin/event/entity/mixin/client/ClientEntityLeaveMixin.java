package com.stardew.craft.platform.mixin.event.entity.mixin.client;


import com.stardew.craft.platform.fabric.event.entity.EntityDispatch;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.multiplayer.ClientLevel$EntityCallbacks")
public abstract class ClientEntityLeaveMixin {
    @Shadow(remap = false) @Final private ClientLevel field_27735;

    @Inject(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"), require = 1)
    private void stardewcraft$leave(Entity entity, CallbackInfo ci) {
        EntityDispatch.leave(entity, field_27735);
    }
}
