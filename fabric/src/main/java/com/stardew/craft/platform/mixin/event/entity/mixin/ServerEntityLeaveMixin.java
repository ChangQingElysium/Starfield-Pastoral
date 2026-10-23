package com.stardew.craft.platform.mixin.event.entity.mixin;


import com.stardew.craft.platform.fabric.event.entity.EntityDispatch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public abstract class ServerEntityLeaveMixin {
    @Shadow(remap = false) @Final private ServerLevel field_26936;

    @Inject(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"), require = 1)
    private void stardewcraft$leave(Entity entity, CallbackInfo ci) {
        EntityDispatch.leave(entity, field_26936);
    }
}
