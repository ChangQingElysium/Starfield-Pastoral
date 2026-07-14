package com.stardew.craft.block.mine;

import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 电梯方块 - 用于在矿井楼层间传送
 * 有水平朝向属性，面向玩家放置
 */
public class ElevatorBlock extends Block {
    
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape[] SHAPES = ModelVoxelShapeCache.horizontalShapes("stardewcraft:block/elevator", Direction.NORTH);

    @SuppressWarnings("null")
    public ElevatorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(@SuppressWarnings("null") StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @SuppressWarnings("null")
    @Override
    public BlockState getStateForPlacement(@SuppressWarnings("null") BlockPlaceContext context) {
        // 放置时面向玩家（玩家看向的反方向）
        if (!context.getLevel().getBlockState(context.getClickedPos().above()).isAir()) {
            return null;
        }
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @SuppressWarnings("null")
    @Override
    public boolean canSurvive(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") LevelReader level, @SuppressWarnings("null") BlockPos pos) {
        return level.getBlockState(pos.above()).isAir();
    }

    @SuppressWarnings("null")
    @Override
    public VoxelShape getShape(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockGetter level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") CollisionContext context) {
        return SHAPES[ModelVoxelShapeCache.horizontalIndex(state.getValue(FACING))];
    }

    @SuppressWarnings("null")
    @Override
    public VoxelShape getCollisionShape(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockGetter level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") CollisionContext context) {
        return SHAPES[ModelVoxelShapeCache.horizontalIndex(state.getValue(FACING))];
    }

    @SuppressWarnings("null")
    @Override
    public VoxelShape getInteractionShape(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockGetter level, @SuppressWarnings("null") BlockPos pos) {
        return SHAPES[ModelVoxelShapeCache.horizontalIndex(state.getValue(FACING))];
    }

    @SuppressWarnings("null")
    @Override
    public BlockState rotate(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @SuppressWarnings("null")
    @Override
    public BlockState mirror(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    /**
     * 禁止生存模式破坏电梯
     */
    @SuppressWarnings("null")
    @Override
    public boolean onDestroyedByPlayer(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") Player player, boolean willHarvest, @SuppressWarnings("null") net.minecraft.world.level.material.FluidState fluid) {
        // 只有创造模式才能破坏
        if (!player.isCreative()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.stardewcraft.cannot_break_elevator"), true);
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    /**
     * 右键交互 - 打开电梯GUI
     */
    @SuppressWarnings("null")
    @Override
    protected InteractionResult useWithoutItem(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos,
                                               @SuppressWarnings("null")    Player player, @SuppressWarnings("null")    BlockHitResult hitResult) {
        if (level.dimension() != com.stardew.craft.core.ModMiningDimensions.STARDEW_MINING) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("stardewcraft.elevator.mine_only"));
            return InteractionResult.FAIL;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (containerId, playerInventory, playerEntity) ->
                    new com.stardew.craft.menu.ElevatorMenu(containerId, playerInventory),
                net.minecraft.network.chat.Component.translatable("container.stardew_craft.elevator")
            ));
        }

        return InteractionResult.CONSUME;
    }
}
