package com.stardew.craft.platform.mixin.event.entity.mixin;


import com.stardew.craft.platform.fabric.event.entity.EntityDispatch;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDeathMixin {
    @Inject(method = "die", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/server/level/ServerPlayer;gameEvent(Lnet/minecraft/core/Holder;)V"),
            cancellable = true, require = 1)
    private void stardewcraft$death(DamageSource source, CallbackInfo ci) {
        if (!EntityDispatch.allowDeath((ServerPlayer) (Object) this, source)) ci.cancel();
    }
}
