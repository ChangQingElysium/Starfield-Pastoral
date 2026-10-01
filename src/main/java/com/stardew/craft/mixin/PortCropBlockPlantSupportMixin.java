package com.stardew.craft.mixin;

import com.stardew.craft.port.PortPlantSupport;
import com.stardew.craft.port.net.neoforged.neoforge.common.util.TriState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.IPlantable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** PORT(1.20.1): NeoForge 1.21.1 {@code CropBlock} soil, light and growth-speed rules (see {@link PortPlantSupport}). */
@Mixin(CropBlock.class)
public abstract class PortCropBlockPlantSupportMixin extends BushBlock {
    protected PortCropBlockPlantSupportMixin(Properties properties) {
        super(properties);
    }

    @Inject(method = "mayPlaceOn", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$port121MayPlaceOn(BlockState state, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(PortPlantSupport.farmland(state));
    }

    /** 1.21.1: a non-DEFAULT soil decides alone; otherwise raw light >= 8 (no sky fallback) and the bush rule. */
    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$port121CanSurvive(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        TriState decision = PortPlantSupport.decision(level.getBlockState(pos.below()), level, pos.below(), Direction.UP, state);
        cir.setReturnValue(decision.isDefault()
                ? PortPlantSupport.sufficientCropLight(level, pos) && super.canSurvive(state, level, pos)
                : decision.isTrue());
    }

    /** 1.21.1 {@code CropBlock#getGrowthSpeed(BlockState, BlockGetter, BlockPos)}. */
    @Inject(method = "getGrowthSpeed", at = @At("HEAD"), cancellable = true)
    private static void stardewcraft$port121GrowthSpeed(Block block, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        BlockState plant = level.getBlockState(pos);
        if (!plant.is(block)) plant = block.defaultBlockState();
        float speed = 1.0F;
        BlockPos below = pos.below();
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                float local = 0.0F;
                BlockPos soilPos = below.offset(i, 0, j);
                BlockState soil = level.getBlockState(soilPos);
                TriState decision = PortPlantSupport.decision(soil, level, soilPos, Direction.UP, plant);
                boolean supported;
                if (!decision.isDefault()) supported = decision.isTrue();
                else if (PortPlantSupport.legacyForgeSoil(soil) && block instanceof IPlantable plantable)
                    supported = soil.canSustainPlant(level, soilPos, Direction.UP, plantable);
                else supported = PortPlantSupport.farmland(soil);
                if (supported) {
                    local = 1.0F;
                    if (soil.isFertile(level, pos.offset(i, 0, j))) local = 3.0F;
                }
                if (i != 0 || j != 0) local /= 4.0F;
                speed += local;
            }
        }
        BlockPos north = pos.north(), south = pos.south(), west = pos.west(), east = pos.east();
        boolean xRow = level.getBlockState(west).is(block) || level.getBlockState(east).is(block);
        boolean zRow = level.getBlockState(north).is(block) || level.getBlockState(south).is(block);
        if (xRow && zRow) {
            speed /= 2.0F;
        } else if (level.getBlockState(west.north()).is(block) || level.getBlockState(east.north()).is(block)
                || level.getBlockState(east.south()).is(block) || level.getBlockState(west.south()).is(block)) {
            speed /= 2.0F;
        }
        cir.setReturnValue(speed);
    }
}
