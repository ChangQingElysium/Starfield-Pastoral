package com.stardew.craft.mixin;

import com.stardew.craft.port.PortPlantSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PORT(1.20.1): NeoForge 1.21.1 farmland rules: {@code isFertile} is true for any moist vanilla/StardewCraft FarmBlock
 * (Forge 1.20.1: only {@code Blocks.FARMLAND}), and dry farmland is kept only under {@code #maintains_farmland}
 * (Forge 1.20.1: under any IPlantable the soil sustains). Third-party farmland (and vanilla farmland under a
 * third-party plant) keeps the Forge 1.20.1 rules ({@code PortPlantSupport#portRules}); the 1.21.1 fertility of a
 * third-party FarmBlock under a StardewCraft crop is applied in the crop growth path ({@code fertile121}).
 */
@Mixin(FarmBlock.class)
public abstract class PortFarmBlockPlantSupportMixin {
    /** Overrides {@code IForgeBlock#isFertile}: the 1.21.1 default for vanilla/StardewCraft farmland (see class doc). */
    public boolean isFertile(BlockState state, BlockGetter level, BlockPos pos) {
        return PortPlantSupport.farmBlockFertile(state);
    }

    @Inject(method = "shouldMaintainFarmland", at = @At("HEAD"), cancellable = true)
    private static void stardewcraft$port121Maintain(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockState soil = level.getBlockState(pos);
        if (!PortPlantSupport.legacyForgeSoil(soil) && PortPlantSupport.portRules(soil, level.getBlockState(pos.above()).getBlock()))
            cir.setReturnValue(level.getBlockState(pos.above()).is(BlockTags.MAINTAINS_FARMLAND));
    }
}
