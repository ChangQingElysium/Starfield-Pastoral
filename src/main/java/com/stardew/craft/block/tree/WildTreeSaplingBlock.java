package com.stardew.craft.block.tree;

import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.manager.TreeGrowthManager;
import com.stardew.craft.tree.WildTrees;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WildTreeSaplingBlock extends Block {
	private final WildTrees.Def def;
	private final int stage;
	private volatile VoxelShape modelShape;
	private volatile boolean modelShapeResolved;

	@SuppressWarnings("null")
	public WildTreeSaplingBlock(WildTrees.Def def, int stage, Properties properties) {
		super(properties);
		this.def = def;
		this.stage = stage;
	}

	// The growth entry is removed with the block before drops are rolled, so remember it at destroy time.
	private static long destroyedPos = Long.MIN_VALUE;
	private static int destroyedGrowthStage = -1;

	@SuppressWarnings("null")
	@Override
	public void playerWillDestroy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.player.Player player) {
		if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
			destroyedPos = pos.asLong();
			destroyedGrowthStage = TreeGrowthManager.get(serverLevel).getGrowthStage(serverLevel, pos);
		}
		super.playerWillDestroy(level, pos, state, player);
	}

	/**
	 * Tree.performSeedDestroy / performSproutDestroy: only a growth-stage 0 seed returns its seed, and only
	 * when the player has foraging level 1+; stage 1-2 sprouts return nothing, but an axe may chop out one wood
	 * with a foraging-level/10 chance.
	 */
	@SuppressWarnings("null")
	@Override
	public java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state,
			net.minecraft.world.level.storage.loot.LootParams.Builder params) {
		java.util.List<net.minecraft.world.item.ItemStack> drops = new java.util.ArrayList<>(super.getDrops(state, params));
		var origin = params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN);
		var tool = params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL);
		var entity = params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY);
		if (stage != 0 || tool == null || !(entity instanceof net.minecraft.server.level.ServerPlayer player) || origin == null) {
			return drops;
		}
		int growth = destroyedPos == BlockPos.containing(origin).asLong() ? destroyedGrowthStage : 0;
		int foraging = com.stardew.craft.player.PlayerStardewDataAPI.getSkillLevel(player, com.stardew.craft.player.SkillType.FORAGING);
		boolean axe = tool.is(net.minecraft.tags.ItemTags.AXES);
		if (growth == 0) {
			if (foraging >= 1 && (axe || tool.is(net.minecraft.tags.ItemTags.HOES) || tool.is(net.minecraft.tags.ItemTags.PICKAXES))) {
				var seed = com.stardew.craft.manager.WildTreeSeedManager.getSeedItem(def);
				if (seed != null) {
					drops.add(new net.minecraft.world.item.ItemStack(seed));
				}
			}
		} else if (axe && player.getRandom().nextDouble() < foraging / 10.0) {
			var wood = com.stardew.craft.tree.prefab.PrefabTrees.logItem(def);
			if (wood != null) {
				drops.add(new net.minecraft.world.item.ItemStack(wood));
			}
		}
		return drops;
	}

	public WildTrees.Def getDef() {
		return def;
	}

	public int getStage() {
		return stage;
	}

	@SuppressWarnings("null")
	@Override
	public VoxelShape getShape(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockGetter level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") CollisionContext context) {
		if (!modelShapeResolved) {
			synchronized (this) {
				if (!modelShapeResolved) {
					String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
					String modelId = ModelVoxelShapeCache.variantModel(blockId, "");
					if (modelId != null && !modelId.isBlank()) {
						modelShape = ModelVoxelShapeCache.shape(modelId);
					}
					modelShapeResolved = true;
				}
			}
		}
		return modelShape != null ? modelShape : super.getShape(state, level, pos, context);
	}

	// ── 生存条件 ──────────────────────────────────────────────
	// 仅本模组的自然土壤与耕地；不得通过原版 dirt 标签放宽。

	private static boolean isValidGround(BlockState ground) {
		return com.stardew.craft.block.terrain.TerrainSoils.treeGround(ground);
	}

	@SuppressWarnings("null")
	@Override
	public boolean canSurvive(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") LevelReader level, @SuppressWarnings("null") BlockPos pos) {
		return isValidGround(level.getBlockState(pos.below()));
	}

	// 任意邻居更新（尤其是脚下方块被破坏 / 替换）时，若已不能生存则破坏并掉落。
	@SuppressWarnings("null")
	@Override
	public BlockState updateShape(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Direction direction,
			@SuppressWarnings("null") BlockState neighborState, @SuppressWarnings("null") LevelAccessor level,
			@SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockPos neighborPos) {
		if (!canSurvive(state, level, pos)) {
			level.scheduleTick(pos, this, 1);
		}
		return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
	}

	@SuppressWarnings("null")
	@Override
	public void tick(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") net.minecraft.server.level.ServerLevel level,
			@SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") net.minecraft.util.RandomSource random) {
		if (!canSurvive(state, level, pos)) {
			dropResources(state, level, pos);
			level.removeBlock(pos, false);
		}
	}

	// 禁止活塞推动（推动会让树苗被挤到任意位置，破坏种植规则）。
	@SuppressWarnings("null")
	@Override
	public PushReaction getPistonPushReaction(@SuppressWarnings("null") BlockState state) {
		return PushReaction.BLOCK;
	}

	@SuppressWarnings("null")
	@Override
	public void onPlace(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (level.isClientSide || oldState.getBlock() == state.getBlock()) {
			return;
		}
		// Stage swap (sapling0 <-> sapling1) should not reset tracked growth days.
		if (isSameTreeSapling(oldState.getBlock())) {
			return;
		}
		TreeGrowthManager.get((net.minecraft.server.level.ServerLevel) level).addSapling(level, pos);
	}

	@SuppressWarnings("null")
	@Override
	public void onRemove(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState newState, boolean movedByPiston) {
		super.onRemove(state, level, pos, newState, movedByPiston);
		if (level.isClientSide || state.getBlock() == newState.getBlock()) {
			return;
		}
		// Stage swap (sapling0 <-> sapling1) should not reset tracked growth days.
		if (isSameTreeSapling(newState.getBlock())) {
			return;
		}
		TreeGrowthManager.get((net.minecraft.server.level.ServerLevel) level).removeSapling(level, pos);
	}

	private boolean isSameTreeSapling(Block maybeSapling) {
		return maybeSapling instanceof WildTreeSaplingBlock other && other.getDef() == this.def;
	}
}
