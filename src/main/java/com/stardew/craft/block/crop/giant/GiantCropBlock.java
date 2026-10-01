package com.stardew.craft.block.crop.giant;

import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.secretnote.SecretNoteService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Stardew 巨型作物方块基类（3×3 占地，高度由模型包络确定）。
 * 当作普通方块处理：空手/任何工具都能挖；硬度由 ModBlocks.Properties 控制；
 * 砍掉任意承载格 = 拆掉整株 + 掉 15-21 个对应作物 + 5 农场经验。
 */
public abstract class GiantCropBlock extends Block {

    public enum Part implements StringRepresentable {
        MAIN("main"),
        EXTENSION("extension");

        private final String name;
        Part(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    /** 至多 3×3×3；MAIN 在 (0,0,0)，只放置实际高度内的承载格。 */
    public static final List<int[]> CELL_OFFSETS;
    static {
        List<int[]> cells = new ArrayList<>(27);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    cells.add(new int[]{dx, dy, dz});
                }
            }
        }
        CELL_OFFSETS = List.copyOf(cells);
    }

    public static final int CHOP_FARMING_XP = 5;

    public GiantCropBlock(Properties properties) {
        // Each cell's box depends on its root and the actual support surface.
        super(properties.dynamicShape());
        registerDefaultState(stateDefinition.any().setValue(PART, Part.MAIN));
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    /** 子类提供的掉落物物品。 */
    public abstract DeferredItem<Item> getDropItem();

    /** 掉落数量（含上下界，闭区间）。SDV 默认 15-21。 */
    public int getDropMin() { return 15; }
    public int getDropMax() { return 21; }

    @Nullable
    private String staticModelId() {
        String model = ModelVoxelShapeCache.variantModel(BuiltInRegistries.BLOCK.getKey(this).toString(), "part=main");
        return model != null && model.startsWith("stardewcraft:block/crop3d/") ? model : null;
    }

    public int footprintHeight() {
        String model = staticModelId();
        return model == null ? 2 : Math.max(1, Math.min(3,
                (int) Math.ceil(ModelVoxelShapeCache.requiredShape(model).bounds().maxY)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        String model = staticModelId();
        if (model == null) return super.getShape(state, level, pos, context);
        BlockPos main = findMainPos(level, pos, state);
        if (main == null) return Shapes.empty();
        double offset = 0;
        BlockState support = level.getBlockState(main.below());
        if (support.getBlock() instanceof FarmBlock) {
            VoxelShape floor = support.getCollisionShape(level, main.below());
            if (!floor.isEmpty()) offset = floor.max(Direction.Axis.Y) - 1;
        } else if (support.getBlock() instanceof com.stardew.craft.block.decor.GardenPlanterBlock) offset = -.25;
        AABB whole = ModelVoxelShapeCache.requiredShape(model).bounds().move(
                main.getX() - pos.getX(), main.getY() - pos.getY() + offset, main.getZ() - pos.getZ());
        return cellShape(whole, pos.getY() == main.getY());
    }

    /** Every occupied cell contributes its slice of the same enclosing model box. */
    public static VoxelShape cellShape(AABB whole, boolean lower) {
        double minX = Math.max(0, whole.minX), minY = lower ? whole.minY : Math.max(0, whole.minY);
        double minZ = Math.max(0, whole.minZ), maxX = Math.min(1, whole.maxX);
        double maxY = Math.min(1, whole.maxY), maxZ = Math.min(1, whole.maxZ);
        if (minX >= maxX || minY >= maxY || minZ >= maxZ) return Shapes.empty();
        return Shapes.create(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    // ── 多格管理 ──────────────────────────────

    /** 在 main pos 处放置整株巨型作物及实际高度所需的扩展格。 */
    public void placeFootprint(Level level, BlockPos mainPos) {
        BlockState mainState = defaultBlockState().setValue(PART, Part.MAIN);
        level.setBlock(mainPos, mainState, 3);
        BlockState extState = defaultBlockState().setValue(PART, Part.EXTENSION);
        for (int[] off : CELL_OFFSETS) {
            if (off[1] >= footprintHeight()) continue;
            if (off[0] == 0 && off[1] == 0 && off[2] == 0) continue;
            BlockPos p = mainPos.offset(off[0], off[1], off[2]);
            level.setBlock(p, extState, 3);
        }
    }

    /** 给定任意属于本巨型作物的 cell 位置，返回 main 的 BlockPos；找不到返回 null。 */
    @Nullable
    public BlockPos findMainPos(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.is(this) && state.getValue(PART) == Part.MAIN) {
            return pos;
        }
        for (int[] off : CELL_OFFSETS) {
            if (off[0] == 0 && off[1] == 0 && off[2] == 0) continue;
            BlockPos candidate = pos.offset(-off[0], -off[1], -off[2]);
            if (level instanceof LevelReader reader && !reader.hasChunkAt(candidate)) continue;
            BlockState s = level.getBlockState(candidate);
            if (off[1] < footprintHeight() && s.is(this) && s.getValue(PART) == Part.MAIN) {
                return candidate;
            }
        }
        return null;
    }

    /** Upgrade old two-layer footprints without replacing player-built obstructions. */
    public void restoreUpperFootprint(ServerLevel level, BlockPos main) {
        if (footprintHeight() <= 2 || !level.hasChunksAt(main.offset(-1, 0, -1), main.offset(1, 2, 1))) return;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockState state = level.getBlockState(main.offset(dx, 2, dz));
            if (state.is(this) && state.getValue(PART) == Part.EXTENSION) continue;
            if (!state.isAir() && !state.canBeReplaced()) return;
        }
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos pos = main.offset(dx, 2, dz);
            if (!level.getBlockState(pos).is(this)) level.setBlock(pos, defaultBlockState().setValue(PART, Part.EXTENSION), 3);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (state.getValue(PART) == Part.MAIN) restoreUpperFootprint(level, pos);
    }

    @Override
    public RenderShape getRenderShape(@Nonnull BlockState state) {
        return RenderShape.MODEL;
    }

    // ── 多块联动：EXT 失去 MAIN 自销 ────────────────────────

    @Override
    @SuppressWarnings("deprecation")
    protected boolean canSurvive(@Nonnull BlockState state, @Nonnull LevelReader level, @Nonnull BlockPos pos) {
        if (state.getValue(PART) == Part.MAIN) {
            return true;
        }
        return findMainPos(level, pos, state) != null;
    }

    @Override
    @SuppressWarnings({ "deprecation", "null" })
    protected BlockState updateShape(@Nonnull BlockState state,
                                     @Nonnull net.minecraft.core.Direction direction,
                                     @Nonnull BlockState neighborState,
                                     @Nonnull net.minecraft.world.level.LevelAccessor level,
                                     @Nonnull BlockPos pos,
                                     @Nonnull BlockPos neighborPos) {
        return state.canSurvive(level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    // ── 玩家破坏：整株掉落 + 经验 + 移除承载格 ────────────────────────

    @SuppressWarnings("null")
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockPos mainPos = findMainPos(level, pos, state);
            if (mainPos != null && level instanceof ServerLevel serverLevel) {
                if (!player.isCreative()) {
                    int min = getDropMin();
                    int max = getDropMax();
                    int count = min + level.random.nextInt(Math.max(1, max - min + 1));
                    ItemStack stack = new ItemStack(getDropItem().get(), count);
                    com.stardew.craft.item.quality.QualityHelper.setQuality(
                            stack, com.stardew.craft.item.quality.QualityHelper.NORMAL);
                    Block.popResource(serverLevel, mainPos, stack);

                    if (player instanceof ServerPlayer sp) {
						ItemStack secretNote = SecretNoteService.tryCreateUnseenNote(sp, level.random);
						if (!secretNote.isEmpty()) {
							Block.popResource(serverLevel, mainPos, secretNote);
						}
                        PlayerStardewDataAPI.addExperience(sp, SkillType.FARMING, CHOP_FARMING_XP);
                    }
                }
                // 移除本株的其余承载格，避免重复掉落或移除相邻植株。
                List<BlockPos> owned = new ArrayList<>();
                for (int[] off : CELL_OFFSETS) {
                    BlockPos p = mainPos.offset(off[0], off[1], off[2]);
                    if (p.equals(pos)) continue;
                    BlockState s = level.getBlockState(p);
                    if (s.is(this) && mainPos.equals(findMainPos(level, p, s))) {
                        owned.add(p);
                    }
                }
                for (BlockPos p : owned) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

}
