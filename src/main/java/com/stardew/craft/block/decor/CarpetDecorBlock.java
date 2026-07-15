package com.stardew.craft.block.decor;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A flat decor block that only occupies the MAIN cell — no extensions are placed.
 * The model may visually extend beyond the single block, but neighbouring cells
 * remain free so other blocks can be placed on top of / next to the carpet.
 */
public class CarpetDecorBlock extends MapDecorStaticBlock {

    public CarpetDecorBlock(Properties properties, String modelId) {
        super(properties, modelId);
    }

    @Override
    protected Set<CellOffset> localOccupiedOffsets() {
        return Set.of(CellOffset.ZERO);
    }

    /** Existing block-based rugs are converted lazily the first time furniture is placed on them. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof BlockItem blockItem
                && !(blockItem.getBlock() instanceof CarpetDecorBlock)) {
            com.stardew.craft.entity.decor.CarpetPlacementService.migratePlacedBlock(level, pos, state, player);
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return context.getItemInHand().getItem() instanceof BlockItem blockItem
                && !(blockItem.getBlock() instanceof CarpetDecorBlock);
    }
}
