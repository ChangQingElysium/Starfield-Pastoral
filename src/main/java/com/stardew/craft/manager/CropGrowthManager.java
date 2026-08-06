package com.stardew.craft.manager;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.agriculture.StardewCropRuntimeAdapter;
import com.stardew.craft.api.v1.agriculture.StardewCropState;
import com.stardew.craft.api.v1.internal.crop.StardewCropRuntimeRegistry;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.manager.FertilizerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * 作物生长管理器
 * 记录所有星露谷作物的位置，并在每天换日时统一处理生长
 */
@SuppressWarnings("unused")
public class CropGrowthManager extends SavedData {
    private static final String DATA_NAME = "stardew_crop_manager";
    
    // 存储所有作物的位置 (使用GlobalPos以支持多维度，虽然目前只限星露谷维度)
    private final Set<GlobalPos> cropPositions = new HashSet<>();
    /**
     * 公共区域（非任何玩家农场）耕作过的区块（chunk key）集合。
     * 由 HoeItem 在锄成耕地后调用 {@link #trackPublicTilledChunk} 登记，
     * 用于次日 dryAllFarmland 时确保被扫描，避免"小镇耕地永远不复原"。
     * 扫描后保留集合（耕地可能被反复锄）；只有当过夜还原后该 chunk 内确无耕地残留时清理。
     */
    private final Set<Long> publicTilledChunks = new HashSet<>();

    /**
     * 每株作物的生长状态：当前阶段已过天数 + 是否处于“再生长倒计时”状态。
     * 这是实现 Stardew Valley 原版 Crop.newDay() 的关键状态。
     */
    private final Map<GlobalPos, CropGrowthState> cropStates = new ConcurrentHashMap<>();

    // 防止在遍历时被 onRemove/onPlace 修改导致 ConcurrentModificationException
    private boolean isProcessing = false;
    private final Set<GlobalPos> pendingAdds = new HashSet<>();
    private final Set<GlobalPos> pendingRemoves = new HashSet<>();

    public CropGrowthManager() {}

    /** 返回所有已注册作物位置的不可变快照。 */
    public java.util.List<GlobalPos> getAllCropPositions() {
        return new java.util.ArrayList<>(cropPositions);
    }

    /** 获取或创建某个作物位置的生长状态。 */
    public CropGrowthState getOrCreateGrowthState(GlobalPos gp) {
        return cropStates.computeIfAbsent(gp, k -> new CropGrowthState());
    }

    public static class CropGrowthState {
        public int dayInPhase;
        /**
         * 当前处于哪一个“星露谷 phase”（0-3）。
         * 注意：我们的方块 AGE 只是 0-3 的渲染阶段，其中 AGE=3 需要只在成熟时出现，
         * 因此不能再用 AGE 直接当 phase。
         */
        public int phase;
        public boolean regrowing;
        public UUID planterUuid;

        public CropGrowthState() {
            this(0, 0, false, null);
        }

        public CropGrowthState(int dayInPhase, int phase, boolean regrowing, UUID planterUuid) {
            this.dayInPhase = dayInPhase;
            this.phase = phase;
            this.regrowing = regrowing;
            this.planterUuid = planterUuid;
        }
    }

    public CropGrowthState getOrCreateState(Level level, BlockPos pos) {
        @SuppressWarnings("null")
        GlobalPos globalPos = GlobalPos.of(level.dimension(), pos.immutable());
        return cropStates.computeIfAbsent(globalPos, (k) -> new CropGrowthState());
    }

    /**
     * 获取作物生长状态（不会创建新状态）。
     * 若未记录，则返回 null。
     */
    public CropGrowthState getState(Level level, BlockPos pos) {
        @SuppressWarnings("null")
        GlobalPos globalPos = GlobalPos.of(level.dimension(), pos.immutable());
        return cropStates.get(globalPos);
    }

