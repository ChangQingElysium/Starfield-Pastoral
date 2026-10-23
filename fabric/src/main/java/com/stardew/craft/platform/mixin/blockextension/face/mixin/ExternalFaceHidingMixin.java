package com.stardew.craft.platform.mixin.blockextension.face.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.blockextension.face.ExternalFaceHiding;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Also reached by Indigo's actual FRAPI BlockRenderInfo.shouldDrawFace. */
@Mixin(Block.class)
abstract class ExternalFaceHidingMixin {
    @WrapOperation(method = "shouldRenderFace", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;skipRendering(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z"), require = 1)
    private static boolean stardew$neighborHides(BlockState state, BlockState neighbor, Direction face, Operation<Boolean> original,
            BlockState methodState, BlockGetter level, BlockPos ownPos, Direction methodFace, BlockPos neighborPos) {
        return original.call(state, neighbor, face) || ExternalFaceHiding.hides(state, neighbor, level, neighborPos, face);
    }
}
