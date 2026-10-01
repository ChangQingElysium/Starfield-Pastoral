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
 * PORT(1.20.1): NeoForge 1.21.1 farmland rules for every FarmBlock subclass: {@code isFertile} is true for any moist
 * FarmBlock (Forge 1.20.1: only {@code Blocks.FARMLAND}), and dry farmland is kept only under {@code #maintains_farmland}
 * (Forge 1.20.1: under any IPlantable the soil sustains).
 */
@Mixin(FarmBlock.class)
public abstract class PortFarmBlockPlantSupportMixin {
    /** Overrides {@code IForgeBlock#isFertile} with the 1.21.1 {@code IBlockExtension#isFertile} default. */
    public boolean isFertile(BlockState state, BlockGetter level, BlockPos pos) {
        return PortPlantSupport.fertile(state);
    }

    @Inject(method = "shouldMaintainFarmland", at = @At("HEAD"), cancellable = true)
    private static void stardewcraft$port121Maintain(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!PortPlantSupport.legacyForgeSoil(level.getBlockState(pos)))
            cir.setReturnValue(level.getBlockState(pos.above()).is(BlockTags.MAINTAINS_FARMLAND));
    }
}
