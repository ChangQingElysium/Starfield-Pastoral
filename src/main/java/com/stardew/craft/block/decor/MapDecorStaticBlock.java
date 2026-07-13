package com.stardew.craft.block.decor;

import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("null")
public class MapDecorStaticBlock extends Block {
    private static final ThreadLocal<Integer> DROP_SUPPRESSION_DEPTH = ThreadLocal.withInitial(() -> 0);

    public static void runWithDropsSuppressed(Runnable action) {
        int previous = DROP_SUPPRESSION_DEPTH.get();
        DROP_SUPPRESSION_DEPTH.set(previous + 1);
        try {
            action.run();
        } finally {
            if (previous == 0) {
                DROP_SUPPRESSION_DEPTH.remove();
            } else {
                DROP_SUPPRESSION_DEPTH.set(previous);
            }
        }
    }

    private static boolean dropsSuppressed() {
        return DROP_SUPPRESSION_DEPTH.get() > 0;
    }

    public enum Part implements StringRepresentable {
        MAIN("main"),
        EXTENSION("extension");

        private final String name;

        Part(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final Map<String, VoxelShape> SHAPE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, VoxelShape> BOX_SHAPE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, VoxelShape> ORIENTED_SHAPE_CACHE = new ConcurrentHashMap<>();
    // Tolerance of 1 pixel (1/16 block) so sub-pixel model overflows
    // don't claim extra extension cells.
    private static final double EPS = 1.0 / 16.0;

    private final String modelId;
    private final boolean boxCollision;
    /** Pre-set per-facing box shapes; null means compute from model. */
    private final Map<Direction, VoxelShape[]> presetBoxShapes;
    private volatile Set<CellOffset> localOccupiedOffsets;
    private final Map<Direction, Set<CellOffset>> occupiedOffsetsByFacing = new ConcurrentHashMap<>();

    public MapDecorStaticBlock(Properties properties, String modelId) {
        this(properties, modelId, false);
    }

    public MapDecorStaticBlock(Properties properties, String modelId, boolean boxCollision) {
        super(properties);
        this.modelId = modelId;
        this.boxCollision = boxCollision;
        this.presetBoxShapes = null;
        registerDefaultState(stateDefinition.any().setValue(PART, Part.MAIN).setValue(FACING, Direction.NORTH));
    }

    /**
     * Constructor with a pre-set bounding box (in pixels, model-space, NORTH facing).
     * The box covers minX..maxX, minY..maxY, minZ..maxZ in pixel coordinates (0-16 = 1 block).
     * Automatically sliced into per-cell box shapes for all 4 facings.
     */
    public MapDecorStaticBlock(Properties properties, String modelId,
                               double minX, double minY, double minZ,
                               double maxX, double maxY, double maxZ) {
        super(properties);
        this.modelId = modelId;
        this.boxCollision = true;
        this.presetBoxShapes = buildPresetBoxShapes(minX, minY, minZ, maxX, maxY, maxZ);
        registerDefaultState(stateDefinition.any().setValue(PART, Part.MAIN).setValue(FACING, Direction.NORTH));
    }

    // Compatibility constructor to keep existing registrations unchanged.
    public MapDecorStaticBlock(Properties properties, String modelId, int extensionOffsetX, int extensionOffsetY, int extensionOffsetZ) {
        this(properties, modelId, false);
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, FACING);
    }

    @Override
    public BlockState rotate(@Nonnull BlockState state, @Nonnull Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @SuppressWarnings("deprecation")
    @Override
    public BlockState mirror(@Nonnull BlockState state, @Nonnull Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        if (presetBoxShapes != null) {
            return resolvePresetShape(state, level, pos);
        }
        if (boxCollision) {
            return resolvePartBoxShape(state, level, pos, state.getValue(PART));
        }
        return resolvePartShape(state, level, pos, state.getValue(PART));
    }

    @Override
    public VoxelShape getCollisionShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        if (presetBoxShapes != null) {
            return resolvePresetShape(state, level, pos);
        }
        if (boxCollision) {
            return resolvePartBoxShape(state, level, pos, state.getValue(PART));
        }
        return resolvePartShape(state, level, pos, state.getValue(PART));
    }

