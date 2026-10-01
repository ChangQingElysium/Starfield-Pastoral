package com.stardew.craft.manager;

import com.stardew.craft.block.tree.WildTreeSaplingBlock;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.tree.WildTrees;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 野生树苗生长管理器：内部按 Stardew Valley 野树的 0..5 生长阶段和每日概率推进，
 * 外观仍映射到项目已有的两种树苗方块。
 */
public class TreeGrowthManager extends SavedData {
	private static final String DATA_NAME = "stardew_tree_manager";

	private static final int SAPLING1_GROWTH_STAGE = 3;
	private static final int MATURE_GROWTH_STAGE = 5;
	private static final int SEASON_WINTER = 3;

	private final Set<GlobalPos> saplingPositions = new HashSet<>();
	private final Map<GlobalPos, Integer> growthStages = new ConcurrentHashMap<>();
	private final Set<GlobalPos> fertilizedSaplings = ConcurrentHashMap.newKeySet();

	private boolean isProcessing = false;
	private final Set<GlobalPos> pendingAdds = new HashSet<>();
	private final Set<GlobalPos> pendingRemoves = new HashSet<>();

	/** 返回所有已注册树苗位置的不可变快照。 */
	public java.util.List<GlobalPos> getAllSaplingPositions() {
		return new java.util.ArrayList<>(saplingPositions);
	}

	public void addSapling(@Nonnull Level level, @Nonnull BlockPos pos) {
		if (!(level instanceof ServerLevel)) {
			return;
		}
		GlobalPos globalPos = toGlobalPos(level, pos);
		int initialStage = initialGrowthStage(level, pos);
		if (isProcessing) {
			pendingAdds.add(globalPos);
			pendingRemoves.remove(globalPos);
			growthStages.putIfAbsent(globalPos, initialStage);
			setDirty();
			return;
		}
		if (saplingPositions.add(globalPos)) {
			growthStages.putIfAbsent(globalPos, initialStage);
			setDirty();
		}
	}

	public void removeSapling(@Nonnull Level level, @Nonnull BlockPos pos) {
		if (!(level instanceof ServerLevel)) {
			return;
		}
		GlobalPos globalPos = toGlobalPos(level, pos);
		if (isProcessing) {
			pendingRemoves.add(globalPos);
			pendingAdds.remove(globalPos);
			growthStages.remove(globalPos);
			fertilizedSaplings.remove(globalPos);
			setDirty();
			return;
		}
		if (saplingPositions.remove(globalPos)) {
			growthStages.remove(globalPos);
			fertilizedSaplings.remove(globalPos);
			setDirty();
		}
	}

	private void applyPendingChanges() {
		boolean changed = false;
		if (!pendingRemoves.isEmpty()) {
			changed |= saplingPositions.removeAll(pendingRemoves);
			for (GlobalPos pendingRemove : pendingRemoves) {
				growthStages.remove(pendingRemove);
				fertilizedSaplings.remove(pendingRemove);
			}
			pendingRemoves.clear();
		}
		if (!pendingAdds.isEmpty()) {
			changed |= saplingPositions.addAll(pendingAdds);
			for (GlobalPos pendingAdd : pendingAdds) {
				growthStages.putIfAbsent(pendingAdd, 0);
			}
			pendingAdds.clear();
		}
		if (changed) {
			setDirty();
		}
	}

	public void growDaily(ServerLevel level) {
		isProcessing = true;
		try {
			java.util.List<GlobalPos> snapshot = new java.util.ArrayList<>(saplingPositions);
			for (GlobalPos globalPos : snapshot) {
				if (globalPos.dimension() != level.dimension()) {
					continue;
				}
				BlockPos pos = Objects.requireNonNull(globalPos.pos(), "pos");

				if (!com.stardew.craft.farm.FarmDailyProcessHelper.shouldProcessPosition(level, pos)) {
					continue;
				}

				if (!level.isLoaded(pos)) {
					continue;
				}

				processSaplingDay(level, pos, currentSeason());
			}
		} finally {
			isProcessing = false;
			applyPendingChanges();
		}
	}

	/** Debug/utility: advance a single sapling by one day using the same growth rules as daily processing. */
	public void growOneDay(@Nonnull ServerLevel level, @Nonnull BlockPos pos) {
		addSapling(level, pos);
		processSaplingDay(level, pos, currentSeason());
	}

