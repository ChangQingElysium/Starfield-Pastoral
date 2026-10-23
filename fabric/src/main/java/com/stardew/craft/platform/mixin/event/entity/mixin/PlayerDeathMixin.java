package com.stardew.craft.platform.mixin.event.entity.mixin;


import com.stardew.craft.platform.fabric.event.entity.EntityDispatch;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerDeathMixin {
    @Inject(method = "die", at = @At("HEAD"), cancellable = true, require = 1)
    private void stardewcraft$death(DamageSource source, CallbackInfo ci) {
        if (!EntityDispatch.allowDeath((Player) (Object) this, source)) ci.cancel();
    }
}
