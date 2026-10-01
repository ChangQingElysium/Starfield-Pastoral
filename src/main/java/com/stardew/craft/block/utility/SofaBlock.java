package com.stardew.craft.block.utility;

import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.entity.seat.SofaSeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.InteractionHand;
import com.stardew.craft.port.PortBlockInteraction;

@SuppressWarnings("null")
public class SofaBlock extends Block implements PortBlockInteraction {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LEFT_CONNECTED = BooleanProperty.create("left_connected");
    public static final BooleanProperty RIGHT_CONNECTED = BooleanProperty.create("right_connected");
    public static final IntegerProperty COLOR = IntegerProperty.create("color", 0, WoodenChestColorPalette.size() - 1);

    private static final VoxelShape FALLBACK_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    private static final Map<String, VoxelShape> SHAPE_CACHE = new ConcurrentHashMap<>();

    public SofaBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(LEFT_CONNECTED, false)
            .setValue(RIGHT_CONNECTED, false)
            .setValue(COLOR, WoodenChestColorPalette.defaultColorIndex()));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LEFT_CONNECTED, RIGHT_CONNECTED, COLOR);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getModelShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getModelShape(state);
    }

    private static VoxelShape getModelShape(BlockState state) {
        String variant = "facing=" + state.getValue(FACING).getSerializedName()
            + ",left_connected=" + state.getValue(LEFT_CONNECTED)
            + ",right_connected=" + state.getValue(RIGHT_CONNECTED);
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return SHAPE_CACHE.computeIfAbsent(variant, key -> {
            VoxelShape shape = ModelVoxelShapeCache.variantShape(blockId, key);
            return shape.isEmpty() ? FALLBACK_SHAPE : shape;
        });
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        int color = WoodenChestColorPalette.defaultColorIndex();
        BlockState hitState = context.getLevel().getBlockState(context.getClickedPos());
        if (hitState.getBlock() instanceof SofaBlock) {
            color = hitState.getValue(COLOR);
        }
        BlockState placed = defaultBlockState().setValue(FACING, facing).setValue(COLOR, color);
        return updateConnections(context.getLevel(), context.getClickedPos(), placed);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level.isClientSide || state.is(oldState.getBlock())) {
            return;
        }

        BlockState updatedSelf = updateConnections(level, pos, state);
        if (updatedSelf != state) {
            level.setBlock(pos, updatedSelf, 3);
        }
        refreshNeighbors(level, pos);
        reorientIsolatedNeighbors(level, pos, updatedSelf.getValue(FACING));
    }

    @Override
    public BlockState updateShape(BlockState state,
                                     Direction direction,
                                     BlockState neighborState,
                                     LevelAccessor level,
                                     BlockPos pos,
                                     BlockPos neighborPos) {
        return updateConnections(level, pos, state);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    // PORT(1.20.1): replay the 1.21 useItemOn/useWithoutItem dispatch.
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return PortBlockInteraction.dispatch(this, state, level, pos, player, hand, hit);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (com.stardew.craft.npc.runtime.NpcSupportTarget.occupied((net.minecraft.server.level.ServerLevel)level,pos))
            return InteractionResult.CONSUME;
        SofaSeatEntity seat = SofaSeatEntity.getOrCreate((net.minecraft.server.level.ServerLevel) level, pos);
        if (seat == null) {
            return InteractionResult.PASS;
        }

        if (seat.isVehicle()) {
            return InteractionResult.CONSUME;
        }

        boolean mounted = player.startRiding(seat, false);
        if (!mounted) {
            return InteractionResult.PASS;
        }

        // Seat should place player at 6px above block base.
        Vec3 seatPos = seat.position();
        player.teleportTo(seatPos.x, seatPos.y, seatPos.z);
        player.setYBodyRot(state.getValue(FACING).toYRot() + 180.0F);
        player.setYRot(state.getValue(FACING).toYRot() + 180.0F);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            SofaSeatEntity.removeForPos((net.minecraft.server.level.ServerLevel) level, pos);
            refreshNeighbors(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private static BlockState updateConnections(LevelAccessor level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();

        boolean straightLeft = isSofaWithFacing(level.getBlockState(pos.relative(left)), facing);
        boolean straightRight = isSofaWithFacing(level.getBlockState(pos.relative(right)), facing);

        boolean sideCornerLeft = isSofaWithFacing(level.getBlockState(pos.relative(left)), left.getOpposite());
        boolean sideCornerRight = isSofaWithFacing(level.getBlockState(pos.relative(right)), right.getOpposite());

        boolean cornerLeft = hasLeftCornerConnection(level, pos, facing);
        boolean cornerRight = hasRightCornerConnection(level, pos, facing);

        boolean leftConnected = straightLeft || sideCornerLeft || (!straightRight && !sideCornerRight && cornerLeft);
        boolean rightConnected = straightRight || sideCornerRight || (!straightLeft && !sideCornerLeft && cornerRight);

        return state.setValue(LEFT_CONNECTED, leftConnected).setValue(RIGHT_CONNECTED, rightConnected);
    }

    private static boolean hasLeftCornerConnection(LevelAccessor level, BlockPos pos, Direction facing) {
        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();

        BlockState front = level.getBlockState(pos.relative(facing));
        if (isSofaWithFacing(front, left)) {
            return true;
        }

        BlockState back = level.getBlockState(pos.relative(facing.getOpposite()));
        return isSofaWithFacing(back, right);
    }

    private static boolean hasRightCornerConnection(LevelAccessor level, BlockPos pos, Direction facing) {
        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();

        BlockState front = level.getBlockState(pos.relative(facing));
        if (isSofaWithFacing(front, right)) {
            return true;
        }

        BlockState back = level.getBlockState(pos.relative(facing.getOpposite()));
        return isSofaWithFacing(back, left);
    }

    private static boolean isSofaWithFacing(BlockState state, Direction facing) {
        return state.getBlock() instanceof SofaBlock && state.getValue(FACING) == facing;
    }

    private static void refreshNeighbors(Level level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            refreshNeighbor(level, pos.relative(direction));
        }
    }

    private static void refreshNeighbor(Level level, BlockPos neighborPos) {
        BlockState neighborState = level.getBlockState(neighborPos);
        if (!(neighborState.getBlock() instanceof SofaBlock)) {
            return;
        }
        BlockState updated = updateConnections(level, neighborPos, neighborState);
        if (updated != neighborState) {
            level.setBlock(neighborPos, updated, 3);
        }
    }

    private static void reorientIsolatedNeighbors(Level level, BlockPos placedPos, Direction placedFacing) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = placedPos.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (!(neighborState.getBlock() instanceof SofaBlock)) {
                continue;
            }
            if (countAdjacentSofas(level, neighborPos) != 1) {
                continue;
            }

            Direction currentFacing = neighborState.getValue(FACING);
            if (currentFacing == placedFacing) {
                continue;
            }

            BlockState rotated = neighborState.setValue(FACING, placedFacing);
            rotated = updateConnections(level, neighborPos, rotated);
            level.setBlock(neighborPos, rotated, 3);
            refreshNeighbors(level, neighborPos);
        }
    }

    private static int countAdjacentSofas(LevelAccessor level, BlockPos pos) {
        int count = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(pos.relative(direction)).getBlock() instanceof SofaBlock) {
                count++;
            }
        }
        return count;
    }
}
