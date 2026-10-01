package com.stardew.craft.mixin;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PORT(1.20.1): 1.20.1's default {@code BlockBehaviour#randomTick} runs the scheduled {@code tick};
 * 1.21.1's default does nothing. This body only runs when a block does not override randomTick (or
 * calls super), so cancelling it for StardewCraft blocks restores the 1.21.1 behaviour for the mod
 * without touching vanilla or other mods' blocks.
 */
@Mixin(BlockBehaviour.class)
public abstract class PortDefaultRandomTickMixin {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$noDefaultRandomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random,
                                                   CallbackInfo ci) {
        if ((Object) this instanceof Block block
                && StardewCraft.MODID.equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace())) {
            ci.cancel();
        }
    }
}
