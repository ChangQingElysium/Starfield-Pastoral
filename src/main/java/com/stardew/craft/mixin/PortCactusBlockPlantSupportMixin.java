package com.stardew.craft.mixin;

import com.stardew.craft.port.PortPlantSupport;
import com.stardew.craft.port.net.neoforged.neoforge.common.util.TriState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** PORT(1.20.1): NeoForge 1.21.1 lets a non-DEFAULT soil decide after the neighbour check (no liquid-above check). */
@Mixin(CactusBlock.class)
public abstract class PortCactusBlockPlantSupportMixin {
    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$port121CanSurvive(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(pos.relative(direction)).isSolid() || level.getFluidState(pos.relative(direction)).is(FluidTags.LAVA)) {
                cir.setReturnValue(false);
                return;
            }
        }
        TriState decision = PortPlantSupport.decision(level.getBlockState(pos.below()), level, pos.below(), Direction.UP, state);
        if (!decision.isDefault()) cir.setReturnValue(decision.isTrue());
    }
}
