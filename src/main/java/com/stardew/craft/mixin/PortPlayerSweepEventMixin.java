package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.port.event.PortEventHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * PORT(1.20.1): fires MinecraftForge's {@code SweepAttackEvent} where {@code Player#attack} decides the sweep
 * ({@code canPerformAction(SWORD_SWEEP)}); the event's {@code isSweeping()} replaces the vanilla decision.
 */
@Mixin(Player.class)
public abstract class PortPlayerSweepEventMixin {
    @ModifyExpressionValue(method = "attack", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/world/item/ItemStack;canPerformAction(Lnet/minecraftforge/common/ToolAction;)Z"))
    private boolean stardewcraft$sweepAttackEvent(boolean vanillaSweep, @Local(argsOnly = true) Entity target) {
        return PortEventHooks.fireSweepAttack((Player) (Object) this, target, vanillaSweep);
    }
}
