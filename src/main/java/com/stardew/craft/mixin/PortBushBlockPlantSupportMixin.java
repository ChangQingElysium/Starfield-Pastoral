package com.stardew.craft.mixin;

import com.stardew.craft.port.PortPlantSupport;
import com.stardew.craft.port.net.neoforged.neoforge.common.util.TriState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.IPlantable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PORT(1.20.1): NeoForge 1.21.1 {@code BushBlock} soil rules (see {@link PortPlantSupport}). 1.20.1 accepted only
 * {@code Blocks.FARMLAND} and, for placed plants, Forge's plant-type rules on top of {@code mayPlaceOn} (e.g. any dirt for
 * every PLAINS plant); 1.21.1 asks the soil's TriState hook, then only {@code mayPlaceOn}.
 */
@Mixin(BushBlock.class)
public abstract class PortBushBlockPlantSupportMixin {
    @Shadow
    protected abstract boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos);

    @Inject(method = "mayPlaceOn", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$port121MayPlaceOn(BlockState state, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(state.is(BlockTags.DIRT) || PortPlantSupport.farmland(state));
    }

    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$port121CanSurvive(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockPos below = pos.below();
        BlockState soil = level.getBlockState(below);
        TriState decision = PortPlantSupport.decision(soil, level, below, Direction.UP, state);
        if (!decision.isDefault()) {
            cir.setReturnValue(decision.isTrue());
        } else if (state.getBlock() == (Object) this && PortPlantSupport.legacyForgeSoil(soil)) {
            cir.setReturnValue(soil.canSustainPlant(level, below, Direction.UP, (IPlantable) (Object) this));
        } else {
            cir.setReturnValue(this.mayPlaceOn(soil, level, below));
        }
    }
}
