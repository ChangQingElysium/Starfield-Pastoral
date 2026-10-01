package com.stardew.craft.block.decor;

import com.stardew.craft.blockentity.DoubleSwingBlockEntity;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One removable, non-colliding structure; frame and seat outlines remain selectable. */
public final class DoubleSwingBlock extends MapDecorStaticBlock implements EntityBlock {
    public static final IntegerProperty CELL = IntegerProperty.create("cell", 0, 125);
    public static final int CENTER = 10;
    private final Map<Integer, VoxelShape> shapes = new ConcurrentHashMap<>();

    public DoubleSwingBlock(Properties properties) {
        super(properties, "stardewcraft:block/double_swing/collision");
        registerDefaultState(defaultBlockState().setValue(CELL, CENTER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CELL);
    }

    public static BlockPos localOffset(int cell) {
        return new BlockPos(cell % 7 - 3, cell / 21, cell / 7 % 3 - 1);
    }

    public static BlockPos rotateOffset(BlockPos offset, Direction facing) {
        int x = offset.getX(), y = offset.getY(), z = offset.getZ();
        return switch (facing) {
            case EAST -> new BlockPos(-z, y, x);
            case SOUTH -> new BlockPos(-x, y, -z);
            case WEST -> new BlockPos(z, y, -x);
            default -> offset;
        };
    }

    @Override
    protected Set<CellOffset> localOccupiedOffsets() {
        Set<CellOffset> cells = new LinkedHashSet<>();
        cells.add(new CellOffset(0, 0, 0));
        for (int y = 0; y < 5; y++) for (int x = -2; x <= 2; x++) for (int z = -1; z <= 1; z++)
            cells.add(new CellOffset(x, y, z));
        // Reserve the slight winter snow overhang before winter arrives.
        for (int x = -3; x <= 3; x++) cells.add(new CellOffset(x, 5, 0));
        return cells;
    }

    @Override
    protected BlockState extensionState(BlockState mainState, BlockPos offset) {
        Direction inverse = switch (mainState.getValue(FACING)) {
            case EAST -> Direction.WEST;
            case WEST -> Direction.EAST;
            default -> mainState.getValue(FACING);
        };
        BlockPos local = rotateOffset(offset, inverse);
        int cell = local.getX() + 3 + 7 * (local.getZ() + 1) + 21 * local.getY();
        return mainState.setValue(PART, Part.EXTENSION).setValue(CELL, cell);
    }

    @Override
    protected CellOffset findOffsetForExtension(BlockGetter level, BlockPos pos, BlockState state) {
        BlockPos offset = rotateOffset(localOffset(state.getValue(CELL)), state.getValue(FACING));
        if (offset.equals(BlockPos.ZERO)) return null;
        BlockState main = level.getBlockState(pos.subtract(offset));
        return main.is(this) && main.getValue(PART) == Part.MAIN && main.getValue(CELL) == CENTER
                && main.getValue(FACING) == state.getValue(FACING)
                ? new CellOffset(offset.getX(), offset.getY(), offset.getZ()) : null;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    private VoxelShape frameOutline(BlockState state) {
        int cell = state.getValue(CELL);
        Direction facing = state.getValue(FACING);
        return shapes.computeIfAbsent(cell * 4 + facing.get2DDataValue(), unused -> {
            BlockPos offset = localOffset(cell);
            // Clip first: rotating the entire structure for every cell is needlessly expensive.
            VoxelShape local = Shapes.join(canonicalShape()
                    .move(-offset.getX(), -offset.getY(), -offset.getZ()), Shapes.block(), BooleanOp.AND);
            return rotateShapeForFacing(local, facing);
        });
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape solid = frameOutline(state);
        BlockPos local = localOffset(state.getValue(CELL));
        VoxelShape seats = Shapes.or(Block.box(-16, 12, -14, 0, 27, 30), Block.box(16, 12, -14, 32, 27, 30));
        VoxelShape clipped = Shapes.join(seats.move(-local.getX(), -local.getY(), -local.getZ()), Shapes.block(), BooleanOp.AND);
        return Shapes.or(solid, rotateShapeForFacing(clipped, state.getValue(FACING)));
    }

    @Override
    public net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (player.isShiftKeyDown() || player.isPassenger()) return net.minecraft.world.InteractionResult.PASS;
        BlockPos main = findMainPos(level, pos, state);
        if (main == null) return net.minecraft.world.InteractionResult.PASS;
        Direction facing = state.getValue(FACING);
        var delta = hit.getLocation().subtract(net.minecraft.world.phys.Vec3.atBottomCenterOf(main));
        double side = switch (facing) { case EAST -> delta.z; case SOUTH -> -delta.x; case WEST -> -delta.z; default -> delta.x; };
        if (level.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
        var seat = com.stardew.craft.entity.seat.DoubleSwingSeatEntity.getOrCreate(
                (net.minecraft.server.level.ServerLevel) level, main, side < 0 ? 0 : 1, facing);
        if (seat == null) return net.minecraft.world.InteractionResult.PASS;
        if (seat.isVehicle()) return net.minecraft.world.InteractionResult.CONSUME;
        if (!player.startRiding(seat, false)) return net.minecraft.world.InteractionResult.PASS;
        seat.positionRider(player);
        player.setYRot(facing.toYRot()); player.setYBodyRot(facing.toYRot()); player.setYHeadRot(facing.toYRot());
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level instanceof net.minecraft.server.level.ServerLevel server) {
            BlockPos main = findMainPos(level, pos, state);
            if (main != null) com.stardew.craft.entity.seat.DoubleSwingSeatEntity.removeForPos(server, main);
        }
        super.onRemove(state, level, pos, next, moving);
    }

    @Override
    protected boolean canPlaceAtFacing(Level level, BlockPos pos, Direction facing, BlockPlaceContext context) {
        if (!super.canPlaceAtFacing(level, pos, facing, context)) return false;
        // Both anchored feet need support; the open center does not need a solid floor.
        for (int x : new int[]{-2, 2}) {
            BlockPos foot = pos.offset(rotateOffset(new BlockPos(x, 0, 0), facing));
            if (!level.getBlockState(foot.below()).isFaceSturdy(level, foot.below(), Direction.UP)) return false;
        }
        BlockState main = defaultBlockState().setValue(FACING, facing);
        for (CellOffset cell : occupiedOffsets(facing)) {
            BlockPos offset = new BlockPos(cell.dx(), cell.dy(), cell.dz());
            BlockPos target = pos.offset(offset);
            if (!level.hasChunkAt(target)) return false;
            if (context.getPlayer() != null && !context.getPlayer().mayUseItemAt(target,
                    context.getClickedFace(), context.getItemInHand())) return false;
            BlockState state = offset.equals(BlockPos.ZERO) ? main : extensionState(main, offset);
            if (!level.isUnobstructed(state, target, CollisionContext.empty())) return false;
        }
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.MAIN ? new DoubleSwingBlockEntity(pos, state) : null;
    }
}
