package com.stardew.craft.block.crop.giant;

import com.stardew.craft.blockentity.GiantCropBlockEntity;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.secretnote.SecretNoteService;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.neoforged.neoforge.registries.DeferredItem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Stardew 巨型作物方块基类（3×3×2，仅 MAIN 块拥有 BE 渲染）。
 * 当作普通方块处理：空手/任何工具都能挖；硬度由 ModBlocks.Properties 控制；
 * 砍掉 18 格中任意一格 = 拆掉整株 + 掉 15-21 个对应作物 + 5 农场经验。
 */
public abstract class GiantCropBlock extends Block implements EntityBlock {

    public enum Part implements StringRepresentable {
        MAIN("main"),
        EXTENSION("extension");

        private final String name;
        Part(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    /** 3×3×2 = 18 个单元；MAIN 在 (0,0,0)，向四周延伸 ±1 + 上方 1 格。 */
    public static final List<int[]> CELL_OFFSETS;
    static {
        List<int[]> cells = new ArrayList<>(18);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    cells.add(new int[]{dx, dy, dz});
                }
            }
        }
        CELL_OFFSETS = List.copyOf(cells);
    }

    public static final int CHOP_FARMING_XP = 5;

    public GiantCropBlock(Properties properties) {
        super(properties);
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

    // ── 多格管理 ──────────────────────────────

    /** 在 main pos 处放置整株巨型作物（含 17 个上下扩展格）。 */
    public void placeFootprint(Level level, BlockPos mainPos) {
        BlockState mainState = defaultBlockState().setValue(PART, Part.MAIN);
        level.setBlock(mainPos, mainState, 3);
        BlockState extState = defaultBlockState().setValue(PART, Part.EXTENSION);
        for (int[] off : CELL_OFFSETS) {
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
            BlockState s = level.getBlockState(candidate);
            if (s.is(this) && s.getValue(PART) == Part.MAIN) {
                return candidate;
            }
        }
        return null;
    }

    // ── BlockEntity / 渲染 ─────────────────────

    @Override
    public RenderShape getRenderShape(@Nonnull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        if (state.getValue(PART) != Part.MAIN) {
            return null;
        }
        return new GiantCropBlockEntity(pos, state);
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

    // ── 玩家破坏：整株掉落 + 经验 + 移除 18 格 ────────────────────────

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
                // 静默移除其余 17 格（避免 onRemove 递归 / 重复 popResource）
                for (int[] off : CELL_OFFSETS) {
                    BlockPos p = mainPos.offset(off[0], off[1], off[2]);
                    if (p.equals(pos)) continue;
                    BlockState s = level.getBlockState(p);
                    if (s.is(this)) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // ── 客户端：左键命中/破坏粒子绑定到作物物品贴图 ────────────────────────

    @Override
    public void initializeClient(@SuppressWarnings("null") java.util.function.Consumer<net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions> consumer) {
        consumer.accept(new net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions() {
            @Override
            @SuppressWarnings("null")
            public boolean addHitEffects(BlockState state, Level level, net.minecraft.world.phys.HitResult target,
                                         net.minecraft.client.particle.ParticleEngine manager) {
                if (target instanceof net.minecraft.world.phys.BlockHitResult bhr) {
                    spawnItemCrack(level, bhr.getBlockPos(), bhr.getDirection(), 4);
                }
                return true;
            }

            @Override
            @SuppressWarnings("null")
            public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos,
                                             net.minecraft.client.particle.ParticleEngine manager) {
                BlockPos main = findMainPos(level, pos, state);
                BlockPos center = main != null ? main : pos;
                for (int[] off : CELL_OFFSETS) {
                    BlockPos p = center.offset(off[0], off[1], off[2]);
                    spawnItemCrack(level, p, null, 6);
                }
                return true;
            }

            private void spawnItemCrack(Level level, BlockPos pos, @Nullable net.minecraft.core.Direction face, int count) {
                ItemStack icon = new ItemStack(getDropItem().get());
                net.minecraft.core.particles.ItemParticleOption opt =
                        new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM, icon);
                java.util.Random rng = new java.util.Random();
                for (int i = 0; i < count; i++) {
                    double dx = pos.getX() + 0.1 + rng.nextDouble() * 0.8;
                    double dy = pos.getY() + 0.1 + rng.nextDouble() * 0.8;
                    double dz = pos.getZ() + 0.1 + rng.nextDouble() * 0.8;
                    double vx = (rng.nextDouble() - 0.5) * 0.3;
                    double vy = (rng.nextDouble() - 0.5) * 0.3 + 0.1;
                    double vz = (rng.nextDouble() - 0.5) * 0.3;
                    if (face != null) {
                        dx += face.getStepX() * 0.05;
                        dy += face.getStepY() * 0.05;
                        dz += face.getStepZ() * 0.05;
                    }
                    level.addParticle(opt, dx, dy, dz, vx, vy, vz);
                }
            }
        });
    }
}