    public void setRegrowing(Level level, BlockPos pos, boolean regrowing, int dayInPhase, int phase) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        CropGrowthState state = getOrCreateState(level, pos);
        state.regrowing = regrowing;
        state.dayInPhase = Math.max(0, dayInPhase);
        state.phase = Math.max(0, phase);
        setDirty();
    }

    /**
     * 登记一个"公共区域（小镇/沙漠等非任何农场）"被锄成耕地的位置所在区块，
     * 用于次日 dryAllFarmland 时强制扫描。HoeItem 在 tillTile 成功后调用。
     */
    public void trackPublicTilledChunk(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel)) return;
        long key = net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        if (publicTilledChunks.add(key)) {
            setDirty();
        }
    }

    /**
     * 添加作物位置
     */
    public void addCrop(Level level, BlockPos pos) {
        addCrop(level, pos, null);
    }

    /**
     * 添加作物位置并记录最近种植者，用于职业判定（如 Agriculturist）。
     */
    public void addCrop(Level level, BlockPos pos, UUID planterUuid) {
        if (level instanceof ServerLevel) {
            @SuppressWarnings("null")
            GlobalPos globalPos = GlobalPos.of(level.dimension(), pos.immutable());
            if (isProcessing) {
                pendingAdds.add(globalPos);
                pendingRemoves.remove(globalPos);
                CropGrowthState state = cropStates.computeIfAbsent(globalPos, k -> new CropGrowthState());
                if (planterUuid != null && state.planterUuid == null) {
                    state.planterUuid = planterUuid;
                }
                setDirty();
                return;
            }
            if (cropPositions.add(globalPos)) {
                CropGrowthState state = cropStates.computeIfAbsent(globalPos, k -> new CropGrowthState());
                if (planterUuid != null && state.planterUuid == null) {
                    state.planterUuid = planterUuid;
                }
                setDirty();
                return;
            }

            CropGrowthState state = cropStates.computeIfAbsent(globalPos, k -> new CropGrowthState());
            if (planterUuid != null && state.planterUuid == null) {
                state.planterUuid = planterUuid;
                setDirty();
            }
        }
    }

    /**
     * 移除作物位置
     */
    public void removeCrop(Level level, BlockPos pos) {
        if (level instanceof ServerLevel) {
            @SuppressWarnings("null")
            GlobalPos globalPos = GlobalPos.of(level.dimension(), pos.immutable());
            if (isProcessing) {
                pendingRemoves.add(globalPos);
                pendingAdds.remove(globalPos);
                cropStates.remove(globalPos);
                setDirty();
                return;
            }
            if (cropPositions.remove(globalPos)) {
                cropStates.remove(globalPos);
                setDirty();
            }
        }
    }

    private void applyPendingChanges() {
        boolean changed = false;
        if (!pendingRemoves.isEmpty()) {
            changed |= cropPositions.removeAll(pendingRemoves);
            for (GlobalPos p : pendingRemoves) {
                cropStates.remove(p);
            }
            pendingRemoves.clear();
        }
        if (!pendingAdds.isEmpty()) {
            changed |= cropPositions.addAll(pendingAdds);
            for (GlobalPos p : pendingAdds) {
                cropStates.putIfAbsent(p, new CropGrowthState());
            }
            pendingAdds.clear();
        }
        if (changed) {
            setDirty();
        }
    }

    /**
     * 每日生长结算
     * 由 TimeManager 在 advanceDay() 时调用
     */
    @SuppressWarnings("null")
    public void growDaily(ServerLevel serverLevel) {
        isProcessing = true;
        try {
            // 使用快照遍历，避免方块替换触发 add/remove 导致 HashSet 迭代器 CME
            java.util.List<GlobalPos> snapshot = new java.util.ArrayList<>(cropPositions);
            for (GlobalPos globalPos : snapshot) {

                // 确保是当前处理的维度
                if (globalPos.dimension() != serverLevel.dimension()) {
                    continue;
                }

                BlockPos pos = globalPos.pos();

                // 多人农场优化：跳过离线玩家农场中的作物
                if (!com.stardew.craft.farm.FarmDailyProcessHelper.shouldProcessPosition(serverLevel, pos)) {
                    continue;
                }

                // 检查区块是否加载 (避免加载未加载的区块造成卡顿)
                if (serverLevel.isLoaded(pos)) {
                    @SuppressWarnings("null")
                    BlockState state = serverLevel.getBlockState(pos);
                    Block block = state.getBlock();

                    boolean coreCrop = block instanceof StardewCropBlock;
                    StardewCropState runtimeCrop = coreCrop
                            ? StardewCropRuntimeRegistry.inspect(serverLevel, pos)
                            : StardewCropRuntimeRegistry.inspectAddon(serverLevel, pos);
                    if (runtimeCrop != null) {
                        boolean isWatered = runtimeCrop.soilPositions().stream()
                                .map(serverLevel::getBlockState)
                                .anyMatch(soil -> soil.getBlock() instanceof FarmBlock
                                        && soil.getValue(FarmBlock.MOISTURE) > 0);

                        // 运行时桥会重验作物身份；附属负责自己的生长持久化。
                        StardewCropRuntimeAdapter.DailyResult result =
                                StardewCropRuntimeRegistry.growOneDay(
                                        serverLevel, pos, isWatered, false);
                        setDirty();
                        if (result == StardewCropRuntimeAdapter.DailyResult.REMOVED) {
                            removeCrop(serverLevel, pos);
                            continue;
                        }

                        // SDV: 核心作物成熟当日 1% 概率长成 3×3 巨型作物。
                        // 附属作物可在自身 daily adapter 中实现其巨型形态，不强制核心几何。
                        BlockState afterGrow = serverLevel.getBlockState(pos);
                        if (afterGrow.getBlock() instanceof StardewCropBlock matureCheck
                                && afterGrow.hasProperty(StardewCropBlock.AGE)
                                && afterGrow.getValue(StardewCropBlock.AGE) == StardewCropBlock.MAX_AGE) {
                            com.stardew.craft.spawner.GiantCropSpawner.tryRoll(serverLevel, pos, matureCheck);
                        }
                    } else {
                        // 只要发现位置上不是作物了，就清理掉脏数据
                        removeCrop(serverLevel, pos);
                    }
                }
            }
        } finally {
            isProcessing = false;
            applyPendingChanges();
        }

        // 暴力处理所有加载区块的耕地 (干燥化)
        dryAllFarmland(serverLevel);
    }

    /**
     * 立即检查并枯萎所有“已加载区块里”的非当季作物。
     * 用于调试命令改季节后立刻生效，避免等到第二天。
     */
    @SuppressWarnings("null")
    public void killOutOfSeasonLoaded(ServerLevel serverLevel) {
        isProcessing = true;
        try {
            // 使用快照遍历，避免 setBlock 触发 add/remove 导致 HashSet 迭代器 CME
            java.util.List<GlobalPos> snapshot = new java.util.ArrayList<>(cropPositions);
            for (GlobalPos globalPos : snapshot) {

                if (globalPos.dimension() != serverLevel.dimension()) {
                    continue;
                }

                BlockPos pos = globalPos.pos();
                if (!serverLevel.isLoaded(pos)) {
                    continue;
                }

                @SuppressWarnings("null")
                BlockState state = serverLevel.getBlockState(pos);
                Block block = state.getBlock();
                if (block instanceof StardewCropBlock
                        || StardewCropRuntimeRegistry.inspectAddon(serverLevel, pos) != null) {
                    // 传 watered=false，确保核心作物不会推进生长；附属仍收到季节上下文。
                    StardewCropRuntimeAdapter.DailyResult result =
                            StardewCropRuntimeRegistry.growOneDay(
                                    serverLevel, pos, false, false);
                    setDirty();
                    if (result == StardewCropRuntimeAdapter.DailyResult.REMOVED) {
                        removeCrop(serverLevel, pos);
                    }
                } else {
                    removeCrop(serverLevel, pos);
                }
            }
        } finally {
            isProcessing = false;
            applyPendingChanges();
        }
    }
    
    @SuppressWarnings("null")
    private void dryAllFarmland(ServerLevel level) {
            FertilizerManager fertilizerManager = FertilizerManager.get(level);
         // SDV 平价：每天午夜扫描整个维度的耕地。
         // 农场区域：10% 衰退；非农场区域（小镇/沙漠等公共区）：必定还原为黄土。
         // 仅"小镇/沙漠等公共区"也产生过耕地（旧版本未拦截或创造模式），所以必须扫描。
         // 收集需要扫描的区块：在线玩家农场 + 已注册作物所在区块 + 所有当前已加载的本维度区块。
         java.util.Set<Long> chunkKeys = new java.util.HashSet<>();

         // 1. 在线玩家农场区块
         com.stardew.craft.farm.FarmInstanceRegistry farmReg = com.stardew.craft.farm.FarmInstanceRegistry.get();
         for (net.minecraft.server.level.ServerPlayer player : level.players()) {
             com.stardew.craft.farm.FarmInstance farm = farmReg.getFarmForPlayer(player.getUUID());
             if (farm == null) continue;
             BlockPos min = farm.getFarmBoundsMin();
             BlockPos max = farm.getFarmBoundsMax();
             int minCX = min.getX() >> 4;
             int maxCX = max.getX() >> 4;
             int minCZ = min.getZ() >> 4;
             int maxCZ = max.getZ() >> 4;
             for (int cx = minCX; cx <= maxCX; cx++) {
                 for (int cz = minCZ; cz <= maxCZ; cz++) {
                     chunkKeys.add(net.minecraft.world.level.ChunkPos.asLong(cx, cz));
                 }
             }
         }

         // 2. 已注册作物所在区块（覆盖温室等区域）
         for (GlobalPos gp : cropPositions) {
             if (gp.dimension() != level.dimension()) continue;
             BlockPos pos = gp.pos();
             chunkKeys.add(net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
         }

         // 3. 公共区域（小镇/沙漠等非农场）锄出过的耕地所在区块 —
         //    HoeItem 在锄成耕地时若位置不属于任何农场，会注册到 publicTilledChunks，
         //    保证次日扫描必能 100% 还原（SDV 同款行为）。
         for (long key : publicTilledChunks) {
             chunkKeys.add(key);
         }

         // 3. 加载并处理
         java.util.Set<net.minecraft.world.level.chunk.LevelChunk> chunksToProcess = new java.util.HashSet<>();
         for (long key : chunkKeys) {
             int cx = net.minecraft.world.level.ChunkPos.getX(key);
             int cz = net.minecraft.world.level.ChunkPos.getZ(key);
             net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
             if (chunk != null) {
                 chunksToProcess.add(chunk);
             }
         }

         // 3. 遍历并干燥
         for (net.minecraft.world.level.chunk.LevelChunk chunk : chunksToProcess) {
            net.minecraft.world.level.chunk.LevelChunkSection[] sections = chunk.getSections();
            for (int i = 0; i < sections.length; i++) {
                net.minecraft.world.level.chunk.LevelChunkSection section = sections[i];
                if (section.hasOnlyAir()) continue;
                if (!section.getStates().maybeHas((state) -> state.getBlock() instanceof net.minecraft.world.level.block.FarmBlock)) {
                    continue;
                }

                int bottomY = chunk.getSectionYFromSectionIndex(i) << 4;

                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            BlockState state = section.getBlockState(x, y, z);
                            if (state.getBlock() instanceof net.minecraft.world.level.block.FarmBlock) {
                                BlockPos realPos = new BlockPos(
                                        chunk.getPos().getMinBlockX() + x,
                                        bottomY + y,
                                        chunk.getPos().getMinBlockZ() + z
                                );

                                // SDV parity: 非农场区域的耕地过夜恢复为泥土
                                // 仅当上方没有作物 / forage 时才恢复（有作物或采集物说明是合法种植区，可保土）
                                // 温室内部豁免 — 温室是合法种植区域
                                boolean permanentContainer = state.getBlock()
                                        instanceof com.stardew.craft.block.utility.GardenPotBlock;
                                if (!permanentContainer
                                    && com.stardew.craft.core.FarmAreaResolver.isInStardewButNotFarm(level, realPos)
                                    && !com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseInterior(level, realPos)) {
                                    BlockState above = level.getBlockState(realPos.above());
                                    if (!isSoilProtectingBlock(level, realPos.above(), above)) {
                                        // 在还原为黄土前，清理该位置残留的肥料数据，避免下次再耕后无法施肥
                                        fertilizerManager.removeFertilizer(level, realPos);
                                        level.setBlock(realPos,
                                            com.stardew.craft.block.ModBlocks.YELLOW_DIRT.get().defaultBlockState(), Block.UPDATE_ALL);
                                        continue;
                                    }
                                }

                                // SDV parity: 农场区域的空耕地每日 10% 概率回退为黄土
                                // SDV GameLocation.GetDirtDecayChance: Farm/IslandWest → 0.1
                                // 温室 0%（已在上面豁免），有作物 / forage 不衰退
                                if (!permanentContainer
                                    && !com.stardew.craft.core.FarmAreaResolver.isInStardewButNotFarm(level, realPos)
                                    && !com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseInterior(level, realPos)) {
                                    BlockState above = level.getBlockState(realPos.above());
                                    if (!isSoilProtectingBlock(level, realPos.above(), above)
                                            && level.random.nextFloat() < 0.1f) {
                                        // 在还原为黄土前，清理该位置残留的肥料数据，避免下次再耕后无法施肥
                                        fertilizerManager.removeFertilizer(level, realPos);
                                        level.setBlock(realPos,
                                            com.stardew.craft.block.ModBlocks.YELLOW_DIRT.get().defaultBlockState(), Block.UPDATE_ALL);
                                        continue;
                                    }
                                }

                                @SuppressWarnings("null")
                                int moisture = state.getValue(net.minecraft.world.level.block.FarmBlock.MOISTURE);
                                if (moisture > 0) {
                                    // 保水土壤：按概率过夜保留水分（对齐 Stardew 的 retaining soil 概念）
                                    float retain = fertilizerManager.getWaterRetention(level, realPos);
                                    if (retain > 0f && level.random.nextFloat() < retain) {
                                        continue;
                                    }

                                    // 默认：设为干燥
                                    level.setBlock(realPos, state.setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 0), 2);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 判断「上方方块」是否能保护下方耕地不在过夜时回退为黄土。
     * 包括：
     *  - {@link StardewCropBlock}：所有自定义作物（含 WildSeedCropBlock）；
     *  - {@link com.stardew.craft.block.nature.ForageBlock}：X 季种成熟后变成的 forage 方块（蒲公英、雪人参等）。
     */
    private static boolean isSoilProtectingBlock(
            ServerLevel level,
            BlockPos abovePos,
            BlockState above
    ) {
        Block block = above.getBlock();
        return block instanceof StardewCropBlock
            || block instanceof com.stardew.craft.block.nature.ForageBlock
            || StardewCropRuntimeRegistry.inspectAddon(level, abovePos) != null;
    }

    @SuppressWarnings("null")
    @Override
    public CompoundTag save(@SuppressWarnings("null") CompoundTag tag, @SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (GlobalPos pos : cropPositions) {
            // GlobalPos 没有内置codec直接转tag的方法比较方便，我们手动存一下或者用NbtUtils存BlockPos
            CompoundTag posTag = new CompoundTag();
            posTag.putString("Dimension", pos.dimension().location().toString());
            posTag.put("Pos", NbtUtils.writeBlockPos(pos.pos()));

            CropGrowthState state = cropStates.get(pos);
            if (state != null) {
                posTag.putInt("DayInPhase", state.dayInPhase);
                posTag.putInt("Phase", state.phase);
                posTag.putBoolean("Regrowing", state.regrowing);
                if (state.planterUuid != null) {
                    posTag.putUUID("PlanterUuid", state.planterUuid);
                }
            }

            list.add(posTag);
        }
        tag.put("Crops", list);
        // 持久化公共区耕地区块（小镇/沙漠等非农场区域）
        long[] tilled = new long[publicTilledChunks.size()];
        int idx = 0;
        for (long key : publicTilledChunks) tilled[idx++] = key;
        tag.putLongArray("PublicTilledChunks", tilled);
        return tag;
    }

    public static CropGrowthManager load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        CropGrowthManager manager = new CropGrowthManager();
        if (tag.contains("Crops", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Crops", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag posTag = list.getCompound(i);
                @SuppressWarnings("null")
                ResourceKey<Level> dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, 
                        net.minecraft.resources.ResourceLocation.parse(posTag.getString("Dimension")));
                BlockPos pos = NbtUtils.readBlockPos(posTag, "Pos").orElse(BlockPos.ZERO);
                @SuppressWarnings("null")
                GlobalPos gp = GlobalPos.of(dim, pos);
                manager.cropPositions.add(gp);

                int dayInPhase = posTag.contains("DayInPhase", Tag.TAG_INT) ? posTag.getInt("DayInPhase") : 0;
                int phase = posTag.contains("Phase", Tag.TAG_INT) ? posTag.getInt("Phase") : 0;
                boolean regrowing = posTag.contains("Regrowing", Tag.TAG_BYTE) && posTag.getBoolean("Regrowing");
                UUID planterUuid = posTag.hasUUID("PlanterUuid") ? posTag.getUUID("PlanterUuid") : null;
                manager.cropStates.put(gp, new CropGrowthState(dayInPhase, phase, regrowing, planterUuid));
            }
        }
        if (tag.contains("PublicTilledChunks", Tag.TAG_LONG_ARRAY)) {
            for (long key : tag.getLongArray("PublicTilledChunks")) {
                manager.publicTilledChunks.add(key);
            }
        }
        return manager;
    }

    @SuppressWarnings("null")
    public static CropGrowthManager get(ServerLevel level) {
        // 数据保存在主世界(Overworld)的存储中，全局共享
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        CropGrowthManager::new,
                        CropGrowthManager::load,
                        null
                ),
                DATA_NAME
        );
    }
}
