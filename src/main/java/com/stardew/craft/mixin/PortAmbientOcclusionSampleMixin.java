package com.stardew.craft.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * PORT(1.20.1): NeoForge 21.1 fixes MC-43968 by testing the four adjacent side
 * cells, rather than moving those probes another cell along the face normal.
 * Keep the subsequent four diagonal moves and all AO/light interpolation intact.
 */
@Mixin(targets = "net.minecraft.client.renderer.block.ModelBlockRenderer$AmbientOcclusionFace")
public abstract class PortAmbientOcclusionSampleMixin {
    private static final String CALCULATE = "calculate(Lnet/minecraft/world/level/BlockAndTintGetter;"
            + "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/core/Direction;[FLjava/util/BitSet;Z)V";
    private static final String MOVE = "Lnet/minecraft/core/BlockPos$MutableBlockPos;"
            + "move(Lnet/minecraft/core/Direction;)Lnet/minecraft/core/BlockPos$MutableBlockPos;";

    @Redirect(method = CALCULATE, at = @At(value = "INVOKE", target = MOVE, ordinal = 0),
            require = 1, expect = 1, allow = 1)
    private BlockPos.MutableBlockPos stardewcraft$side0(BlockPos.MutableBlockPos position, Direction direction) {
        return position;
    }

    @Redirect(method = CALCULATE, at = @At(value = "INVOKE", target = MOVE, ordinal = 1),
            require = 1, expect = 1, allow = 1)
    private BlockPos.MutableBlockPos stardewcraft$side1(BlockPos.MutableBlockPos position, Direction direction) {
        return position;
    }

    @Redirect(method = CALCULATE, at = @At(value = "INVOKE", target = MOVE, ordinal = 2),
            require = 1, expect = 1, allow = 1)
    private BlockPos.MutableBlockPos stardewcraft$side2(BlockPos.MutableBlockPos position, Direction direction) {
        return position;
    }

    @Redirect(method = CALCULATE, at = @At(value = "INVOKE", target = MOVE, ordinal = 3),
            require = 1, expect = 1, allow = 1)
    private BlockPos.MutableBlockPos stardewcraft$side3(BlockPos.MutableBlockPos position, Direction direction) {
        return position;
    }
}