	/**
	 * 离线/批量补帧：按天回放野树概率生长。树苗成熟、被成熟树挡在 4 阶、或成熟放置被阻挡时会提前停止，
	 * 避免长离线窗口里对已无变化的树重复 roll。
	 */
	public void growBy(@Nonnull ServerLevel level, @Nonnull BlockPos pos, int days) {
		if (days <= 0) {
			return;
		}
		addSapling(level, pos);
		int currentAbsoluteDay = currentAbsoluteDay();
		for (int dayOffset = 0; dayOffset < days; dayOffset++) {
			if (!level.isLoaded(pos)) {
				return;
			}
			BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof WildTreeSaplingBlock)) {
				removeSapling(level, pos);
				return;
			}
			GlobalPos globalPos = toGlobalPos(level, pos);
			int growthStageBefore = growthStages.getOrDefault(globalPos, initialGrowthStage(level, pos));
			int season = seasonOfAbsoluteDay(currentAbsoluteDay - days + dayOffset + 1);
			processSaplingDay(level, pos, season);

			if (!saplingPositions.contains(globalPos)) {
				return;
			}
			int growthStageAfter = growthStages.getOrDefault(globalPos, growthStageBefore);
			if (growthStageAfter == growthStageBefore && isPhysicallyBlockedNow(level, pos)) {
				return;
			}
		}
	}

	/**
	 * Tree Fertilizer：只设置 fertilized 标记。原版不会立即推进阶段。
	 * 返回 true 表示本次成功施肥并应消耗物品。
	 */
	public boolean fertilize(@Nonnull ServerLevel level, @Nonnull BlockPos pos) {
		addSapling(level, pos);
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof WildTreeSaplingBlock)) {
			removeSapling(level, pos);
			return false;
		}

		GlobalPos globalPos = toGlobalPos(level, pos);
		int growthStage = growthStages.getOrDefault(globalPos, initialGrowthStage(level, pos));
		if (growthStage >= MATURE_GROWTH_STAGE || fertilizedSaplings.contains(globalPos)) {
			return false;
		}

		fertilizedSaplings.add(globalPos);
		setDirty();
		return true;
	}

	public boolean isFertilized(@Nonnull ServerLevel level, @Nonnull BlockPos pos) {
		return fertilizedSaplings.contains(toGlobalPos(level, pos));
	}

	public int getGrowthStage(@Nonnull ServerLevel level, @Nonnull BlockPos pos) {
		GlobalPos globalPos = toGlobalPos(level, pos);
		int growthStage = growthStages.getOrDefault(globalPos, initialGrowthStage(level, pos));
		return Math.max(0, Math.min(growthStage, MATURE_GROWTH_STAGE));
	}

	public static int matureGrowthStage() {
		return MATURE_GROWTH_STAGE;
	}

	public boolean isBlockedNow(@Nonnull ServerLevel level, @Nonnull BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof WildTreeSaplingBlock saplingBlock)) {
			return false;
		}
		WildTrees.Def def = Objects.requireNonNull(saplingBlock.getDef(), "def");
		GlobalPos globalPos = toGlobalPos(level, pos);
		int growthStage = growthStages.getOrDefault(globalPos, initialGrowthStage(level, pos));
		if (growthStage >= MATURE_GROWTH_STAGE) {
			return !canMature(level, pos, def);
		}
		if (!canGrowInSeason(def, fertilizedSaplings.contains(globalPos), currentSeason())) {
			return true;
		}
		return growthStage >= getMaxGrowthStageHere(level, pos);
	}

	private boolean isPhysicallyBlockedNow(@Nonnull ServerLevel level, @Nonnull BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof WildTreeSaplingBlock saplingBlock)) {
			return false;
		}
		WildTrees.Def def = Objects.requireNonNull(saplingBlock.getDef(), "def");
		GlobalPos globalPos = toGlobalPos(level, pos);
		int growthStage = growthStages.getOrDefault(globalPos, initialGrowthStage(level, pos));
		if (growthStage >= MATURE_GROWTH_STAGE) {
			return !canMature(level, pos, def);
		}
		return growthStage >= getMaxGrowthStageHere(level, pos);
	}

	private void processSaplingDay(@Nonnull ServerLevel level, @Nonnull BlockPos pos, int season) {
		GlobalPos globalPos = toGlobalPos(level, pos);
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof WildTreeSaplingBlock saplingBlock)) {
			removeSapling(level, pos);
			return;
		}

        if (!com.stardew.craft.block.terrain.TerrainSoils.treeGround(level.getBlockState(pos.below()))) return;

		WildTrees.Def def = Objects.requireNonNull(saplingBlock.getDef(), "def");
		int growthStage = growthStages.getOrDefault(globalPos, initialGrowthStage(level, pos));
		boolean fertilized = fertilizedSaplings.contains(globalPos);

		if (growthStage >= MATURE_GROWTH_STAGE) {
			tryMature(level, pos, def);
			return;
		}

		if (!canGrowInSeason(def, fertilized, season)) {
			return;
		}

		int maxGrowthStage = getMaxGrowthStageHere(level, pos);
		if (growthStage >= maxGrowthStage) {
			return;
		}

        // Preserve original growth/Tree Fertilizer rules; compact soil reduces daily opportunities by 20%.
        if (com.stardew.craft.block.terrain.TerrainSoils.infertile(level.getBlockState(pos.below()))
                && level.random.nextFloat() >= .8F) return;

		boolean grows = level.random.nextFloat() < def.growthChance();
		if (!grows && fertilized) {
			grows = level.random.nextFloat() < def.fertilizedGrowthChance();
		}
		if (!grows) {
			return;
		}

		int nextGrowthStage = Math.min(growthStage + 1, maxGrowthStage);
		growthStages.put(globalPos, nextGrowthStage);
		setDirty();
		updateVisualStage(level, pos, saplingBlock, def, nextGrowthStage);

		if (nextGrowthStage >= MATURE_GROWTH_STAGE) {
			tryMature(level, pos, def);
		}
	}

	private void tryMature(@Nonnull ServerLevel level, @Nonnull BlockPos pos, @Nonnull WildTrees.Def def) {
		// 成熟时放置一个能容纳的预制树变体。
		// 放不下（空间不足）时不强行生成，树苗保留、下次再试。
		if (com.stardew.craft.tree.prefab.PrefabTreeManager.tryPlaceRandomVariant(level, pos, def)) {
			removeSapling(level, pos);
		}
	}

	private static boolean canGrowInSeason(@Nonnull WildTrees.Def def, boolean fertilized, int season) {
		return season != SEASON_WINTER || fertilized || def.growsInWinter();
	}

	private static int getMaxGrowthStageHere(@Nonnull ServerLevel level, @Nonnull BlockPos pos) {
		return isGrowthBlockedByNearbyMatureTree(level, pos) ? MATURE_GROWTH_STAGE - 1 : MATURE_GROWTH_STAGE;
	}

	private static boolean isGrowthBlockedByNearbyMatureTree(@Nonnull ServerLevel level, @Nonnull BlockPos saplingPos) {
		for (int xOffset = -1; xOffset <= 1; xOffset++) {
			for (int zOffset = -1; zOffset <= 1; zOffset++) {
				if (xOffset == 0 && zOffset == 0) {
					continue;
				}
				BlockPos checkPos = saplingPos.offset(xOffset, 0, zOffset);
				WildTrees.Def nearbyDef = WildTrees.findByModernRoot(level.getBlockState(checkPos));
				if (nearbyDef != null && WildTrees.isModernCompleteTree(level, checkPos, nearbyDef)) {
					return true;
				}
			}
		}
		return false;
	}

	private static void updateVisualStage(@Nonnull ServerLevel level, @Nonnull BlockPos pos, @Nonnull WildTreeSaplingBlock saplingBlock, @Nonnull WildTrees.Def def, int growthStage) {
		int wantedBlockStage = growthStage >= SAPLING1_GROWTH_STAGE ? 1 : 0;
		if (saplingBlock.getStage() == wantedBlockStage) {
			return;
		}
		BlockState nextState = (wantedBlockStage == 1 ? def.sapling1().get() : def.sapling0().get()).defaultBlockState();
		level.setBlock(pos, nextState, Block.UPDATE_ALL);
	}

	private static boolean canMature(@Nonnull ServerLevel level, @Nonnull BlockPos pos, @Nonnull WildTrees.Def def) {
		// 成熟门槛：至少有一个预制树变体放得下。
		return com.stardew.craft.tree.prefab.PrefabTreeManager.canPlaceAnyVariant(level, pos, def);
	}

	private static GlobalPos toGlobalPos(@Nonnull Level level, @Nonnull BlockPos pos) {
		return GlobalPos.of(
			Objects.requireNonNull(level.dimension(), "dimension"),
			Objects.requireNonNull(pos.immutable(), "pos")
		);
	}

	private static int initialGrowthStage(@Nonnull Level level, @Nonnull BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof WildTreeSaplingBlock saplingBlock && saplingBlock.getStage() >= 1) {
			return SAPLING1_GROWTH_STAGE;
		}
		return 0;
	}

	private static int currentSeason() {
		return StardewTimeManager.get().getCurrentSeason();
	}

	private static int currentAbsoluteDay() {
		StardewTimeManager timeManager = StardewTimeManager.get();
		return (timeManager.getCurrentYear() - 1) * 112 + timeManager.getCurrentSeason() * 28 + timeManager.getCurrentDay();
	}

	private static int seasonOfAbsoluteDay(int absoluteDay) {
		return Math.floorMod((absoluteDay - 1) / 28, 4);
	}

	@Override
	public @Nonnull CompoundTag save(@Nonnull CompoundTag tag, @Nonnull net.minecraft.core.HolderLookup.Provider provider) {
		ListTag list = new ListTag();
		for (GlobalPos globalPos : saplingPositions) {
			CompoundTag entryTag = new CompoundTag();
			String dimensionId = Objects.requireNonNull(globalPos.dimension().location().toString(), "dimension");
			Tag posTag = Objects.requireNonNull(
				NbtUtils.writeBlockPos(Objects.requireNonNull(globalPos.pos(), "pos")),
				"posTag"
			);
			int growthStage = growthStages.getOrDefault(globalPos, 0);
			entryTag.putString("Dimension", dimensionId);
			entryTag.put("Pos", posTag);
			entryTag.putInt("Stage", growthStage);
			entryTag.putBoolean("Fertilized", fertilizedSaplings.contains(globalPos));
			list.add(entryTag);
		}
		tag.put("Saplings", list);
		return tag;
	}

	public static TreeGrowthManager load(@Nonnull CompoundTag tag, @Nonnull net.minecraft.core.HolderLookup.Provider provider) {
		TreeGrowthManager manager = new TreeGrowthManager();
		if (tag.contains("Saplings", Tag.TAG_LIST)) {
			ListTag list = tag.getList("Saplings", Tag.TAG_COMPOUND);
			for (int index = 0; index < list.size(); index++) {
				CompoundTag entryTag = list.getCompound(index);
				ResourceKey<Level> dimension = ResourceKey.create(
					Objects.requireNonNull(net.minecraft.core.registries.Registries.DIMENSION, "DIMENSION"),
					Objects.requireNonNull(
						new net.minecraft.resources.ResourceLocation(
							Objects.requireNonNull(entryTag.getString("Dimension"), "dimension")
						),
						"dimensionId"
					)
				);
				BlockPos pos = NbtUtils.readBlockPos(entryTag, "Pos").orElse(BlockPos.ZERO);
				GlobalPos globalPos = GlobalPos.of(
					Objects.requireNonNull(dimension, "dimension"),
					Objects.requireNonNull(pos, "pos")
				);
				int growthStage = entryTag.getInt("Stage");
				manager.saplingPositions.add(globalPos);
				manager.growthStages.put(globalPos, Math.max(0, Math.min(growthStage, MATURE_GROWTH_STAGE)));
				if (entryTag.getBoolean("Fertilized")) {
					manager.fertilizedSaplings.add(globalPos);
				}
			}
		}
		return manager;
	}

	public static TreeGrowthManager get(ServerLevel level) {
		ServerLevel overworld = level.getServer().overworld();
		return overworld.getDataStorage().computeIfAbsent(
			new SavedData.Factory<>(
				TreeGrowthManager::new,
				TreeGrowthManager::load
			),
			DATA_NAME
		);
	}
}
