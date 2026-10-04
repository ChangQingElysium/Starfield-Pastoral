package com.stardew.craft.block.decor;

import com.stardew.craft.blockentity.ParkedVehicleBlockEntity;
import com.stardew.craft.desert.DesertBusService;
import com.stardew.craft.desert.DesertConstants;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
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

/** One placed vehicle, explicit owner cells, and broad collision boxes. */
public final class ParkedVehicleBlock extends MapDecorStaticBlock implements EntityBlock {
    public static final IntegerProperty CELL = IntegerProperty.create("cell", 0, 199);
    public static final int CENTER = 16;
    private final boolean bus;
    private final boolean jojaTruck;
    private final Map<Integer, VoxelShape> shapes = new ConcurrentHashMap<>();
    private VoxelShape collision;

    public ParkedVehicleBlock(Properties properties, boolean bus) {
        this(properties, bus ? "bus" : "mayor_pickup");
    }
    public ParkedVehicleBlock(Properties properties, String assetId) {
        super(properties, "stardewcraft:block/vehicles/" + assetId + "/empty");
        if (!Set.of("bus", "mayor_pickup", "joja_truck").contains(assetId)) throw new IllegalArgumentException(assetId);
        this.bus = assetId.equals("bus");
        this.jojaTruck = assetId.equals("joja_truck");
        registerDefaultState(defaultBlockState().setValue(CELL, CENTER));
    }
    public boolean isBus() { return bus; }
    public boolean isJojaTruck() { return jojaTruck; }
    public String assetId() { return bus ? "bus" : jojaTruck ? "joja_truck" : "mayor_pickup"; }
    public int longitudinalOrigin() { return bus ? 64 : jojaTruck ? 48 : 32; }
    public int widthCenter() { return bus || jojaTruck ? 24 : 20; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(CELL);
    }
    public static BlockPos localOffset(int cell) {
        // Preserve existing bus/pickup cell encodings; extra columns only serve wide vehicles.
        if (cell >= 120) return new BlockPos((cell - 120) % 2 == 0 ? -2 : 2,
                (cell - 120) / 20, (cell - 120) / 2 % 10 - 5);
        return new BlockPos(cell % 3 - 1, cell / 30, cell / 3 % 10 - 5);
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
        cells.add(new CellOffset(0, 0, 0));
        // Reserve the visual envelope, including winter snow and the bus's protruding lamps.
        for (int y = 0; y < (bus || jojaTruck ? 4 : 3); y++)
            for (int z = bus ? -5 : jojaTruck ? -3 : -2; z <= (jojaTruck ? 4 : 3); z++)
                for (int x = jojaTruck ? -2 : -1; x <= (jojaTruck ? 2 : 1); x++) cells.add(new CellOffset(x, y, z));
        return cells;
    }
    @Override protected BlockState extensionState(BlockState main, BlockPos offset) {
        Direction inverse = switch (main.getValue(FACING)) {
            case EAST -> Direction.WEST; case WEST -> Direction.EAST; default -> main.getValue(FACING);
        };
        BlockPos p = rotateOffset(offset, inverse);
        int cell = Math.abs(p.getX()) == 2
                ? 120 + (p.getX() == -2 ? 0 : 1) + 2 * (p.getZ() + 5) + 20 * p.getY()
                : p.getX() + 1 + 3 * (p.getZ() + 5) + 30 * p.getY();
        return main.setValue(PART, Part.EXTENSION).setValue(CELL, cell);
    }
    @Override protected CellOffset findOffsetForExtension(BlockGetter level, BlockPos pos, BlockState state) {
        BlockPos offset = rotateOffset(localOffset(state.getValue(CELL)), state.getValue(FACING));
        if (offset.equals(BlockPos.ZERO)) return null;
        BlockState main = level.getBlockState(pos.subtract(offset));
        return main.is(this) && main.getValue(PART) == Part.MAIN && main.getValue(CELL) == CENTER
                && main.getValue(FACING) == state.getValue(FACING)
                ? new CellOffset(offset.getX(), offset.getY(), offset.getZ()) : null;
    }
    private VoxelShape rawBox(double x, double y, double z, double X, double Y, double Z) {
        return Block.box(8 + widthCenter() - Z, y, x - longitudinalOrigin(),
                8 + widthCenter() - z, Y, X - longitudinalOrigin());
    }
    @Override protected VoxelShape canonicalShape() {
        if (collision != null) return collision;
        VoxelShape shape;
        if (bus) {
            shape = Shapes.or(rawBox(-1.25, 12, 1, 128, 47, 47), rawBox(2, 47, 3, 126, 58, 45),
                    rawBox(4, 6, 6, 124, 16, 42));
            for (int x : new int[]{40, 104}) for (int z : new int[]{0, 43})
                shape = Shapes.or(shape, rawBox(x - 8, 0, z, x + 8, 16, z + 5));
        } else if (jojaTruck) {
            shape = Shapes.or(rawBox(0, 10, 2, 26, 32, 46), rawBox(24, 8, 1, 46, 43, 47),
                    rawBox(46, 16, 0, 112, 60, 48), rawBox(4, 8, 6, 110, 16, 42));
            for (int x : new int[]{16, 94}) for (int z : new int[]{0, 43})
                shape = Shapes.or(shape, rawBox(x - 8, 0, z, x + 8, 16, z + 5));
        } else {
            shape = Shapes.or(rawBox(0, 8, 2, 29, 23, 38), rawBox(29, 10, 3, 50, 39, 37),
                    rawBox(50, 8, 2, 82, 24, 38));
            for (int x : new int[]{18, 64}) for (int z : new int[]{1, 35})
                shape = Shapes.or(shape, rawBox(x - 7, 0, z, x + 7, 14, z + 4));
        }
        return collision = shape.optimize();
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int cell = state.getValue(CELL); Direction facing = state.getValue(FACING);
        return shapes.computeIfAbsent(cell * 4 + facing.get2DDataValue(), ignored -> {
            BlockPos p = localOffset(cell);
            VoxelShape clipped = Shapes.join(canonicalShape().move(-p.getX(), -p.getY(), -p.getZ()), Shapes.block(), BooleanOp.AND);
            return rotateShapeForFacing(clipped, facing);
        });
    }
    public AABB renderBounds(BlockState state, BlockPos pos) {
        VoxelShape envelope = rawBox(bus ? -1.25 : 0, 0, jojaTruck ? -3 : 0,
                bus ? 128 : jojaTruck ? 116 : 82, bus ? 58 : jojaTruck ? 61 : 39,
                jojaTruck ? 51 : bus ? 48 : 40);
        return rotateShapeForFacing(envelope, state.getValue(FACING)).bounds().move(pos);
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
        }
        // Require support beneath all four wheel columns.
        for (int x : bus ? new int[]{40, 104} : jojaTruck ? new int[]{16, 94} : new int[]{18, 64}) for (int side : new int[]{-1, 1}) {
            BlockPos wheel = new BlockPos(side, -1, Math.floorDiv(x - longitudinalOrigin(), 16));
            BlockPos floor = pos.offset(rotateOffset(wheel, facing));
            if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return false;
        }
        return true;
    }
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        BlockPos main = findMainPos(level, pos, state);
        if (!bus || main == null || !DesertConstants.isInDesertRegion(level.dimension(), main)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) DesertBusService.beginReturnRide(serverPlayer);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.MAIN ? new ParkedVehicleBlockEntity(pos, state) : null;
    }
}
