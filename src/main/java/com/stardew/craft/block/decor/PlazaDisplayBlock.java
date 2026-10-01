package com.stardew.craft.block.decor;

import com.stardew.craft.blockentity.PlazaDisplayBlockEntity;
import com.stardew.craft.time.StardewTimeManager;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Stable 2x3x4 ownership across four entirely different seasonal arrangements. */
public final class PlazaDisplayBlock extends MapDecorStaticBlock implements EntityBlock {
    public static final IntegerProperty CELL = IntegerProperty.create("cell", 0, 23);
    private static volatile int clientSeason;
    private final Map<Integer, VoxelShape> shapes = new ConcurrentHashMap<>();
    private static final VoxelShape[] COLLISIONS = {
        Shapes.or(Block.box(0, 0, 14, 20, 20, 34), Block.box(17, 0, 34, 31, 16, 48), Block.box(19, 0, 2, 31, 11, 14)),
        Shapes.or(Block.box(1, 0, 14, 19, 12, 32), Block.box(17, 0, 33, 31, 10, 47), Block.box(19, 0, 1, 31, 9, 13)),
        Shapes.or(Block.box(0, 0, 28, 18, 25, 48), Block.box(14, 0, 10, 32, 17, 28),
                Block.box(0, 0, 1, 14, 10, 15), Block.box(18, 17, 12, 30, 29, 24)),
        Shapes.or(Block.box(12, 0, 20, 20, 16, 28), Block.box(4, 6, 12, 28, 25, 36),
                Block.box(8, 25, 16, 24, 41, 32), Block.box(12, 41, 20, 20, 53, 28))
    };
    private static final VoxelShape[] OUTLINES = {
        Shapes.or(COLLISIONS[0], Block.box(0, 15, 12, 22, 42, 36), Block.box(16, 12, 33, 32, 35, 48), Block.box(19, 8, 2, 31, 24, 14)),
        Shapes.or(COLLISIONS[1], Block.box(0, 7, 10, 23, 55, 36), Block.box(14, 5, 30, 32, 41, 48), Block.box(17, 4, 0, 32, 34, 18)),
        COLLISIONS[2],
        Shapes.or(COLLISIONS[3], Block.box(0, 6, 8, 32, 25, 40), Block.box(4, 25, 12, 28, 41, 36),
                Block.box(10, 41, 18, 22, 54, 30), Block.box(13, 52, 22, 20, 59, 25))
    };

    public PlazaDisplayBlock(Properties properties) {
        super(properties, "stardewcraft:block/plaza_display/spring/empty");
        registerDefaultState(defaultBlockState().setValue(CELL, 0));
    }
    public static void updateClientSeason(int season) { clientSeason = com.stardew.craft.port.PortJava.clamp(season, 0, 3); }
    private static int season(BlockGetter level) {
        return level instanceof ServerLevel ? com.stardew.craft.port.PortJava.clamp(StardewTimeManager.get().getCurrentSeason(), 0, 3) : clientSeason;
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(CELL);
    }
    public static BlockPos localOffset(int cell) { return new BlockPos(cell % 2, cell / 6, cell / 2 % 3); }
    public static BlockPos rotateOffset(BlockPos p, Direction facing) {
        return switch (facing) {
            case EAST -> new BlockPos(-p.getZ(), p.getY(), p.getX());
            case SOUTH -> new BlockPos(-p.getX(), p.getY(), -p.getZ());
            case WEST -> new BlockPos(p.getZ(), p.getY(), -p.getX());
            default -> p;
        };
    }
    @Override protected Set<CellOffset> localOccupiedOffsets() {
        var cells = new LinkedHashSet<CellOffset>();
        for (int cell = 0; cell < 24; cell++) {
            BlockPos p = localOffset(cell); cells.add(new CellOffset(p.getX(), p.getY(), p.getZ()));
        }
        return cells;
    }
    @Override protected BlockState extensionState(BlockState main, BlockPos offset) {
        Direction inverse = switch (main.getValue(FACING)) {
            case EAST -> Direction.WEST; case WEST -> Direction.EAST; default -> main.getValue(FACING);
        };
        BlockPos p = rotateOffset(offset, inverse);
        return main.setValue(PART, Part.EXTENSION).setValue(CELL, p.getX() + 2 * p.getZ() + 6 * p.getY());
    }
    @Override protected CellOffset findOffsetForExtension(BlockGetter level, BlockPos pos, BlockState state) {
        BlockPos p = rotateOffset(localOffset(state.getValue(CELL)), state.getValue(FACING));
        if (p.equals(BlockPos.ZERO)) return null;
        BlockState main = level.getBlockState(pos.subtract(p));
        return main.is(this) && main.getValue(PART) == Part.MAIN && main.getValue(CELL) == 0
                && main.getValue(FACING) == state.getValue(FACING) ? new CellOffset(p.getX(), p.getY(), p.getZ()) : null;
    }
    private VoxelShape shape(BlockState state, BlockGetter level, boolean outline) {
        int cell = state.getValue(CELL), season = season(level); Direction facing = state.getValue(FACING);
        int key = (((season * 24 + cell) * 4 + facing.get2DDataValue()) * 2) + (outline ? 1 : 0);
        return shapes.computeIfAbsent(key, ignored -> {
            BlockPos p = localOffset(cell);
            VoxelShape whole = (outline ? OUTLINES : COLLISIONS)[season];
            return rotateShapeForFacing(Shapes.join(whole.move(-p.getX(), -p.getY(), -p.getZ()), Shapes.block(), BooleanOp.AND), facing);
        });
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return shape(state, level, true); }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return shape(state, level, false); }
    public AABB renderBounds(BlockState state, BlockPos pos) {
        return rotateShapeForFacing(Block.box(0, 0, 0, 32, 64, 48), state.getValue(FACING)).bounds().move(pos);
    }
    @Override protected boolean canPlaceAtFacing(Level level, BlockPos pos, Direction facing, BlockPlaceContext context) {
        if (!super.canPlaceAtFacing(level, pos, facing, context)) return false;
        BlockState main = defaultBlockState().setValue(FACING, facing);
        for (CellOffset cell : occupiedOffsets(facing)) {
            BlockPos offset = new BlockPos(cell.dx(), cell.dy(), cell.dz()), target = pos.offset(offset);
            if (!level.hasChunkAt(target)) return false;
            if (context.getPlayer() != null && !context.getPlayer().mayUseItemAt(target, context.getClickedFace(), context.getItemInHand())) return false;
            BlockState state = offset.equals(BlockPos.ZERO) ? main : extensionState(main, offset);
            if (!level.isUnobstructed(state, target, CollisionContext.empty())) return false;
            if (cell.dy() == 0 && !level.getBlockState(target.below()).isFaceSturdy(level, target.below(), Direction.UP)) return false;
        }
        return true;
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.MAIN ? new PlazaDisplayBlockEntity(pos, state) : null;
    }
}
