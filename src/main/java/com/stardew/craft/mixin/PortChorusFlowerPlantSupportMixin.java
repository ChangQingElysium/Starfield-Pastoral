package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.port.PortPlantSupport;
import com.stardew.craft.port.net.neoforged.neoforge.common.util.TriState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.ChorusPlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PORT(1.20.1): NeoForge 1.21.1 asks the soil's TriState hook in {@code ChorusFlowerBlock#canSurvive} (first) and in the
 * two soil checks of {@code randomTick} (the block below the flower, and the block below the plant column). A non-DEFAULT
 * answer there takes the place of the END_STONE test, which is exactly where 1.20.1 tests END_STONE.
 */
@Mixin(ChorusFlowerBlock.class)
public abstract class PortChorusFlowerPlantSupportMixin {
    @Shadow @Final private ChorusPlantBlock plant;

    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$port121CanSurvive(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        TriState decision = PortPlantSupport.decision(level.getBlockState(pos.below()), level, pos.below(), Direction.UP, state);
        if (!decision.isDefault()) cir.setReturnValue(decision.isTrue());
    }

    @WrapOperation(method = "randomTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z"))
    private boolean stardewcraft$port121GrowthSoil(BlockState soil, Block expected, Operation<Boolean> original,
                                                   @Local(argsOnly = true) BlockState flower,
                                                   @Local(argsOnly = true) ServerLevel level,
                                                   @Local(argsOnly = true) BlockPos pos) {
        if (expected == Blocks.END_STONE && soil.getBlock() instanceof PortPlantSupport.Soil) {
            BlockPos soilPos = pos.below();
            if (level.getBlockState(soilPos).is(this.plant)) {
                // Inner check: first non-plant block under the column (1.21.1 pos.below(j + 1), at most 4 deep).
                soilPos = pos.below(2);
                for (int k = 0; k < 4 && level.getBlockState(soilPos).is(this.plant); k++) soilPos = soilPos.below();
            }
            TriState decision = PortPlantSupport.decision(soil, level, soilPos, Direction.UP, flower);
            if (!decision.isDefault()) return decision.isTrue();
        }
        return original.call(soil, expected);
    }
}
