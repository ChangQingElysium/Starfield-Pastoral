package com.stardew.craft.gingerisland;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Boats can be aimed directly at source water, without a temporary support block. */
public final class GingerIslandBoatItem extends GingerIslandDecorBlockItem {
    public GingerIslandBoatItem(Block block, String itemTypeKey, int sellPrice, Properties properties) {
        super(block, itemTypeKey, sellPrice, properties);
    }

    @Override public BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
        var level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        BlockPos water = level.getFluidState(clicked).is(FluidTags.WATER) ? clicked
                : level.getFluidState(clicked.below()).is(FluidTags.WATER) ? clicked.below() : null;
        if (water != null) {
            // Normal block targeting ignores fluids and may hit the seabed. Its
            // BlockPlaceContext starts inside the water column, not at the sea's
            // surface. Find that surface before choosing the authored waterline.
            while (!level.isOutsideBuildHeight(water.above())
                    && level.getFluidState(water.above()).is(FluidTags.WATER)) water = water.above();
            if (!level.getFluidState(water).isSource()) return null;
            var decor = (com.stardew.craft.block.decor.MapDecorStaticBlock) getBlock();
            var anchor = water.above().relative(context.getHorizontalDirection(), decor.waterPlacementForwardOffset());
            var adjusted = BlockPlaceContext.at(context, anchor, context.getClickedFace());
            return adjusted.getClickedPos().equals(anchor) && adjusted.canPlace() ? adjusted : null;
        }
        // On land keep the whole keel above the clicked foundation.
        return super.updatePlacementContext(context);
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().getFluidState(context.getClickedPos()).is(Fluids.WATER))
            return super.useOn(new UseOnContext(context.getLevel(), context.getPlayer(), context.getHand(),
                    context.getItemInHand(), new BlockHitResult(context.getClickLocation(),
                            net.minecraft.core.Direction.UP, context.getClickedPos().above(), false)));
        return super.useOn(context);
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK || !level.getFluidState(hit.getBlockPos()).is(Fluids.WATER))
            return InteractionResultHolder.pass(stack);
        if (!level.mayInteract(player, hit.getBlockPos()) || !player.mayUseItemAt(hit.getBlockPos(), hit.getDirection(), stack))
            return InteractionResultHolder.fail(stack);
        InteractionResult result = super.useOn(new UseOnContext(player, hand, hit.withPosition(hit.getBlockPos().above())));
        return new InteractionResultHolder<>(result, player.getItemInHand(hand));
    }
}
