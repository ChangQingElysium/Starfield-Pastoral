package com.stardew.craft.manager;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.world.StardewWorldLootPools;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.world.data.WorldLootPoolData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;

/**
 * 采石场（Quarry）每日刷新服务 — SDV Mountain.quarryDayUpdate 的 MC 等价实现。
 *
 * <p>规则（完全照抄 SDV Mountain.cs:238-321）：
 * <ul>
 *   <li>每天尝试次数：N = min(16, 5 + year × 2)</li>
 *   <li>每次尝试随机抽一个 tile，必须 Y=-12 为砂土（coarse_dirt）且 Y=-11 为空气</li>
 *   <li>概率级联（按顺序判定，首个命中即放置并跳出）：
 *     <ol>
 *       <li>6% → 树苗（随机 oak / maple 的 SAPLING1）</li>
 *       <li>2% → 矿物节点：内 10% 神秘石替代(FIRE_QUARTZ)，90% 宝石原矿（7 种均分）</li>
 *       <li>4% → 远古斑点（SDV 内有 15% 种子斑点子分支，因我们无对应方块，全部归为远古斑点）</li>
 *       <li>15% → 大矿脉：0.1% 铱 / 10% 金 / 33% 铁 / 其余 铜（用对应 earth_*_ore 替代大节点）</li>
 *       <li>10% → 煤矿（earth_coal_ore 替代 BasicCoalNode）</li>
 *       <li>兜底 → 普通石头（6 种 STARDEW_STONES 等概率）</li>
 *     </ol>
 *   </li>
 * </ul>
 *
    * <p>采石场区域：X ∈ [155, 194]，Z ∈ [-140, -101]，只在 Y=80..81 的裸露采石场土面生成。
 */
@SuppressWarnings("null")
public final class QuarrySpawnService {

    private static final String INIT_DATA_ID = "stardewcraft_quarry_init";

    // ── 区域 ──
    private static final int AREA_MIN_X = 155;
    private static final int AREA_MAX_X = 194;
    private static final int AREA_MIN_Z = -140;
    private static final int AREA_MAX_Z = -101;
    private static final int FLOOR_MIN_Y = 80;
    private static final int FLOOR_MAX_Y = 81;


    private QuarrySpawnService() {}

    // ======================== 入口 ========================

    /** 每日（过夜结算）调用，从 StardewTimeManager 触发。 */
    public static void onNewDay(ServerLevel level, int year) {
        if (!level.dimension().equals(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY)) return;

        int n = Math.min(16, 5 + year * 2);
        java.util.List<long[]> forced = forceQuarryChunks(level);
        int placed = 0;
        try {
            RandomSource random = level.getRandom();
            for (int i = 0; i < n; i++) {
                if (trySpawnOne(level, random)) placed++;
            }
        } finally {
            releaseQuarryChunks(level, forced);
        }
        StardewCraft.LOGGER.info("[QUARRY] onNewDay year={} attempts={} placed={}", year, n, placed);
    }

    /** 初始全图铺设密度 — 每个砂土格按此概率触发一次放置尝试（与原版 hand-painted 采石场密度近似）。 */
    private static final double INITIAL_FILL_CHANCE = 0.20;

    /** 重置初始化标记，下次进入时会重新铺石头。用于 pregen region 覆盖后。 */
    public static void resetInitialSpawn(ServerLevel level) {
        if (!level.dimension().equals(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY)) return;
        QuarryInitData data = level.getDataStorage().computeIfAbsent(
                QuarryInitData.factory(), INIT_DATA_ID);
        data.resetForMigration();
    }

    /** 首次进入星露谷维度时执行初始化（老存档升级也会自动补）。幂等。 */
    public static void ensureInitialSpawn(ServerLevel level, int year) {
        if (!level.dimension().equals(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY)) return;

        QuarryInitData data = level.getDataStorage().computeIfAbsent(
                QuarryInitData.factory(), INIT_DATA_ID);
        if (data.isInitialized()) return;

        StardewCraft.LOGGER.info("[QUARRY] Running initial dense spawn (year={}, fillChance={})", year, INITIAL_FILL_CHANCE);

        // 强制加载采石场覆盖的区块，保证 setBlock 不被 chunk unloaded 跳过。
        java.util.List<long[]> forced = forceQuarryChunks(level);

        try {
            RandomSource r = level.getRandom();
            int placed = 0, attempts = 0;
            // 全图逐格扫描：每个砂土格按概率独立滚一次，拿到 SDV 级别的密度
            for (int x = AREA_MIN_X; x <= AREA_MAX_X; x++) {
                for (int z = AREA_MIN_Z; z <= AREA_MAX_Z; z++) {
                    if (r.nextDouble() > INITIAL_FILL_CHANCE) continue;
                    attempts++;
                    if (trySpawnAt(level, r, x, z)) placed++;
                }
            }
            StardewCraft.LOGGER.info("[QUARRY] Initial dense spawn done: attempts={} placed={}", attempts, placed);
            if (placed <= 0) {
                StardewCraft.LOGGER.warn("[QUARRY] Initial dense spawn placed nothing; leaving initialization pending for retry");
                return;
            }
        } finally {
            releaseQuarryChunks(level, forced);
        }
        data.markInitialized();
    }

