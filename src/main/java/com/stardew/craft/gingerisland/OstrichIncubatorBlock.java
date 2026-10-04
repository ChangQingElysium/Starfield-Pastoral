package com.stardew.craft.gingerisland;

import com.stardew.craft.animal.runtime.FarmFeed;
import com.stardew.craft.animal.runtime.LivestockSpecies;
import com.stardew.craft.block.utility.IncubatorBlock;
import com.stardew.craft.building.runtime.BuildingService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Two cells, one persisted incubation receipt. Loaded is retained until the animal is claimed. */
public final class OstrichIncubatorBlock extends IncubatorBlock {
    public static final BooleanProperty LOADED = BooleanProperty.create("loaded");

    public OstrichIncubatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LOADED, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LOADED);
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        var level = context.getLevel();
        for (BlockPos cell : new BlockPos[]{pos, pos.above()}) {
            if (level.isOutsideBuildHeight(cell) || !level.getWorldBorder().isWithinBounds(cell)
                    || !level.getFluidState(cell).isEmpty()
                    || !level.getBlockState(cell).canBeReplaced(context)
                    || !level.isUnobstructed(defaultBlockState(), cell, CollisionContext.empty())) return null;
        }
        if (context.getPlayer() instanceof ServerPlayer player) {
            if (!IslandContext.canModifyAt(player, pos)
                    || !IslandContext.canModifyAt(player, pos.above())) return null;
            // Creative workshop placement does not grant production or newborn ownership.
            if (!player.isCreative()) {
                var home = FarmFeed.home(player.serverLevel(), pos);
                if (home == null || !home.family().equals(LivestockSpecies.OSTRICH.family())
                        || !BuildingService.canManage(player, home)) return null;
            }
        }
        return super.getStateForPlacement(context);
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        // BlockItem applies held state components after getStateForPlacement.
        // Normalize only this placement; real incubation updates remain unrestricted.
        BlockState selected = state.setValue(PART, Part.MAIN).setValue(WORKING, false).setValue(LOADED, false);
        if (selected != state) level.setBlock(pos, selected, Block.UPDATE_CLIENTS);
        super.setPlacedBy(level, pos, selected, placer, stack);
    }

    private boolean canUse(Player player, BlockPos pos, BlockState state) {
        return !(player instanceof ServerPlayer actor)
                || IslandContext.canModifyAt(actor, getMainPos(pos, state));
    }

    @Override public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        return canUse(player, pos, state) ? super.useItemOn(stack, state, level, pos, player, hand, hit)
                : ItemInteractionResult.FAIL;
    }
    @Override public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        return canUse(player, pos, state) ? super.useWithoutItem(state, level, pos, player, hit) : InteractionResult.FAIL;
    }

    /** OnlyCompleteOvernight: expire at the first morning at or after the source duration. */
    public static long completionMorning(long now, int minutes) {
        return Math.floorDiv(now + minutes + 1599, 1600) * 1600;
    }
}
