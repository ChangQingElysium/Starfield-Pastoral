package com.stardew.craft.mixin;

import com.stardew.craft.integration.xaero.XaeroMapMaterials;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "xaero.common.minimap.write.MinimapWriter", remap = false)
public abstract class XaeroMinimapWriterMixin {
    @Unique private int stardewcraft$colourSeason = -1;
    @Shadow public abstract void setClearBlockColours(boolean clear);

    // PORT(1.20.1): Forge 1.20.1 production runs SRG names; the vanilla call target must go through the refmap.
    // Minimap skips unpackFramedBlocks entirely unless FramedBlocks is installed.
    // Resolve immediately after its primary sample, independently of that optional mod.
    @ModifyExpressionValue(method = "findBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunk;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", ordinal = 0, remap = true))
    private BlockState stardewcraft$resolveTemplate(BlockState state,
            @Local(argsOnly = true) Level level,
            @Local(argsOnly = true, ordinal = 1) BlockPos.MutableBlockPos globalPos) {
        return state == null ? null : XaeroMapMaterials.resolve(state, level, globalPos);
    }

    @Inject(method = "writeChunk", at = @At("HEAD"))
    private void stardewcraft$invalidateSeasonColours(CallbackInfoReturnable<Boolean> callback) {
        int season = XaeroMapMaterials.season();
        if (season != stardewcraft$colourSeason) {
            stardewcraft$colourSeason = season;
            // Xaero clears its caches and marks the next complete write cycle changed.
            setClearBlockColours(true);
        }
    }
}
