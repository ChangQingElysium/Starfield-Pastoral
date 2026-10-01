package com.stardew.craft.block.decor;

import com.stardew.craft.port.PortEntities;
import com.stardew.craft.port.PortItemData;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.entity.seat.SofaSeatEntity;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One removable seat per cell; a reserved upper cell protects the tilted backrest. */
public final class ParkBenchBlock extends MapDecorStaticBlock {
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 2);
    private static final Map<String, VoxelShape> SHAPES = new ConcurrentHashMap<>();

    public ParkBenchBlock(Properties properties) {
        super(properties, "stardewcraft:block/decor/park_bench/spring/single_0");
        registerDefaultState(defaultBlockState().setValue(LEFT, false).setValue(RIGHT, false).setValue(VARIANT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LEFT, RIGHT, VARIANT);
    }

    public static String modelPart(BlockState state) {
        if (state.getValue(LEFT)) return state.getValue(RIGHT) ? "middle" : "right";
        return state.getValue(RIGHT) ? "left" : "single";
    }

    private static VoxelShape localShape(String part) {
        // End arm boards have a one-pixel visual overhang. Their collision ends at the
        // seat cell boundary so they do not reserve the next connectable seat's cell.
        return Shapes.join(ModelVoxelShapeCache.shapeFromModelId(
                "stardewcraft:block/decor/park_bench/spring/" + part + "_0"),
                Block.box(0, 0, 0, 16, 32, 16), BooleanOp.AND).optimize();
    }

    @Override
    protected VoxelShape canonicalShape() { return localShape("single"); }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockPos mainPos = findMainPos(level, pos, state);
        if (mainPos == null) return Shapes.empty();
        BlockState main = state.getValue(PART) == Part.MAIN ? state : level.getBlockState(mainPos);
        String part = modelPart(main);
        Direction facing = main.getValue(FACING);
        return SHAPES.computeIfAbsent(part + facing.getName(), unused -> rotateShapeForFacing(localShape(part), facing))
                .move(0, mainPos.getY() - pos.getY(), 0);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) return null;
        Integer fixed = PortItemData.getOrDefault(context.getItemInHand(), DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY).get(VARIANT);
        int variant = fixed == null ? (context.getLevel().isClientSide ? 0 : context.getLevel().random.nextInt(3)) : fixed;
        return withConnections(state.setValue(VARIANT, variant), context.getLevel(), context.getClickedPos());
    }

    private BlockState withConnections(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        return state.setValue(LEFT, connects(level, pos.relative(facing.getCounterClockWise()), facing))
                .setValue(RIGHT, connects(level, pos.relative(facing.getClockWise()), facing));
    }

    private boolean connects(LevelReader level, BlockPos pos, Direction facing) {
        BlockState neighbor = level.getBlockState(pos);
        return neighbor.is(this) && neighbor.getValue(PART) == Part.MAIN && neighbor.getValue(FACING) == facing;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BlockState result = super.updateShape(state, direction, neighbor, level, pos, neighborPos);
        if (!result.is(this)) return result;
        if (state.getValue(PART) == Part.MAIN && direction.getAxis().isHorizontal())
            return withConnections(result, level, pos);
        if (state.getValue(PART) == Part.EXTENSION && direction == Direction.DOWN
                && neighbor.is(this) && neighbor.getValue(PART) == Part.MAIN)
            return neighbor.setValue(PART, Part.EXTENSION);
        return result;
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)))
                .setValue(LEFT, state.getValue(RIGHT)).setValue(RIGHT, state.getValue(LEFT));
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown() || player.isPassenger()) return InteractionResult.PASS;
        BlockPos mainPos = findMainPos(level, pos, state);
        if (mainPos == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        // Vanilla subtracts the 0.6 vehicle attachment; humanoid hips are 12/16 above the feet.
        double seatOffset = 10.0 / 16.0 - 12.0 / 16.0 + PortEntities.PLAYER_VEHICLE_ATTACHMENT.y;
        SofaSeatEntity seat = SofaSeatEntity.getOrCreate((ServerLevel) level, mainPos, seatOffset);
        if (seat == null) return InteractionResult.PASS;
        if (seat.isVehicle()) return InteractionResult.CONSUME;
        if (!player.startRiding(seat, false)) return InteractionResult.PASS;
        seat.positionRider(player);
        float yaw = level.getBlockState(mainPos).getValue(FACING).toYRot();
        player.setYRot(yaw);
        player.setYBodyRot(yaw);
        player.setYHeadRot(yaw);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level instanceof ServerLevel server) {
            BlockPos mainPos = findMainPos(level, pos, state);
            if (mainPos != null) SofaSeatEntity.removeForPos(server, mainPos);
        }
        super.onRemove(state, level, pos, next, moving);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) { return false; }
}
