package com.stardew.craft.platform.mixin.event.entity.mixin;


import com.stardew.craft.platform.fabric.event.entity.EntityDispatch;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDeathMixin {
    @Inject(method = "die", at = @At("HEAD"), cancellable = true, require = 1)
    private void stardewcraft$death(DamageSource source, CallbackInfo ci) {
        // Player.die must be canceled before its call to super, otherwise its own drops/stats still run.
        if ((Object) this instanceof Player) return;
        if (!EntityDispatch.allowDeath((LivingEntity) (Object) this, source)) ci.cancel();
    }
}
