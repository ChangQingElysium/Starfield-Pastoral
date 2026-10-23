package com.stardew.craft.platform.mixin.event.entity.player.mixin;


import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.stardew.craft.platform.fabric.event.entity.player.AttackDecisionHooks;
import com.stardew.craft.platform.fabric.event.entity.player.CriticalHitEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerAttackDecisionsMixin {
    @ModifyVariable(method = "attack", at = @At("STORE"), index = 9)
    private boolean stardewcraft$critical(boolean nativeCritical, @Local(argsOnly = true) Entity target,
            @Share("stardewcraft$criticalDecision") LocalRef<CriticalHitEvent> decision) {
        var event = AttackDecisionHooks.critical((Player) (Object) this, target, nativeCritical);
        decision.set(event);
        return event.isCriticalHit();
    }
    @ModifyConstant(method = "attack", constant = @Constant(floatValue = 1.5F))
    private float stardewcraft$criticalMultiplier(float original,
            @Share("stardewcraft$criticalDecision") LocalRef<CriticalHitEvent> decision) {
        return decision.get().getDamageMultiplier();
    }
    @ModifyVariable(method = "attack", at = @At(value = "LOAD", ordinal = 1), index = 9)
    private boolean stardewcraft$criticalBlocksSweep(boolean critical,
            @Share("stardewcraft$criticalDecision") LocalRef<CriticalHitEvent> decision) {
        return AttackDecisionHooks.blocksSweep(decision.get());
    }
    @Inject(method = "attack", at = @At(value = "CONSTANT", args = "floatValue=0.0"),
            slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getSpeed()F"),
                    to = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getDeltaMovement()Lnet/minecraft/world/phys/Vec3;")))
    private void stardewcraft$sweepDecision(Entity target, CallbackInfo callback, @Local(index = 11) LocalBooleanRef sweep) {
        sweep.set(AttackDecisionHooks.sweep((Player) (Object) this, target, sweep.get()));
    }
}
