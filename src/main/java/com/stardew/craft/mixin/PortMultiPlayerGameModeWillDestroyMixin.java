package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.port.PortInheritance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PORT(1.20.1): client half of {@code PortServerPlayerGameModeWillDestroyMixin}. NeoForge 1.21.1
 * {@code MultiPlayerGameMode#destroyBlock} calls {@code block.playerWillDestroy} just before reading the fluid state and
 * calling {@code onDestroyedByPlayer}; Forge 1.20.1 only calls it inside the default {@code onDestroyedByPlayer}.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class PortMultiPlayerGameModeWillDestroyMixin {
    @Inject(method = "destroyBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"))
    private void stardewcraft$playerWillDestroyAt121Position(BlockPos pos, CallbackInfoReturnable<Boolean> cir,
            @Local BlockState state) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null || minecraft.player == null) return;
        if (PortInheritance.isModBlock(state.getBlock())) {
            state.getBlock().playerWillDestroy(level, pos, state, minecraft.player);
        }
    }
}
