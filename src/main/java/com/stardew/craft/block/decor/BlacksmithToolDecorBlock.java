package com.stardew.craft.block.decor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

/** Ground decorations. The shovel's upper grip touches the wall behind its upper cell. */
public final class BlacksmithToolDecorBlock extends MapDecorStaticBlock {
    public static final IntegerProperty CELL = IntegerProperty.create("cell", 0, 2);
    private static final List<BlockPos> SHOVEL_CELLS = List.of(BlockPos.ZERO, new BlockPos(0, 1, 0));
    private static final List<BlockPos> PLANK_CELLS = List.of(BlockPos.ZERO, new BlockPos(-1, 0, 0), new BlockPos(1, 0, 0));
    private final boolean shovel;
    private final Map<Integer, VoxelShape> shapes = new ConcurrentHashMap<>();

    public BlacksmithToolDecorBlock(Properties properties, boolean shovel) {
        super(properties, "stardewcraft:block/decor/blacksmith_tools/" + (shovel ? "leaning_shovel" : "old_plank"), true);
        this.shovel = shovel;
        registerDefaultState(defaultBlockState().setValue(CELL, 0));
    }
    private List<BlockPos> cells() { return shovel ? SHOVEL_CELLS : PLANK_CELLS; }
    private static BlockPos rotate(BlockPos p, Direction facing) {
        return switch (facing) {
            case EAST -> new BlockPos(-p.getZ(), p.getY(), p.getX());
            case SOUTH -> new BlockPos(-p.getX(), p.getY(), -p.getZ());
            case WEST -> new BlockPos(p.getZ(), p.getY(), -p.getX());
            default -> p;
        };
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(CELL);
    }
    @Override protected Set<CellOffset> localOccupiedOffsets() {
        var result = new LinkedHashSet<CellOffset>();
        for (BlockPos p : cells()) result.add(new CellOffset(p.getX(), p.getY(), p.getZ()));
        return result;
    }
    @Override protected BlockState extensionState(BlockState main, BlockPos offset) {
        Direction inverse = switch (main.getValue(FACING)) {
            case EAST -> Direction.WEST; case WEST -> Direction.EAST; default -> main.getValue(FACING);
        };
        return main.setValue(PART, Part.EXTENSION).setValue(CELL, cells().indexOf(rotate(offset, inverse)));
    }
    @Override protected CellOffset findOffsetForExtension(BlockGetter level, BlockPos pos, BlockState state) {
        int cell = state.getValue(CELL);
        if (cell <= 0 || cell >= cells().size()) return null;
        BlockPos offset = rotate(cells().get(cell), state.getValue(FACING));
        BlockState main = level.getBlockState(pos.subtract(offset));
        return main.is(this) && main.getValue(PART) == Part.MAIN && main.getValue(CELL) == 0
                && main.getValue(FACING) == state.getValue(FACING)
                ? new CellOffset(offset.getX(), offset.getY(), offset.getZ()) : null;
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int cell = state.getValue(CELL); Direction facing = state.getValue(FACING);
        if (cell >= cells().size()) return Shapes.empty();
        return shapes.computeIfAbsent(cell * 4 + facing.get2DDataValue(), ignored -> {
            BlockPos p = cells().get(cell);
            return rotateShapeForFacing(Shapes.join(canonicalShape().move(-p.getX(), -p.getY(), -p.getZ()),
                    Shapes.block(), BooleanOp.AND), facing);
        });
    }
    private boolean supported(LevelReader level, BlockPos main, Direction facing) {
        for (BlockPos cell : cells()) if (cell.getY() == 0) {
            BlockPos floor = main.offset(rotate(cell, facing)).below();
            if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return false;
        }
        if (!shovel) return true;
        BlockPos wall = main.above().relative(facing.getOpposite());
        return level.getBlockState(wall).isFaceSturdy(level, wall, facing);
    }
    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (!super.canSurvive(state, level, pos)) return false;
        BlockPos main = findMainPos(level, pos, state);
        return main != null && supported(level, main, state.getValue(FACING));
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        Direction facing = shovel && clicked.getAxis().isHorizontal() ? clicked : context.getHorizontalDirection().getOpposite();
        return canPlaceAtFacing(context.getLevel(), context.getClickedPos(), facing, context)
                ? defaultBlockState().setValue(FACING, facing) : null;
    }
    @Override protected boolean canPlaceAtFacing(Level level, BlockPos pos, Direction facing, BlockPlaceContext context) {
        if (!super.canPlaceAtFacing(level, pos, facing, context) || !supported(level, pos, facing)) return false;
        BlockState main = defaultBlockState().setValue(FACING, facing);
        for (CellOffset cell : occupiedOffsets(facing)) {
            BlockPos offset = new BlockPos(cell.dx(), cell.dy(), cell.dz()), target = pos.offset(offset);
            if (!level.hasChunkAt(target)) return false;
            if (context.getPlayer() != null && !context.getPlayer().mayUseItemAt(target, context.getClickedFace(), context.getItemInHand())) return false;
            BlockState state = offset.equals(BlockPos.ZERO) ? main : extensionState(main, offset);
            if (!level.isUnobstructed(state, target, CollisionContext.empty())) return false;
        }
        return true;
    }
}
