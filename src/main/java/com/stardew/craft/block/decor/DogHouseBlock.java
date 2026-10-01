package com.stardew.craft.block.decor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One doghouse and bowl; each occupied cell owns only its local collision and mesh. */
public final class DogHouseBlock extends MapDecorStaticBlock {
    private static final List<BlockPos> CELLS = List.of(BlockPos.ZERO, new BlockPos(1, 0, 0), new BlockPos(1, 0, 1));
    public static final IntegerProperty CELL = IntegerProperty.create("cell", 0, 2);
    private static final VoxelShape COLLISION = Shapes.or(
            Block.box(16, 0, 0, 32, 16, 20), Block.box(4, 0, 0, 12, 3, 8));
    private final Map<Integer, VoxelShape> shapes = new ConcurrentHashMap<>();

    public DogHouseBlock(Properties properties) {
        super(properties, "stardewcraft:block/decor/dog_house/spring/full");
        registerDefaultState(defaultBlockState().setValue(CELL, 0));
    }

    public static BlockPos localOffset(int cell) { return CELLS.get(cell); }

    public static BlockPos rotateOffset(BlockPos p, Direction facing) {
        return switch (facing) {
            case EAST -> new BlockPos(-p.getZ(), p.getY(), p.getX());
            case SOUTH -> new BlockPos(-p.getX(), p.getY(), -p.getZ());
            case WEST -> new BlockPos(p.getZ(), p.getY(), -p.getX());
            default -> p;
        };
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CELL);
    }

    @Override protected VoxelShape canonicalShape() { return COLLISION; }

    @Override protected BlockState extensionState(BlockState main, BlockPos offset) {
        Direction inverse = switch (main.getValue(FACING)) {
            case EAST -> Direction.WEST; case WEST -> Direction.EAST; default -> main.getValue(FACING);
        };
        return main.setValue(PART, Part.EXTENSION).setValue(CELL, CELLS.indexOf(rotateOffset(offset, inverse)));
    }

    @Override protected CellOffset findOffsetForExtension(BlockGetter level, BlockPos pos, BlockState state) {
        BlockPos offset = rotateOffset(localOffset(state.getValue(CELL)), state.getValue(FACING));
        if (offset.equals(BlockPos.ZERO)) return null;
        BlockState main = level.getBlockState(pos.subtract(offset));
        return main.is(this) && main.getValue(PART) == Part.MAIN && main.getValue(CELL) == 0
                && main.getValue(FACING) == state.getValue(FACING)
                ? new CellOffset(offset.getX(), offset.getY(), offset.getZ()) : null;
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int cell = state.getValue(CELL);
        Direction facing = state.getValue(FACING);
        return shapes.computeIfAbsent(cell * 4 + facing.get2DDataValue(), ignored -> {
            BlockPos p = localOffset(cell);
            VoxelShape local = Shapes.join(COLLISION.move(-p.getX(), -p.getY(), -p.getZ()), Shapes.block(), BooleanOp.AND);
            return rotateShapeForFacing(local, facing);
        });
    }

    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return super.canSurvive(state, level, pos)
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override protected boolean canPlaceAtFacing(Level level, BlockPos pos, Direction facing, BlockPlaceContext context) {
        if (!super.canPlaceAtFacing(level, pos, facing, context)) return false;
        BlockState main = defaultBlockState().setValue(FACING, facing);
        for (int cell = 0; cell < CELLS.size(); cell++) {
            BlockPos target = pos.offset(rotateOffset(localOffset(cell), facing));
            if (!level.hasChunkAt(target) || !level.getBlockState(target.below()).isFaceSturdy(level, target.below(), Direction.UP)) return false;
            if (context.getPlayer() != null && !context.getPlayer().mayUseItemAt(target, context.getClickedFace(), context.getItemInHand())) return false;
            BlockState part = main.setValue(CELL, cell).setValue(PART, cell == 0 ? Part.MAIN : Part.EXTENSION);
            if (!level.isUnobstructed(part, target, CollisionContext.empty())) return false;
        }
        return true;
    }
}
