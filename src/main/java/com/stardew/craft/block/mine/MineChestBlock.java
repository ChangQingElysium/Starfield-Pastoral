package com.stardew.craft.block.mine;

import com.stardew.craft.blockentity.MineChestBlockEntity;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * 矿井宝箱方块 — 不可破坏，右键打开 per-player 独立库存。
 * 原生箱体／箱盖模型，客户端沿后铰轴连续开合。
 */
@SuppressWarnings("null")
public class MineChestBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty SPECIAL = BooleanProperty.create("special");
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    private static final VoxelShape[] SHAPES = ModelVoxelShapeCache.horizontalShapes(
            "stardewcraft:block/mine/reward_chest/closed", Direction.NORTH);

    private static final VoxelShape[] SPECIAL_SHAPES = ModelVoxelShapeCache.horizontalShapes(
            "stardewcraft:block/mine/desert_special_chest/closed", Direction.NORTH);

    public MineChestBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false).setValue(SPECIAL, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, SPECIAL);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        var saved = context.getItemInHand().getOrDefault(com.stardew.craft.port.net.minecraft.core.component.DataComponents.BLOCK_STATE,
                com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties.EMPTY);
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(SPECIAL, Boolean.TRUE.equals(saved.get(SPECIAL)));
    }

    @Override
    public net.minecraft.world.item.ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level,
            BlockPos pos, BlockState state) {
        var stack = new net.minecraft.world.item.ItemStack(this);
        stack.set(com.stardew.craft.port.net.minecraft.core.component.DataComponents.BLOCK_STATE,
                com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties.EMPTY.with(SPECIAL, state));
        return stack;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(SPECIAL) ? SPECIAL_SHAPES : SHAPES)[ModelVoxelShapeCache.horizontalIndex(state.getValue(FACING))];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(SPECIAL) ? SPECIAL_SHAPES : SHAPES)[ModelVoxelShapeCache.horizontalIndex(state.getValue(FACING))];
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MineChestBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide || type != com.stardew.craft.blockentity.ModBlockEntities.MINE_CHEST.get()) return null;
        return (world, blockPos, blockState, entity) -> MineChestBlockEntity.clientTick(
                world, blockPos, blockState, (MineChestBlockEntity) entity);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state,
                                               Level level,
                                               BlockPos pos,
                                               Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MineChestBlockEntity chest)) {
            return InteractionResult.PASS;
        }

        player.openMenu(chest);
        return InteractionResult.CONSUME;
    }

    // 矿井宝箱不掉落任何东西
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
