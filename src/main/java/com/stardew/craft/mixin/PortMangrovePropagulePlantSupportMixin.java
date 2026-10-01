package com.stardew.craft.mixin;

import com.stardew.craft.port.PortPlantSupport;
import com.stardew.craft.port.net.neoforged.neoforge.common.util.TriState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.MangrovePropaguleBlock;
import org.spongepowered.asm.mixin.Shadow;

/** PORT(1.20.1): NeoForge 1.21.1 asks the supporting block's TriState hook first (cocoa side, hanging propagule). */
@Mixin(MangrovePropaguleBlock.class)
public abstract class PortMangrovePropagulePlantSupportMixin {
    @Shadow
    private static boolean isHanging(BlockState state) {
        throw new AssertionError();
    }

    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$port121CanSurvive(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!isHanging(state)) return;
        TriState decision = PortPlantSupport.decision(level.getBlockState(pos.above()), level, pos.above(), Direction.DOWN, state);
        if (!decision.isDefault()) cir.setReturnValue(decision.isTrue());
    }
}
