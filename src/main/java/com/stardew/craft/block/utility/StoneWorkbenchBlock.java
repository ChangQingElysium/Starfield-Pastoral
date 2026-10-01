package com.stardew.craft.block.utility;

import com.stardew.craft.workbench.WorkbenchType;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.entity.player.Player;
import com.stardew.craft.port.PortBlockInteraction;

@SuppressWarnings("null")
public class StoneWorkbenchBlock extends Block implements PortBlockInteraction {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public StoneWorkbenchBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ModelVoxelShapeCache.variantShape("stardewcraft:stone_workbench",
                "facing=" + state.getValue(FACING).getSerializedName());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    // PORT(1.20.1): replay the 1.21 useItemOn/useWithoutItem dispatch.
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return PortBlockInteraction.dispatch(this, state, level, pos, player, hand, hit);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state,
            Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return ItemInteractionResult.sidedSuccess(true);
        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp,
                new com.stardew.craft.network.payload.OpenWorkbenchPayload(WorkbenchType.STONE));
        }
        return ItemInteractionResult.sidedSuccess(false);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level,
            BlockPos pos, net.minecraft.world.entity.player.Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp,
                new com.stardew.craft.network.payload.OpenWorkbenchPayload(WorkbenchType.STONE));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }
}
