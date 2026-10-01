package com.stardew.craft.mixin;

import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PORT(1.20.1): vanilla 1.21.1 gives {@link DecoratedPotBlock} and {@link BellBlock} {@code rotate}/{@code mirror}
 * overrides (horizontal facing rotates; mirror = rotate by {@code mirror.getRotation(facing)}). In 1.20.1 neither class
 * overrides them, so structure/prefab rotation and mirroring leave their facing unchanged. These are the only vanilla
 * blocks whose rotate/mirror differ between 1.20.1 and 1.21.1. Both classes inherit the methods straight from
 * {@link BlockBehaviour}, so the 1.21.1 bodies are applied there for exactly these block types.
 */
@Mixin(BlockBehaviour.class)
public abstract class PortBlockRotationMixin {
    @Inject(method = "rotate(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/Rotation;)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"), cancellable = true)
    private void stardewcraft$rotate121(BlockState state, Rotation rotation, CallbackInfoReturnable<BlockState> cir) {
        if (stardewcraft$rotatesLike121(this)) {
            cir.setReturnValue(state.setValue(BlockStateProperties.HORIZONTAL_FACING,
                    rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING))));
        }
    }

    @Inject(method = "mirror(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/Mirror;)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"), cancellable = true)
    private void stardewcraft$mirror121(BlockState state, Mirror mirror, CallbackInfoReturnable<BlockState> cir) {
        if (stardewcraft$rotatesLike121(this)) {
            cir.setReturnValue(state.rotate(mirror.getRotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING))));
        }
    }

    private static boolean stardewcraft$rotatesLike121(Object block) {
        return block instanceof DecoratedPotBlock || block instanceof BellBlock;
    }
}
