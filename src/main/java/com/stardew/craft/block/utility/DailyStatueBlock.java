package com.stardew.craft.block.utility;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.blockentity.DailyStatueBlockEntity;
import com.stardew.craft.blockentity.ModBlockEntities;
import com.stardew.craft.blockentity.UtilityDropHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

/** A placeable utility statue which produces one source-faithful daily reward. */
public final class DailyStatueBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final DailyStatueBlockEntity.Kind kind;
    private final VoxelShape[] shapes;

    public DailyStatueBlock(DailyStatueBlockEntity.Kind kind, String model, Properties properties) {
        super(properties);
        this.kind = kind;
        this.shapes = ModelVoxelShapeCache.horizontalShapes(model, Direction.NORTH);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public DailyStatueBlockEntity.Kind kind() {
        return kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes[ModelVoxelShapeCache.horizontalIndex(state.getValue(FACING))];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state,
                                      net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        return List.of(new ItemStack(kind == DailyStatueBlockEntity.Kind.PERFECTION
                ? ModBlocks.STATUE_OF_PERFECTION.get()
                : ModBlocks.STATUE_OF_ENDLESS_FORTUNE.get()));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DailyStatueBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.DAILY_STATUE.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) ->
                DailyStatueBlockEntity.serverTick(
                        tickLevel, pos, tickState, (DailyStatueBlockEntity) blockEntity);
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        return harvest(level, pos, player)
                ? ItemInteractionResult.sidedSuccess(false)
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        return harvest(level, pos, player)
                ? InteractionResult.CONSUME
                : InteractionResult.PASS;
    }

    private static boolean harvest(Level level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof DailyStatueBlockEntity statue)) {
            return false;
        }
        return UtilityDropHelper.tryHarvest(
                level, pos, player, statue::isReady, statue::harvestOne, 0);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock()) && !movedByPiston
                && level.getBlockEntity(pos) instanceof DailyStatueBlockEntity) {
            UtilityDropHelper.dropAutomationContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
