package com.stardew.craft.block.decor;

import com.stardew.craft.blockentity.PlaygroundBlockEntity;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
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

/** Independent native-model structures with per-cell collision and unambiguous ownership. */
public final class PlaygroundBlock extends MapDecorStaticBlock implements EntityBlock {
    public static final IntegerProperty CELL = IntegerProperty.create("cell", 0, 69);
    public static final int CENTER = 4;
    private final boolean slide;
    private final String modelName;
    private final Map<Integer, VoxelShape> shapes = new ConcurrentHashMap<>();

    public PlaygroundBlock(Properties properties, boolean slide) {
        this(properties, slide ? "playground_slide" : "climbing_frame");
    }

    public PlaygroundBlock(Properties properties, String modelName) {
        super(properties, "stardewcraft:block/" + modelName + "/collision");
        this.modelName = modelName;
        this.slide = modelName.equals("playground_slide");
        registerDefaultState(defaultBlockState().setValue(CELL, CENTER));
    }

    public String modelName() { return modelName; }
    public boolean isSlide() { return slide; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CELL);
    }

    public static BlockPos localOffset(int cell) {
        return new BlockPos(cell % 2, cell / 14, cell / 2 % 7 - 2);
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

    private static Direction inverse(Direction facing) {
        return switch (facing) { case EAST -> Direction.WEST; case WEST -> Direction.EAST; default -> facing; };
    }

    @Override
    protected Set<CellOffset> localOccupiedOffsets() {
        // Cleanup must remove the owner before its children, otherwise each child can drop an item.
        Set<CellOffset> cells = new LinkedHashSet<>();
        cells.add(new CellOffset(0, 0, 0));
        cells.addAll(super.localOccupiedOffsets());
        return cells;
    }

    @Override
    protected BlockState extensionState(BlockState main, BlockPos offset) {
        BlockPos local = rotateOffset(offset, inverse(main.getValue(FACING)));
        int cell = local.getX() + 2 * (local.getZ() + 2) + 14 * local.getY();
        return main.setValue(PART, Part.EXTENSION).setValue(CELL, cell);
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
        int cell = state.getValue(CELL);
        Direction facing = state.getValue(FACING);
        return shapes.computeIfAbsent(cell * 4 + facing.get2DDataValue(), unused -> {
            BlockPos offset = localOffset(cell);
            VoxelShape collision = modelName.equals("bird_spring_rider") ? Block.box(3, 0, 9, 13, 16, 21) : canonicalShape();
            VoxelShape clipped = Shapes.join(collision.move(-offset.getX(), -offset.getY(), -offset.getZ()),
                    Shapes.block(), BooleanOp.AND);
            return rotateShapeForFacing(clipped, facing);
        });
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!modelName.equals("bird_spring_rider")) return getCollisionShape(state, level, pos, context);
        BlockPos offset = localOffset(state.getValue(CELL));
        VoxelShape outline = Shapes.join(canonicalShape().move(-offset.getX(), -offset.getY(), -offset.getZ()), Shapes.block(), BooleanOp.AND);
        return rotateShapeForFacing(outline, state.getValue(FACING));
    }

    @Override
    public net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (!modelName.equals("bird_spring_rider") || player.isShiftKeyDown() || player.isPassenger())
            return net.minecraft.world.InteractionResult.PASS;
        BlockPos main = findMainPos(level, pos, state);
        if (main == null) return net.minecraft.world.InteractionResult.PASS;
        if (level.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
        var facing = state.getValue(FACING);
        var seat = com.stardew.craft.entity.seat.BirdSpringRiderSeatEntity.getOrCreate(
                (net.minecraft.server.level.ServerLevel) level, main, facing);
        if (seat == null) return net.minecraft.world.InteractionResult.PASS;
        if (seat.isVehicle()) return net.minecraft.world.InteractionResult.CONSUME;
        if (!player.startRiding(seat, false)) return net.minecraft.world.InteractionResult.PASS;
        seat.positionRider(player);
        player.setYRot(facing.toYRot()); player.setYBodyRot(facing.toYRot()); player.setYHeadRot(facing.toYRot());
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (modelName.equals("bird_spring_rider") && !state.is(next.getBlock())
                && level instanceof net.minecraft.server.level.ServerLevel server) {
            BlockPos main = findMainPos(level, pos, state);
            if (main != null) com.stardew.craft.entity.seat.BirdSpringRiderSeatEntity.removeForPos(server, main);
        }
        super.onRemove(state, level, pos, next, moving);
    }

    @Override
    protected boolean canPlaceAtFacing(Level level, BlockPos pos, Direction facing, BlockPlaceContext context) {
        if (!super.canPlaceAtFacing(level, pos, facing, context)) return false;
        BlockState main = defaultBlockState().setValue(FACING, facing);
        for (CellOffset cell : occupiedOffsets(facing)) {
            BlockPos offset = new BlockPos(cell.dx(), cell.dy(), cell.dz());
            BlockPos target = pos.offset(offset);
            if (!level.hasChunkAt(target)) return false;
            if (context.getPlayer() != null && !context.getPlayer().mayUseItemAt(target,
                    context.getClickedFace(), context.getItemInHand())) return false;
            BlockState state = offset.equals(BlockPos.ZERO) ? main : extensionState(main, offset);
            if (!level.isUnobstructed(state, target, CollisionContext.empty())) return false;
            if (cell.dy() == 0 && !level.getBlockState(target.below()).isFaceSturdy(level, target.below(), Direction.UP)) return false;
        }
        return true;
    }

    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        if (modelName.equals("bird_spring_rider")) return false;
        BlockPos main = findMainPos(level, pos, state);
        if (main == null) return false;
        double dx = entity.getX() - main.getX() - .5, dz = entity.getZ() - main.getZ() - .5;
        double x, z;
        switch (state.getValue(FACING)) {
            case EAST -> { x = dz; z = -dx; }
            case SOUTH -> { x = -dx; z = -dz; }
            case WEST -> { x = -dz; z = dx; }
            default -> { x = dx; z = dz; }
        }
        x = (x + .5) * 16; z = (z + .5) * 16;
        double y = (entity.getY() - main.getY()) * 16;
        if (x < 4 || x > 28 || y < 0 || y >= 48) return false;
        return slide ? Math.abs(z - (-2 + (y - 48) * Math.tan(Math.PI / 8))) <= 7
                : z >= 31 && z <= 41;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.MAIN ? new PlaygroundBlockEntity(pos, state) : null;
    }
}
