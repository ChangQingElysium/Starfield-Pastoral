package com.stardew.craft.platform.mixin.event.entity.player.mixin;


import com.stardew.craft.platform.fabric.extension.CommonHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Player.class, priority = 900)
abstract class PlayerAttackMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$attack(Entity target, CallbackInfo ci) {
        if (!CommonHooks.onPlayerAttackTarget((Player) (Object) this, target)) ci.cancel();
    }
}
