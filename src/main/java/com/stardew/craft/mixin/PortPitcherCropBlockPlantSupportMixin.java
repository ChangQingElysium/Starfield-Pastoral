package com.stardew.craft.mixin;

import com.stardew.craft.port.PortPlantSupport;
import com.stardew.craft.port.net.neoforged.neoforge.common.util.TriState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** PORT(1.20.1): NeoForge 1.21.1 {@code PitcherCropBlock#canSurvive}/{@code mayPlaceOn}. */
@Mixin(PitcherCropBlock.class)
public abstract class PortPitcherCropBlockPlantSupportMixin extends DoublePlantBlock {
    protected PortPitcherCropBlockPlantSupportMixin(Properties properties) {
        super(properties);
    }

    @Shadow
    private static boolean isLower(BlockState state) {
        throw new AssertionError();
    }

    @Inject(method = "mayPlaceOn", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$port121MayPlaceOn(BlockState state, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!PortPlantSupport.portRules(state, this)) return;
        cir.setReturnValue(PortPlantSupport.farmland(state));
    }

    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$port121CanSurvive(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockState soil = level.getBlockState(pos.below());
        if (!PortPlantSupport.portRules(soil, this)) return;
        TriState decision = PortPlantSupport.decision(soil, level, pos.below(), Direction.UP, state);
        cir.setReturnValue(isLower(state) && !PortPlantSupport.sufficientCropLight(level, pos)
                ? decision.isTrue() : super.canSurvive(state, level, pos));
    }
}