    @Override
    public boolean propagatesSkylightDown(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos) {
        return true;
    }

    @Override
    public int getLightBlock(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos) {
        return 0;
    }

    @Override
    public float getShadeBrightness(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos) {
        return 1.0F;
    }

    /**
     * Looks up the pre-computed box shape for the given cell (facing + offset).
     */
    private VoxelShape resolvePresetShape(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
        Part part = state.getValue(PART);
        CellOffset offset;
        if (part == Part.MAIN) {
            offset = CellOffset.ZERO;
        } else {
            offset = findOffsetForExtension(level, pos, state);
            if (offset == null) {
                return Shapes.empty();
            }
            // offset is in world-space; convert back to model-space (NORTH) for lookup
            offset = offset.unrotateY(facing);
        }
        VoxelShape[] shapes = presetBoxShapes.get(facing);
        if (shapes == null) return Shapes.empty();
        int idx = cellIndex(offset);
        if (idx < 0 || idx >= shapes.length) return Shapes.empty();
        return shapes[idx];
    }

    /**
     * Returns a cached box-simplified collision shape (AABB bounds of the full shape).
     */
    private VoxelShape resolvePartBoxShape(BlockState state, BlockGetter level, BlockPos pos, Part part) {
        Direction facing = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
        CellOffset offset;
        if (part == Part.MAIN) {
            offset = CellOffset.ZERO;
        } else {
            offset = findOffsetForExtension(level, pos, state);
            if (offset == null) {
                return Shapes.empty();
            }
        }
        String key = modelId + "#box#" + facing.getSerializedName() + "#" + offset.dx + "," + offset.dy + "," + offset.dz;
        return BOX_SHAPE_CACHE.computeIfAbsent(key, unused -> {
            VoxelShape shape = shapeForPart(facing, offset);
            if (shape.isEmpty()) return Shapes.empty();
            var bounds = shape.bounds();
            return Shapes.box(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ);
        });
    }

    private VoxelShape resolvePartShape(BlockState state, BlockGetter level, BlockPos pos, Part part) {
        Direction facing = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
        if (part == Part.MAIN) {
            return shapeForPart(facing, CellOffset.ZERO);
        }
        CellOffset offset = findOffsetForExtension(level, pos, state);
        if (offset == null) {
            return Shapes.empty();
        }
        return shapeForPart(facing, offset);
    }

    private VoxelShape shapeForPart(Direction facing, CellOffset offsetFromMain) {
        String key = modelId + "#" + facing.getSerializedName() + "#part#" + offsetFromMain.dx + "," + offsetFromMain.dy + "," + offsetFromMain.dz;
        return SHAPE_CACHE.computeIfAbsent(
            key,
            unused -> shiftShape(orientedShape(facing), -offsetFromMain.dx, -offsetFromMain.dy, -offsetFromMain.dz)
        );
    }

    private VoxelShape orientedShape(Direction facing) {
        String key = modelId + "#" + facing.getSerializedName();
        return ORIENTED_SHAPE_CACHE.computeIfAbsent(key, unused -> {
            VoxelShape compactShape = compactDecorShape(facing);
            if (compactShape != null) {
                return compactShape;
            }
            if (modelId.contains("bonsai")) {
                if (modelId.contains("wall")) {
                    return Block.box(0, 0, 0, 16, 16, 16);
                }
                // 盆栽 (bonsai_6_x) 也就是在 common 里的盆栽系列，碰撞箱只需1格高(1*1*1)
                if (modelId.contains("bonsai_6_")) {
                    return Block.box(0, 0, 0, 16, 16, 16);
                }
                // 原版盆景系列 (长1宽1高2)
                return Block.box(0, 0, 0, 16, 32, 16); 
            }
            return rotateShapeY(ModelVoxelShapeCache.shapeFromModelId(modelId), facing);
        });
    }

