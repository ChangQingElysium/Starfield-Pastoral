package com.stardew.craft.platform.mixin.blockextension.client;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.blockextension.BlockBehaviors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MultiPlayerGameMode.class)
abstract class MiningSoundMixin {
    @Shadow @Final private Minecraft minecraft;
    @WrapOperation(method = "continueDestroyBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"), require = 1)
    private SoundType stardewcraft$miningMaterial(BlockState state, Operation<SoundType> original, BlockPos pos, Direction face) {
        return BlockBehaviors.resolveSound(state, minecraft.level, pos, minecraft.player, original.call(state));
    }
}
