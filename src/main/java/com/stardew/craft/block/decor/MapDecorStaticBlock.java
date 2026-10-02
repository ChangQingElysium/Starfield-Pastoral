package com.stardew.craft.block.decor;

import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
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
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.stardew.craft.entity.npc.StardewNpcEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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

    protected static boolean dropsSuppressed() {
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

    // Only absorb floating-point noise; every actual collision cell must be reserved.
    private static final double EPS = 1.0E-7;

    private final String modelId;
    private final boolean boxCollision;
    /**
     * Map props may be solid for players while remaining traversable to the
     * schedule walker.  Stardew's map collision treats counters and display
     * furniture as visual props for NPC routing; the NPC must be able to reach
     * an authored standing tile behind them without changing player collision.
     */
    private final boolean npcPassable;
    /** Optional custom shape in model pixels; null means load the model profile. */
    private final VoxelShape presetShape;
    private volatile Set<CellOffset> localOccupiedOffsets;
    private final Map<Direction, Set<CellOffset>> occupiedOffsetsByFacing = new ConcurrentHashMap<>();

    public MapDecorStaticBlock(Properties properties, String modelId) {
        this(properties, modelId, false);
    }

    public MapDecorStaticBlock(Properties properties, String modelId, boolean boxCollision) {
        super(properties.dynamicShape());
        this.modelId = modelId;
        this.boxCollision = boxCollision;
        this.npcPassable = boxCollision;
        this.presetShape = null;
        registerDefaultState(stateDefinition.any().setValue(PART, Part.MAIN).setValue(FACING, Direction.NORTH));
    }

    /**
     * Constructor with a pre-set bounding box (in pixels, model-space, NORTH facing).
     * The box covers minX..maxX, minY..maxY, minZ..maxZ in pixel coordinates (0-16 = 1 block).
     * Every occupied cell returns this entire shape, translated into local coordinates.
     */
    public MapDecorStaticBlock(Properties properties, String modelId,
                               double minX, double minY, double minZ,
                               double maxX, double maxY, double maxZ) {
        this(properties, modelId, minX, minY, minZ, maxX, maxY, maxZ, false);
    }

    /**
     * Constructor for a multi-cell map prop with an NPC-only pass-through
     * collision policy.  The authored shape remains the player collision and
     * outline shape; only EntityCollisionContext for Stardew NPCs is cleared.
     */
    public MapDecorStaticBlock(Properties properties, String modelId,
                               double minX, double minY, double minZ,
                               double maxX, double maxY, double maxZ,
                               boolean npcPassable) {
        super(properties.dynamicShape());
        this.modelId = modelId;
        this.boxCollision = true;
        this.npcPassable = npcPassable;
        this.presetShape = Block.box(minX, minY, minZ, maxX, maxY, maxZ);
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
        return resolvePartShape(state, level, pos, state.getValue(PART));
    }

    @Override
    public VoxelShape getCollisionShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        if (npcPassable && context instanceof EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof StardewNpcEntity) {
            return Shapes.empty();
        }
        return getShape(state, level, pos, context);
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
        return orientedShape(facing).move(-offsetFromMain.dx, -offsetFromMain.dy, -offsetFromMain.dz);
    }

    private final Map<Direction, VoxelShape> wholeShapes = new ConcurrentHashMap<>();

    private VoxelShape orientedShape(Direction facing) {
        return wholeShapes.computeIfAbsent(facing, unused -> rotateShapeForFacing(canonicalShape(), facing));
    }

    /** One shape defines collision, outline and occupied cells for every renderer. */
    protected VoxelShape canonicalShape() {
        if (ModelVoxelShapeCache.hasCollisionProfile(modelId)) return ModelVoxelShapeCache.shapeFromModelId(modelId);
        if (presetShape != null) return presetShape;
        VoxelShape shape = ModelVoxelShapeCache.shapeFromModelId(modelId);
        return boxCollision && !shape.isEmpty() ? Shapes.create(shape.bounds()) : shape;
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

    protected Set<CellOffset> localOccupiedOffsets() {
        Set<CellOffset> cached = localOccupiedOffsets;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (localOccupiedOffsets != null) {
                return localOccupiedOffsets;
            }
            Set<CellOffset> discovered = new LinkedHashSet<>();
            VoxelShape shape = canonicalShape();
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
            if (level.isOutsideBuildHeight(extensionPos) || !level.getWorldBorder().isWithinBounds(extensionPos)) {
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
        placeExtensions(level, pos, state);
    }

    /** Safe for structure installers as well as BlockItem placement. No partial footprint on failure. */
    public boolean placeExtensions(Level level, BlockPos pos, BlockState state) {
        var cells = occupiedOffsets(state.getValue(FACING));
        Map<BlockPos, BlockState> previous = new java.util.LinkedHashMap<>();
        for (CellOffset offset : cells) {
            if (offset.isZero()) continue;
            BlockPos target = pos.offset(offset.dx, offset.dy, offset.dz);
            BlockState existing = level.getBlockState(target);
            if (level.isOutsideBuildHeight(target) || !level.getWorldBorder().isWithinBounds(target)
                    || (!existing.canBeReplaced() && !(existing.is(this)
                        && existing.getValue(PART) == Part.EXTENSION
                        && pos.equals(findMainPos(level, target, existing))))) return false;
            previous.put(target, existing);
        }
        for (BlockPos target : previous.keySet()) {
            BlockState extension = extensionState(state, target.subtract(pos));
            if (!level.getBlockState(target).equals(extension) && !level.setBlock(target, extension, 2 | 16)) {
                runWithDropsSuppressed(() -> previous.forEach((cell, before) -> level.setBlock(cell, before, 2 | 16)));
                return false;
            }
        }
        for (BlockPos target : previous.keySet()) level.updateNeighborsAt(target, this);
        return true;
    }

    /** Per-cell state, e.g. a light source at the lamp head, with shared placement/cleanup. */
    protected BlockState extensionState(BlockState mainState, BlockPos offset) {
        return mainState.setValue(PART, Part.EXTENSION);
    }

    @Override
    protected InteractionResult useWithoutItem(@Nonnull BlockState state, @Nonnull Level level,
                                               @Nonnull BlockPos pos, @Nonnull Player player,
                                               @Nonnull BlockHitResult hit) {
        if (state.getValue(PART) == Part.EXTENSION) {
            BlockPos mainPos = findMainPos(level, pos, state);
            if (mainPos == null) {
                return InteractionResult.PASS;
            }
            BlockHitResult mainHit = new BlockHitResult(hit.getLocation(), hit.getDirection(), mainPos, hit.isInside());
            return useWithoutItem(level.getBlockState(mainPos), level, mainPos, player, mainHit);
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    protected ItemInteractionResult useItemOn(@Nonnull ItemStack stack, @Nonnull BlockState state,
                                              @Nonnull Level level, @Nonnull BlockPos pos,
                                              @Nonnull Player player, @Nonnull InteractionHand hand,
                                              @Nonnull BlockHitResult hit) {
        if (state.getValue(PART) == Part.EXTENSION) {
            BlockPos mainPos = findMainPos(level, pos, state);
            if (mainPos == null) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            BlockHitResult mainHit = new BlockHitResult(hit.getLocation(), hit.getDirection(), mainPos, hit.isInside());
            return useItemOn(stack, level.getBlockState(mainPos), level, mainPos, player, hand, mainHit);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
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
                    popResource(level, mainPos, extensionRemovalDrop(level, mainPos));
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
                    popResource(level, mainPos, extensionRemovalDrop(level, mainPos));
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

    /** Stateful furniture may own its single packed drop in MAIN.onRemove instead. */
    protected ItemStack extensionRemovalDrop(Level level, BlockPos mainPos) { return new ItemStack(this); }

    private Direction mainFacingForCleanup(BlockGetter level, BlockPos mainPos, BlockState fallbackState) {
        BlockState mainState = level.getBlockState(mainPos);
        if (mainState.is(this) && mainState.hasProperty(FACING)) {
            return mainState.getValue(FACING);
        }
        return fallbackState.hasProperty(FACING) ? fallbackState.getValue(FACING) : Direction.NORTH;
    }

    protected record CellOffset(int dx, int dy, int dz) {
        public CellOffset {}
        static final CellOffset ZERO = new CellOffset(0, 0, 0);

        private boolean isZero() {
            return dx == 0 && dy == 0 && dz == 0;
        }

        public CellOffset rotateY(Direction facing) {
            return switch (facing) {
                case EAST -> new CellOffset(-dz, dy, dx);
                case SOUTH -> new CellOffset(-dx, dy, -dz);
                case WEST -> new CellOffset(dz, dy, -dx);
                default -> this;
            };
        }

        public CellOffset unrotateY(Direction facing) {
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

}
