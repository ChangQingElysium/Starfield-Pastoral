package com.stardew.craft.mixin;

import com.stardew.craft.port.PortPlantSupport;
import com.stardew.craft.port.net.neoforged.neoforge.common.util.TriState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** PORT(1.20.1): NeoForge 1.21.1 lets a non-DEFAULT soil decide sugar cane survival (no water check). */
@Mixin(SugarCaneBlock.class)
public abstract class PortSugarCaneBlockPlantSupportMixin {
    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$port121CanSurvive(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockState soil = level.getBlockState(pos.below());
        if (soil.is((Block) (Object) this)) {
            cir.setReturnValue(true);
            return;
        }
        TriState decision = PortPlantSupport.decision(soil, level, pos.below(), Direction.UP, state);
        if (!decision.isDefault()) cir.setReturnValue(decision.isTrue());
    }
}
