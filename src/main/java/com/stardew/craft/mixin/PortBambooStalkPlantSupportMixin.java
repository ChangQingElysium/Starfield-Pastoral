package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
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
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;

/**
 * PORT(1.20.1): NeoForge 1.21.1 asks the soil's TriState hook before the {@code #bamboo_plantable_on} tag (survival and
 * stalk placement). 1.20.1 only checked the tag. Only mod {@link PortPlantSupport.Soil} blocks answer non-DEFAULT.
 */
@Mixin(BambooStalkBlock.class)
public abstract class PortBambooStalkPlantSupportMixin {
    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$port121CanSurvive(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        TriState decision = PortPlantSupport.decision(level.getBlockState(pos.below()), level, pos.below(), Direction.UP, state);
        if (!decision.isDefault()) cir.setReturnValue(decision.isTrue());
    }

    @WrapOperation(method = "getStateForPlacement", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean stardewcraft$port121PlacementSoil(BlockState soil, TagKey<Block> tag, Operation<Boolean> original,
                                                      @Local(argsOnly = true) BlockPlaceContext context) {
        if (tag == BlockTags.BAMBOO_PLANTABLE_ON) {
            BlockPos soilPos = context.getClickedPos().below();
            TriState decision = PortPlantSupport.decision(soil, context.getLevel(), soilPos, Direction.UP,
                    ((Block) (Object) this).defaultBlockState());
            if (!decision.isDefault()) return decision.isTrue();
        }
        return original.call(soil, tag);
    }
}
