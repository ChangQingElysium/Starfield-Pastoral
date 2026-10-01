package com.stardew.craft.block.decor;

import com.stardew.craft.blockentity.IceCreamStandBlockEntity;
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

/** Four-season stand with stable ownership and an actual air aisle for the vendor. */
public final class IceCreamStandBlock extends MapDecorStaticBlock implements EntityBlock {
    public static final IntegerProperty CELL = IntegerProperty.create("cell", 0, 47);
    private static volatile int clientSeason;
    private final Map<Integer, VoxelShape> shapes = new ConcurrentHashMap<>();
    private static final VoxelShape COUNTERS = Shapes.or(
            Block.box(0, 0, 0, 32, 18, 48), Block.box(48, 0, 0, 64, 18, 48),
            Block.box(32, 0, 32, 48, 18, 48));
    private static final VoxelShape CLOSED_COUNTER = Shapes.or(COUNTERS,
            Block.box(2, 18, 2, 30, 32, 6), Block.box(3, 18, 25, 26, 28, 43),
            Block.box(24, 18, 17, 30, 28, 24), Block.box(51, 18, 12, 61, 27, 22));
    private static final VoxelShape[] COLLISIONS = {
        CLOSED_COUNTER,
        Shapes.or(COUNTERS, Block.box(19, 18, 19, 25, 20, 25), Block.box(21, 20, 21, 23, 56, 23),
                Block.box(0, 46, 0, 44, 58, 44), Block.box(50, 18, 6, 63, 20, 20),
                Block.box(50, 18, 27, 63, 20, 41), Block.box(24, 18, 30, 29, 23, 35)),
        Shapes.or(CLOSED_COUNTER, Block.box(6, 18, 8, 16, 29, 18)),
        Shapes.or(Block.box(0, 0, 0, 32, 21, 48), Block.box(48, 0, 0, 64, 21, 48),
                Block.box(32, 0, 32, 48, 21, 48), Block.box(20, 21, 20, 28, 28, 28))
    };
    private static final VoxelShape[] OUTLINES = {
        Shapes.or(COLLISIONS[0], Block.box(50, 26, 11, 62, 40, 23)),
        Shapes.or(COLLISIONS[1], Block.box(10, 32, 21, 21, 46, 25)),
        Shapes.or(COLLISIONS[2], Block.box(50, 26, 11, 62, 40, 23)),
        COLLISIONS[3]
    };

    public IceCreamStandBlock(Properties properties) {
        super(properties, "stardewcraft:block/ice_cream_stand/spring/empty");
        registerDefaultState(defaultBlockState().setValue(CELL, 0));
    }
    public static void updateClientSeason(int season) { clientSeason = com.stardew.craft.port.PortJava.clamp(season, 0, 3); }
    private static int season(BlockGetter level) {
        return level instanceof ServerLevel ? com.stardew.craft.port.PortJava.clamp(StardewTimeManager.get().getCurrentSeason(), 0, 3) : clientSeason;
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(CELL);
    }
    public static BlockPos localOffset(int cell) { return new BlockPos(cell % 4, cell / 12, cell / 4 % 3); }
    public static boolean ownsCell(int cell) {
        BlockPos p = localOffset(cell);
        if (p.getY() < 2 && p.getX() == 2 && p.getZ() < 2) return false;
        if (p.getY() == 3 && p.getX() == 3) return false;
        return !(p.getY() == 2 && p.getX() == 3 && p.getZ() == 2);
    }
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
        for (int cell = 0; cell < 48; cell++) {
            if (!ownsCell(cell)) continue;
            BlockPos p = localOffset(cell); cells.add(new CellOffset(p.getX(), p.getY(), p.getZ()));
        }
        return cells;
    }
    @Override protected BlockState extensionState(BlockState main, BlockPos offset) {
        Direction inverse = switch (main.getValue(FACING)) {
            case EAST -> Direction.WEST; case WEST -> Direction.EAST; default -> main.getValue(FACING);
        };
        BlockPos p = rotateOffset(offset, inverse);
        return main.setValue(PART, Part.EXTENSION).setValue(CELL, p.getX() + 4 * p.getZ() + 12 * p.getY());
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
        int key = (((season * 48 + cell) * 4 + facing.get2DDataValue()) * 2) + (outline ? 1 : 0);
        return shapes.computeIfAbsent(key, ignored -> {
            BlockPos p = localOffset(cell);
            VoxelShape whole = (outline ? OUTLINES : COLLISIONS)[season];
            return rotateShapeForFacing(Shapes.join(whole.move(-p.getX(), -p.getY(), -p.getZ()), Shapes.block(), BooleanOp.AND), facing);
        });
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return shape(state, level, true); }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return shape(state, level, false); }
    public AABB renderBounds(BlockState state, BlockPos pos) {
        return rotateShapeForFacing(Block.box(-1, 0, -1, 64, 58, 49), state.getValue(FACING)).bounds().move(pos);
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
        return state.getValue(PART) == Part.MAIN ? new IceCreamStandBlockEntity(pos, state) : null;
    }
}
