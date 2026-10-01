package com.stardew.craft.mixin;

import com.stardew.craft.port.PortPlantSupport;
import com.stardew.craft.port.net.neoforged.neoforge.common.util.TriState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.IPlantable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** PORT(1.20.1): NeoForge 1.21.1 {@code MushroomBlock#canSurvive} (soil decision, then dark + solid-render soil). */
@Mixin(MushroomBlock.class)
public abstract class PortMushroomBlockPlantSupportMixin {
    @Shadow
    protected abstract boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos);

    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$port121CanSurvive(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockPos below = pos.below();
        BlockState soil = level.getBlockState(below);
        TriState decision = PortPlantSupport.decision(soil, level, below, Direction.UP, state);
        if (soil.is(BlockTags.MUSHROOM_GROW_BLOCK)) cir.setReturnValue(true);
        else if (!decision.isDefault()) cir.setReturnValue(decision.isTrue());
        else cir.setReturnValue(level.getRawBrightness(pos, 0) < 13 && (PortPlantSupport.legacyForgeSoil(soil)
                ? soil.canSustainPlant(level, below, Direction.UP, (IPlantable) (Object) this)
                : this.mayPlaceOn(soil, level, below)));
    }
}
