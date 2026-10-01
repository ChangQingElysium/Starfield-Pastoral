package com.stardew.craft.block.mine;

import com.stardew.craft.port.PortItemData;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;
import javax.annotation.Nullable;

/** One independently supported wall cell, with persistent variation and vertical end caps. */
public final class MineWallDecorationBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 3);
    public static final BooleanProperty ABOVE = BooleanProperty.create("above");
    public static final BooleanProperty BELOW = BooleanProperty.create("below");

    public MineWallDecorationBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(VARIANT, 0)
                .setValue(ABOVE, false).setValue(BELOW, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, VARIANT, ABOVE, BELOW);
    }

    @Nullable
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Integer fixed = PortItemData.getOrDefault(context.getItemInHand(), DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY).get(VARIANT);
        int variant = fixed == null ? context.getLevel().getRandom().nextInt(4) : fixed;
        // Extending an existing strip from its top/bottom retains the same wall orientation.
        BlockState clicked = context.getLevel().getBlockState(context.getClickedPos().relative(context.getClickedFace().getOpposite()));
        Direction preferred = !context.getClickedFace().getAxis().isHorizontal() && clicked.is(this)
                ? clicked.getValue(FACING) : context.getClickedFace();
        if (preferred.getAxis().isHorizontal()) {
            BlockState state = defaultBlockState().setValue(FACING, preferred).setValue(VARIANT, variant);
            if (state.canSurvive(context.getLevel(), context.getClickedPos())) return connections(state, context.getLevel(), context.getClickedPos());
        }
        for (Direction direction : context.getNearestLookingDirections()) {
            if (!direction.getAxis().isHorizontal()) continue;
            BlockState state = defaultBlockState().setValue(FACING, direction.getOpposite()).setValue(VARIANT, variant);
            if (state.canSurvive(context.getLevel(), context.getClickedPos())) return connections(state, context.getLevel(), context.getClickedPos());
        }
        return null;
    }

    private BlockState connections(BlockState state, BlockGetter level, BlockPos pos) {
        return state.setValue(ABOVE, joins(state, level.getBlockState(pos.above())))
                .setValue(BELOW, joins(state, level.getBlockState(pos.below())));
    }

    private boolean joins(BlockState state, BlockState other) {
        return other.is(this) && other.getValue(FACING) == state.getValue(FACING);
    }

    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos wall = pos.relative(facing.getOpposite());
        return level.getBlockState(wall).isFaceSturdy(level, wall, facing);
    }

    @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.canSurvive(level, pos) ? connections(state, level, pos) : Blocks.AIR.defaultBlockState();
    }

    @Override public ItemStack getCloneItemStack(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(this);
        PortItemData.set(stack, DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(VARIANT, state));
        return stack;
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> box(0, 0, 0, 16, 16, 2);
            case EAST -> box(0, 0, 0, 2, 16, 16);
            case WEST -> box(14, 0, 0, 16, 16, 16);
            default -> box(0, 0, 14, 16, 16, 16);
        };
    }

    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
