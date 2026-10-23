package com.stardew.craft.platform.mixin.event.mixin;


import com.stardew.craft.platform.fabric.event.FabricPlayerEvents;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Project tracking payloads are sent after the spawn bundle, as on NeoForge. */
@Mixin(ServerEntity.class)
abstract class ServerEntityTrackingMixin {
    @Shadow @Final private Entity entity;

    @Inject(method = "addPairing", at = @At("TAIL"))
    private void stardewcraft$started(ServerPlayer player, CallbackInfo ci) {
        FabricPlayerEvents.postTrackingStarted(entity, player);
    }

    @Inject(method = "removePairing", at = @At("TAIL"))
    private void stardewcraft$stopped(ServerPlayer player, CallbackInfo ci) {
        FabricPlayerEvents.postTrackingStopped(entity, player);
    }
}
