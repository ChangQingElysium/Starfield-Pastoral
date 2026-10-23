package com.stardew.craft.platform.mixin.blockextension.plant.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.blockextension.plant.PlantHooks;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BambooStalkBlock.class)
abstract class BambooPlacementSoilMixin {
    @WrapOperation(method = "getStateForPlacement", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z", ordinal = 0), require = 1)
    private boolean stardewcraft$soilAfterFluidGuard(BlockState soil, TagKey<Block> tag, Operation<Boolean> original, BlockPlaceContext context) {
        var decision = PlantHooks.canSustainPlant(soil, context.getLevel(), context.getClickedPos().below(), Direction.UP, ((BambooStalkBlock) (Object) this).defaultBlockState());
        return decision.isDefault() ? original.call(soil, tag) : decision.isTrue();
    }
}