    private static java.util.List<long[]> forceQuarryChunks(ServerLevel level) {
        java.util.List<long[]> forced = new java.util.ArrayList<>();
        int cxMin = AREA_MIN_X >> 4, cxMax = AREA_MAX_X >> 4;
        int czMin = AREA_MIN_Z >> 4, czMax = AREA_MAX_Z >> 4;
        for (int cx = cxMin; cx <= cxMax; cx++) {
            for (int cz = czMin; cz <= czMax; cz++) {
                level.setChunkForced(cx, cz, true);
                level.getChunk(cx, cz);
                forced.add(new long[]{cx, cz});
            }
        }
        return forced;
    }

    private static void releaseQuarryChunks(ServerLevel level, java.util.List<long[]> forced) {
        for (long[] chunk : forced) {
            level.setChunkForced((int) chunk[0], (int) chunk[1], false);
        }
    }

    // ======================== 核心：一次随机放置尝试 ========================

    private static boolean trySpawnOne(ServerLevel level, RandomSource r) {
        int x = AREA_MIN_X + r.nextInt(AREA_MAX_X - AREA_MIN_X + 1);
        int z = AREA_MIN_Z + r.nextInt(AREA_MAX_Z - AREA_MIN_Z + 1);
        return trySpawnAt(level, r, x, z);
    }

    private static boolean trySpawnAt(ServerLevel level, RandomSource r, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return false;
        BlockPos floor = findQuarryFloor(level, x, z);
        if (floor == null) return false;
        BlockPos above = floor.above();

        Block toPlace = pickBlock(level, r);
        if (toPlace == null) return false;

        // 其他方块（石头/矿石/树苗等）放在 coarse dirt 顶面上方。
        if (!isReplaceableAbove(level, above)) return false;
        level.setBlock(above, toPlace.defaultBlockState(), Block.UPDATE_ALL);
        return true;
    }

    /** 在指定 Y 范围内找裸露的采石场土面。 */
    private static BlockPos findQuarryFloor(ServerLevel level, int x, int z) {
        for (int y = FLOOR_MAX_Y; y >= FLOOR_MIN_Y; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (isQuarryFloor(level, pos) && isReplaceableAbove(level, pos.above())) {
                return pos;
            }
        }
        return null;
    }

    /** 严格只在裸露采石场土面生成。 */
    private static boolean isQuarryFloor(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.COARSE_DIRT) || state.is(ModBlocks.YELLOW_DIRT.get());
    }

    private static boolean isReplaceableAbove(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
    }

    // ======================== 概率级联（SDV parity） ========================

    private static Block pickBlock(ServerLevel level, RandomSource random) {
        var rewards = WorldLootPoolData.resolve(
                StardewWorldLootPools.QUARRY, "default", level, null, random);
        if (rewards.isEmpty() || !(rewards.getFirst().getItem() instanceof BlockItem blockItem)) {
            StardewCraft.LOGGER.error("[QUARRY] World-loot pool returned no placeable block");
            return null;
        }
        return blockItem.getBlock();
    }

    public static boolean canPlayerBreakInQuarry(BlockState state) {
        return isQuarryResourceBlock(state) || com.stardew.craft.tree.WildTrees.isAnyWildTreePart(state);
    }

    public static boolean canBombDestroyInQuarry(BlockState state) {
        return isQuarryResourceBlock(state);
    }

    private static boolean isQuarryResourceBlock(BlockState state) {
        return state.is(com.stardew.craft.core.ModTags.Blocks.QUARRY_RESOURCES);
    }

    // ======================== 持久化（首次初始化标志） ========================

    /**
     * 初始化版本号。改动采石场区域、放置密度、可生成方块表等任何会影响「初始面貌」的参数时
     * 把这个数 +1，老存档下次进入星露谷会重新铺一遍。
     */
    public static final int CURRENT_VERSION = 3;

    public static class QuarryInitData extends SavedData {
        private int initializedVersion;

        public QuarryInitData() {}

        private QuarryInitData(CompoundTag tag) {
            if (tag.contains("InitializedVersion")) {
                this.initializedVersion = tag.getInt("InitializedVersion");
            } else if (tag.contains("Initialized")) {
                // 旧存档兼容：老 Initialized=true 视为版本 1
                this.initializedVersion = tag.getBoolean("Initialized") ? 1 : 0;
            }
        }

        /** 已初始化到至少 CURRENT_VERSION 则认为无需再跑。 */
        public boolean isInitialized() { return initializedVersion >= CURRENT_VERSION; }

        public void markInitialized() {
            this.initializedVersion = CURRENT_VERSION;
            setDirty();
        }

        public void resetForMigration() {
            this.initializedVersion = 0;
            setDirty();
        }

        @Override
        @Nonnull
        public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
            tag.putInt("InitializedVersion", initializedVersion);
            return tag;
        }

        public static SavedData.Factory<QuarryInitData> factory() {
            return new SavedData.Factory<>(QuarryInitData::new, (tag, provider) -> new QuarryInitData(tag));
        }
    }
}
