package com.stardew.craft.block.mine;

import com.stardew.craft.port.PortItemData;
import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Independently ceiling-supported foliage; neighbors join only at the same height. */
public final class MineCanopyBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 1);
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    private static final VoxelShape OUTLINE = box(0, 7, 0, 16, 16, 16);

    public MineCanopyBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(VARIANT, 0)
                .setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, VARIANT, NORTH, EAST, SOUTH, WEST);
    }

    public static BooleanProperty connection(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> throw new IllegalArgumentException("Canopy connections are horizontal");
        };
    }

    private BlockState connections(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL)
            state = state.setValue(connection(direction), level.getBlockState(pos.relative(direction)).is(this));
        return state;
    }

    @Nullable
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Integer fixed = PortItemData.getOrDefault(context.getItemInHand(), DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY).get(VARIANT);
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(VARIANT, fixed == null ? context.getLevel().getRandom().nextInt(2) : fixed);
        return state.canSurvive(context.getLevel(), context.getClickedPos())
                && context.getLevel().getFluidState(context.getClickedPos()).isEmpty()
                ? connections(state, context.getLevel(), context.getClickedPos()) : null;
    }

    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos ceiling = pos.above();
        return level.getBlockState(ceiling).isFaceSturdy(level, ceiling, Direction.DOWN);
    }

    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        // Structure templates can place foliage before the ceiling in the same tick.
        level.scheduleTick(pos, this, 1);
        return connections(state, level, pos);
    }

    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        } else {
            BlockState connected = connections(state, level, pos);
            if (connected != state) level.setBlock(pos, connected, 3);
        }
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE;
    }

    @Override public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(this);
        PortItemData.set(stack, DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(VARIANT, state));
        return stack;
    }

    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        BlockState result = state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        for (Direction direction : Direction.Plane.HORIZONTAL)
            result = result.setValue(connection(rotation.rotate(direction)), state.getValue(connection(direction)));
        return result;
    }

    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        BlockState result = state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
        for (Direction direction : Direction.Plane.HORIZONTAL)
            result = result.setValue(connection(mirror.mirror(direction)), state.getValue(connection(direction)));
        return result;
    }
}
