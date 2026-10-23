package com.stardew.craft.platform.mixin.event.entity.mixin;


import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.entity.EntityTravelToDimensionEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Entity.class, ServerPlayer.class})
public abstract class DimensionTravelPermissionMixin {
    @Inject(method = "changeDimension", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$allowDimension(DimensionTransition transition, CallbackInfoReturnable<Entity> callback) {
        if (FabricEvents.GAME.post(new EntityTravelToDimensionEvent((Entity) (Object) this,
                transition.newLevel().dimension())).isCanceled()) callback.setReturnValue(null);
    }
}