    @Nullable
    private VoxelShape compactDecorShape(Direction facing) {
        VoxelShape shape = null;
        if (modelId.contains("bonsai_5_wall")) {
            shape = Block.box(4.0D, 3.0D, 12.0D, 12.0D, 14.0D, 16.0D);
        } else if (modelId.contains("bonsai_6_") || modelId.endsWith("/bonsai_6")) {
            shape = Block.box(4.0D, 0.0D, 4.0D, 12.0D, 8.0D, 12.0D);
        } else if (modelId.endsWith("/empty_terracotta_pot")) {
            shape = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 8.0D, 13.0D);
        } else if (modelId.endsWith("/long_potted_plant")) {
            shape = Block.box(-6.0D, 0.0D, 4.0D, 22.0D, 8.0D, 12.0D);
        } else if (modelId.contains("luau_soup_pot_proxy")) {
            shape = Block.box(-25.66D, 0.0D, -5.26D, 41.66D, 20.1D, 21.26D);
        } else if (modelId.contains("luau_totem_proxy")) {
            shape = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 40.0D, 16.0D);
        } else if (modelId.contains("winter_star_tree_proxy")) {
            shape = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 52.0D, 16.0D);
        } else if (modelId.endsWith("/fair_grave_stone")) {
            shape = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);
        } else if (modelId.contains("uncertainty_statue_proxy")) {
            shape = Block.box(-13.0D, 0.0D, 3.0D, 29.0D, 22.0D, 15.0D);
        }
        return shape == null ? null : rotateShapeForFacing(shape, facing);
    }

    private static VoxelShape rotateShapeY(VoxelShape shape, Direction facing) {
        return rotateShapeForFacing(shape, facing);
    }

    protected static VoxelShape rotateShapeForFacing(VoxelShape shape, Direction facing) {
        if (facing == Direction.NORTH || shape.isEmpty()) {
            return shape;
        }
        final VoxelShape[] out = new VoxelShape[] { Shapes.empty() };
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
            Aabb rotated = rotateAabbY(minX, minY, minZ, maxX, maxY, maxZ, facing);
            out[0] = Shapes.or(out[0], Shapes.box(rotated.minX, rotated.minY, rotated.minZ, rotated.maxX, rotated.maxY, rotated.maxZ));
        });
        return out[0].optimize();
    }

    private static Aabb rotateAabbY(double minX, double minY, double minZ,
                                    double maxX, double maxY, double maxZ,
                                    Direction facing) {
        return switch (facing) {
            case EAST -> new Aabb(1.0 - maxZ, minY, minX, 1.0 - minZ, maxY, maxX);
            case SOUTH -> new Aabb(1.0 - maxX, minY, 1.0 - maxZ, 1.0 - minX, maxY, 1.0 - minZ);
            case WEST -> new Aabb(minZ, minY, 1.0 - maxX, maxZ, maxY, 1.0 - minX);
            default -> new Aabb(minX, minY, minZ, maxX, maxY, maxZ);
        };
    }

    private static VoxelShape shiftShape(VoxelShape shape, int dx, int dy, int dz) {
        if (shape.isEmpty()) {
            return Shapes.empty();
        }
        final VoxelShape[] out = new VoxelShape[] { Shapes.empty() };
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
            out[0] = Shapes.or(
                out[0],
                Shapes.box(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz)
            );
        });
        return out[0].optimize();
    }

    protected Set<CellOffset> localOccupiedOffsets() {
        Set<CellOffset> cached = localOccupiedOffsets;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (localOccupiedOffsets != null) {
                return localOccupiedOffsets;
            }
            if (presetCellOffsets != null) {
                localOccupiedOffsets = new LinkedHashSet<>(presetCellOffsets);
                return localOccupiedOffsets;
            }
            Set<CellOffset> discovered = new LinkedHashSet<>();
            // For bonsai blocks, use the same canonical shape as orientedShape() so that
            // overflowing decorative geometry in the model does not cause EXTENSION blocks
            // to be spuriously placed in neighbouring cells (which would block block-placement
            // next to the bonsai and leave orphaned extensions after removal).
            VoxelShape shape;
            VoxelShape compactShape = compactDecorShape(Direction.NORTH);
            if (compactShape != null) {
                shape = compactShape;
            } else if (modelId.contains("bonsai")) {
                if (modelId.contains("wall") || modelId.contains("bonsai_6_")) {
                    shape = Block.box(0, 0, 0, 16, 16, 16); // 1×1×1
                } else {
                    shape = Block.box(0, 0, 0, 16, 32, 16); // 1×2×1 tall bonsai
                }
            } else {
                shape = ModelVoxelShapeCache.shapeFromModelId(modelId);
            }
            shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
                int minCX = minOccupiedCell(minX);
                int minCY = minOccupiedCell(minY);
                int minCZ = minOccupiedCell(minZ);
                int maxCX = maxOccupiedCell(maxX);
                int maxCY = maxOccupiedCell(maxY);
                int maxCZ = maxOccupiedCell(maxZ);
                if (maxCX < minCX) {
                    minCX = maxCX = centerOccupiedCell(minX, maxX);
                }
                if (maxCY < minCY) {
                    minCY = maxCY = centerOccupiedCell(minY, maxY);
                }
                if (maxCZ < minCZ) {
                    minCZ = maxCZ = centerOccupiedCell(minZ, maxZ);
                }
                for (int x = minCX; x <= maxCX; x++) {
                    for (int y = minCY; y <= maxCY; y++) {
                        for (int z = minCZ; z <= maxCZ; z++) {
                            discovered.add(new CellOffset(x, y, z));
                        }
                    }
                }
            });

            if (!discovered.contains(CellOffset.ZERO)) {
                discovered.add(CellOffset.ZERO);
            }
            localOccupiedOffsets = discovered;
            return discovered;
        }
    }

    private static int minOccupiedCell(double min) {
        return (int) Math.floor(min + EPS);
    }

    private static int maxOccupiedCell(double max) {
        return (int) Math.ceil(max - EPS) - 1;
    }

    private static int centerOccupiedCell(double min, double max) {
        return (int) Math.floor((min + max) * 0.5D);
    }

    protected Set<CellOffset> occupiedOffsets(Direction facing) {
        return occupiedOffsetsByFacing.computeIfAbsent(facing, unused -> {
            Set<CellOffset> rotated = new LinkedHashSet<>();
            for (CellOffset offset : localOccupiedOffsets()) {
                rotated.add(offset.rotateY(facing));
            }
            if (!rotated.contains(CellOffset.ZERO)) {
                rotated.add(CellOffset.ZERO);
            }
            return rotated;
        });
    }

    private boolean hasExtensions() {
        return localOccupiedOffsets().size() > 1;
    }

    protected CellOffset findOffsetForExtension(BlockGetter level, BlockPos pos, BlockState extensionState) {
        Direction facing = extensionState.getValue(FACING);
        for (CellOffset offset : occupiedOffsets(facing)) {
            if (offset.isZero()) {
                continue;
            }
            BlockPos candidateMainPos = pos.offset(-offset.dx, -offset.dy, -offset.dz);
            BlockState candidate = level.getBlockState(candidateMainPos);
            if (candidate.is(this) && candidate.getValue(PART) == Part.MAIN && candidate.getValue(FACING) == facing) {
                return offset;
            }
        }
        return null;
    }

    public BlockPos findMainPos(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.getValue(PART) == Part.MAIN) {
            return pos;
        }
        CellOffset offset = findOffsetForExtension(level, pos, state);
        return offset == null ? null : pos.offset(-offset.dx, -offset.dy, -offset.dz);
    }

    /**
     * 返回此装饰方块（按当前 FACING）相对 MAIN 格的水平 extension 方向。
     * 仅取第一个非零、且 dy==0 的偏移并返回单位 {@link Direction}；
     * 如果没有水平 extension（单格方块或仅纵向 extension）则返回 {@code null}。
     */
    public Direction findHorizontalExtensionDirection(Direction facing) {
        for (CellOffset offset : occupiedOffsets(facing)) {
            if (offset.dy != 0) continue;
            if (offset.dx == 0 && offset.dz == 0) continue;
            if (offset.dx == 1 && offset.dz == 0) return Direction.EAST;
            if (offset.dx == -1 && offset.dz == 0) return Direction.WEST;
            if (offset.dx == 0 && offset.dz == 1) return Direction.SOUTH;
            if (offset.dx == 0 && offset.dz == -1) return Direction.NORTH;
        }
        return null;
    }

    /**
     * 返回此装饰方块（按当前 FACING）所有相对 MAIN 格的「水平 extension 单位方向」集合。
     * 用于判断 1×N、N×1、N×M 等形状。仅返回 |dx|+|dz|==1 且 dy==0 的格子。
     * 例如：1×2 床返回 {SOUTH}；2×2 床返回 {SOUTH, WEST}（顺序按迭代顺序）。
     */
    public java.util.List<Direction> findAllHorizontalExtensionDirections(Direction facing) {
        java.util.List<Direction> dirs = new java.util.ArrayList<>(2);
        for (CellOffset offset : occupiedOffsets(facing)) {
            if (offset.dy != 0) continue;
            if (Math.abs(offset.dx) + Math.abs(offset.dz) != 1) continue;
            Direction d = null;
            if (offset.dx == 1) d = Direction.EAST;
            else if (offset.dx == -1) d = Direction.WEST;
            else if (offset.dz == 1) d = Direction.SOUTH;
            else if (offset.dz == -1) d = Direction.NORTH;
            if (d != null && !dirs.contains(d)) {
                dirs.add(d);
            }
        }
        return dirs;
    }

    protected boolean canPlaceAtFacing(Level level, BlockPos pos, Direction facing, BlockPlaceContext context) {
        for (CellOffset offset : occupiedOffsets(facing)) {
            if (offset.isZero()) {
                continue;
            }
            BlockPos extensionPos = pos.offset(offset.dx, offset.dy, offset.dz);
            if (!level.getWorldBorder().isWithinBounds(extensionPos)) {
                return false;
            }
            if (!level.getBlockState(extensionPos).canBeReplaced(context)) {
                return false;
            }
        }
        return true;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (!canPlaceAtFacing(level, pos, facing, context)) {
            return null;
        }
        return defaultBlockState().setValue(PART, Part.MAIN).setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(@Nonnull Level level,
                            @Nonnull BlockPos pos,
                            @Nonnull BlockState state,
                            @Nullable net.minecraft.world.entity.LivingEntity placer,
                            @Nonnull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !hasExtensions() || state.getValue(PART) != Part.MAIN) {
            return;
        }
        Direction facing = state.getValue(FACING);
        for (CellOffset offset : occupiedOffsets(facing)) {
            if (offset.isZero()) {
                continue;
            }
            BlockPos extensionPos = pos.offset(offset.dx, offset.dy, offset.dz);
            level.setBlock(extensionPos, state.setValue(PART, Part.EXTENSION), 3);
        }
    }

    @Override
    protected List<ItemStack> getDrops(@Nonnull BlockState state, @Nonnull LootParams.Builder params) {
        if (state.getValue(PART) == Part.EXTENSION) {
            return List.of();
        }
        return List.of(new ItemStack(this));
    }

    /**
     * 禁止水流/流体替换装饰方块。
     * 默认的 canBeReplaced 对非完整碰撞箱方块返回 true，导致水流能冲掉家具。
     */
    @Override
    protected boolean canBeReplaced(@Nonnull BlockState state, @Nonnull net.minecraft.world.level.material.Fluid fluid) {
        return false;
    }

    @Override
    protected boolean canSurvive(@Nonnull BlockState state, @Nonnull LevelReader level, @Nonnull BlockPos pos) {
        if (state.getValue(PART) == Part.MAIN) {
            return true;
        }
        return findOffsetForExtension(level, pos, state) != null;
    }

    @Override
    protected BlockState updateShape(@Nonnull BlockState state,
                                     @Nonnull net.minecraft.core.Direction direction,
                                     @Nonnull BlockState neighborState,
                                     @Nonnull net.minecraft.world.level.LevelAccessor level,
                                     @Nonnull BlockPos pos,
                                     @Nonnull BlockPos neighborPos) {
        return state.canSurvive(level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    public void onRemove(@Nonnull BlockState state,
                         @Nonnull Level level,
                         @Nonnull BlockPos pos,
                         @Nonnull BlockState newState,
                         boolean isMoving) {
        if (!state.is(newState.getBlock()) && hasExtensions()) {
            BlockPos mainPos = findMainPos(level, pos, state);
            if (mainPos != null) {
                // 安全网：EXTENSION 被非玩家方式移除时（如爆炸等），掉落物品
                // （水流已被 canBeReplaced 拦截，但保留此逻辑以防万一）
                if (!dropsSuppressed() && !level.isClientSide && state.getValue(PART) == Part.EXTENSION
                        && level.getBlockState(mainPos).is(this)) {
                    popResource(level, mainPos, new ItemStack(this));
                }
                Direction facing = mainFacingForCleanup(level, mainPos, state);
                for (CellOffset offset : occupiedOffsets(facing)) {
                    BlockPos target = mainPos.offset(offset.dx, offset.dy, offset.dz);
                    if (target.equals(pos)) {
                        continue;
                    }
                    BlockState targetState = level.getBlockState(target);
                    if (targetState.is(this)) {
                        level.removeBlock(target, false);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public BlockState playerWillDestroy(@Nonnull Level level,
                                        @Nonnull BlockPos pos,
                                        @Nonnull BlockState state,
                                        @Nonnull net.minecraft.world.entity.player.Player player) {
        if (!level.isClientSide && hasExtensions() && state.getValue(PART) == Part.EXTENSION) {
            BlockPos mainPos = findMainPos(level, pos, state);
            if (mainPos != null) {
                if (!player.isCreative()) {
                    popResource(level, mainPos, new ItemStack(this));
                }
                // 关键：先清掉 MAIN，让 MAIN 的 onRemove 级联删除所有 EXTENSION。
                // 此时各 EXTENSION 的 onRemove 调用 findMainPos 会返回 null，从而不会触发安全网 popResource，
                // 避免每个 extension 各自再掉一份物品。
                BlockState mainState = level.getBlockState(mainPos);
                if (mainState.is(this)) {
                    level.setBlock(mainPos, Blocks.AIR.defaultBlockState(), 35);
                }
                // 兜底：MAIN 级联应已清掉所有格子；如还有残留则强制清理（不会再触发掉落，因为 MAIN 已不在）。
                Direction facing = mainFacingForCleanup(level, mainPos, state);
                for (CellOffset offset : occupiedOffsets(facing)) {
                    BlockPos target = mainPos.offset(offset.dx, offset.dy, offset.dz);
                    if (level.getBlockState(target).is(this)) {
                        level.setBlock(target, Blocks.AIR.defaultBlockState(), 35);
                    }
                }
                return state;
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    private Direction mainFacingForCleanup(BlockGetter level, BlockPos mainPos, BlockState fallbackState) {
        BlockState mainState = level.getBlockState(mainPos);
        if (mainState.is(this) && mainState.hasProperty(FACING)) {
            return mainState.getValue(FACING);
        }
        return fallbackState.hasProperty(FACING) ? fallbackState.getValue(FACING) : Direction.NORTH;
    }

    protected record CellOffset(int dx, int dy, int dz) {
        static final CellOffset ZERO = new CellOffset(0, 0, 0);

        private boolean isZero() {
            return dx == 0 && dy == 0 && dz == 0;
        }

        private CellOffset rotateY(Direction facing) {
            return switch (facing) {
                case EAST -> new CellOffset(-dz, dy, dx);
                case SOUTH -> new CellOffset(-dx, dy, -dz);
                case WEST -> new CellOffset(dz, dy, -dx);
                default -> this;
            };
        }

        CellOffset unrotateY(Direction facing) {
            return switch (facing) {
                case EAST -> new CellOffset(dz, dy, -dx);
                case SOUTH -> new CellOffset(-dx, dy, -dz);
                case WEST -> new CellOffset(-dz, dy, dx);
                default -> this;
            };
        }
    }

    private record Aabb(double minX, double minY, double minZ,
                        double maxX, double maxY, double maxZ) {
    }

    // ── Preset box shape utilities ──

    /**
     * Pre-compute per-cell box shapes for all 4 horizontal facings.
     * Input coordinates are in pixel space (0-16 = 1 block), NORTH facing.
     */
    private Map<Direction, VoxelShape[]> buildPresetBoxShapes(
            double pMinX, double pMinY, double pMinZ,
            double pMaxX, double pMaxY, double pMaxZ) {

        // Determine which cells this bounding box covers (in NORTH orientation)
        int cellMinX = (int) Math.floor(pMinX / 16.0);
        int cellMinY = (int) Math.floor(pMinY / 16.0);
        int cellMinZ = (int) Math.floor(pMinZ / 16.0);
        int cellMaxX = (int) Math.floor((pMaxX - 0.001) / 16.0);
        int cellMaxY = (int) Math.floor((pMaxY - 0.001) / 16.0);
        int cellMaxZ = (int) Math.floor((pMaxZ - 0.001) / 16.0);

        // Collect all cell offsets (for NORTH, main at 0,0,0)
        List<CellOffset> offsets = new ArrayList<>();
        for (int y = cellMinY; y <= cellMaxY; y++) {
            for (int z = cellMinZ; z <= cellMaxZ; z++) {
                for (int x = cellMinX; x <= cellMaxX; x++) {
                    offsets.add(new CellOffset(x, y, z));
                }
            }
        }

        Map<Direction, VoxelShape[]> result = new ConcurrentHashMap<>();
        for (Direction facing : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            VoxelShape[] shapes = new VoxelShape[offsets.size()];
            for (int i = 0; i < offsets.size(); i++) {
                CellOffset off = offsets.get(i);
                // Clip the bounding box to this cell (in pixel space), then normalize to 0-1
                double cMinX = Math.max(pMinX - off.dx * 16.0, 0) / 16.0;
                double cMinY = Math.max(pMinY - off.dy * 16.0, 0) / 16.0;
                double cMinZ = Math.max(pMinZ - off.dz * 16.0, 0) / 16.0;
                double cMaxX = Math.min(pMaxX - off.dx * 16.0, 16.0) / 16.0;
                double cMaxY = Math.min(pMaxY - off.dy * 16.0, 16.0) / 16.0;
                double cMaxZ = Math.min(pMaxZ - off.dz * 16.0, 16.0) / 16.0;

                if (cMaxX <= cMinX || cMaxY <= cMinY || cMaxZ <= cMinZ) {
                    shapes[i] = Shapes.empty();
                    continue;
                }

                // Rotate the local box for this facing
                Aabb rotated = rotateAabbY(cMinX, cMinY, cMinZ, cMaxX, cMaxY, cMaxZ, facing);
                shapes[i] = Shapes.box(rotated.minX, rotated.minY, rotated.minZ,
                                       rotated.maxX, rotated.maxY, rotated.maxZ);
            }
            result.put(facing, shapes);
        }
        // Store offsets list for cellIndex lookup
        this.presetCellOffsets = offsets;
        return result;
    }

    private List<CellOffset> presetCellOffsets;

    private int cellIndex(CellOffset offset) {
        if (presetCellOffsets == null) return -1;
        for (int i = 0; i < presetCellOffsets.size(); i++) {
            CellOffset c = presetCellOffsets.get(i);
            if (c.dx == offset.dx && c.dy == offset.dy && c.dz == offset.dz) {
                return i;
            }
        }
        return -1;
    }
}
