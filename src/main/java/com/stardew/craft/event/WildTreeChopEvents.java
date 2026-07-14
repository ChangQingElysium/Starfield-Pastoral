package com.stardew.craft.event;

import com.stardew.craft.entity.FallenOakTreeEntity;
import com.stardew.craft.book.BookPowerEffects;
import com.stardew.craft.blockentity.NewTreePartBlockEntity;
import com.stardew.craft.enchantment.StardewEnchantments;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.tool.StardewAxeItem;
import com.stardew.craft.manager.CoalForestArea;
import com.stardew.craft.manager.WildTreeSeedManager;
import com.stardew.craft.tree.WildTrees;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.ProfessionType;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.secretnote.SecretNoteService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WildTreeChopEvents {
	private WildTreeChopEvents() {
	}

	private static final int MAX_CONNECTED_BLOCKS = 160;
	// Must match the duration of sounds/tree/tree_crack.ogg (in ticks, 20 ticks = 1s).
	private static final int FALL_ANIM_TICKS = 20;
	private static final int MAX_TRUNK1_HEIGHT = 48;
	private static final int LEAF_DECAY_SCHEDULE_RADIUS = 10;
	private static final int MAX_LEAF_BLOCKS = 512;

	// Normalize all axes (except Stardew axes) to SV level-0 feel.
	private static final float BASE_TRUNK0_SPEED = 6.0f;
	// Tuning target: make non-Stardew axes and tier-0 axes feel ~5-6s on a typical tree.
	private static final float OTHER_AXE_DAMAGE_MULTIPLIER = 0.55f;
	private static final int STUMP_HEALTH = 45;
	private static final int MODERN_LOG_HEALTH = 10;
	private static final int MODERN_ROOT_HEALTH = 13;
	private static final int MODERN_BRANCH_HEALTH = 12;
	private static final double HEALTH_DENOM_BASE = 2.2;
	private static final double HEALTH_DENOM_PER = 0.035;
	private static final int XP_FELL_TREE = 14;
	private static final int XP_REMOVE_STUMP = 2;
	private static final int XP_MODERN_NATURAL_WOOD_PART = 1;
	// Approximate SV swing cadence (0.35s). Used for time->"swings" conversion when charging energy by time.
	private static final int AXE_SWING_TICKS = 7;

	private static final Map<UUID, MiningState> MINING = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_EXHAUST_WARN_TICK = new ConcurrentHashMap<>();

	public static void removePlayer(UUID playerId) {
		MINING.remove(playerId);
		LAST_EXHAUST_WARN_TICK.remove(playerId);
	}

	private record MiningState(BlockPos pos, long startTick) {
	}

	@SuppressWarnings("null")
	@SubscribeEvent
	public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
		if (!(event.getEntity() instanceof Player player)) {
			return;
		}
		if (player.isCreative()) {
			return;
		}
		ItemStack tool = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (!isAxeLike(tool)) {
			return;
		}

		BlockPos pos = event.getPosition().orElse(null);
		if (pos == null) {
			return;
		}
		Level level = player.level();
		BlockState state = level.getBlockState(pos);
		WildTrees.Def def = findChoppableTreeDef(state);
		if (def == null) {
			return;
		}
		BlockPos miningPos = pos;
		BlockPos pivotPos = findChopPivot(level, pos, def);
		if (pivotPos == null) {
			return;
		}

		// Energy gate: only applies in Stardew Valley dimension.
		if (!level.isClientSide
			&& player instanceof ServerPlayer serverPlayer
			&& player.level().dimension() == ModDimensions.STARDEW_VALLEY) {
			if (PlayerStardewDataAPI.getEnergy(serverPlayer) <= 0.0f) {
				// Stop mining progress and show message (rate-limited).
				event.setNewSpeed(0.0f);
				long now = level.getGameTime();
				long last = LAST_EXHAUST_WARN_TICK.getOrDefault(serverPlayer.getUUID(), 0L);
				if (now - last >= 20) {
					serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.translatable("stardewcraft.message.player.exhausted"), true);
					LAST_EXHAUST_WARN_TICK.put(serverPlayer.getUUID(), now);
				}
				return;
			}
		}
		int health = isModernWood(def, state) ? computeModernBlockHealth(state, def) : computeTreeHealth(level, pivotPos, def);
		if (!isModernWood(def, state) && health <= 0) {
			// Stump stage (lonely trunk0) should still reflect tier differences.
			if (isLonelyTreeBase(level, pivotPos, def)) {
				health = STUMP_HEALTH;
			} else {
				return;
			}
		}

		// Track mining start for energy calculation on completion.
		if (!level.isClientSide) {
			UUID id = player.getUUID();
			long now = level.getGameTime();
			MiningState prev = MINING.get(id);
			if (prev == null || !prev.pos.equals(miningPos)) {
				MINING.put(id, new MiningState(miningPos, now));
			}
		}

		// Make larger trees slower to chop. Non-Stardew axes and tier-0 axes are intentionally slower.
		float damageMul = (float) getStardewAxeDamageMultiplier(tool);
		double denom = HEALTH_DENOM_BASE + (double) health * HEALTH_DENOM_PER;
		float normalized = (float) (BASE_TRUNK0_SPEED * damageMul / Math.max(0.001, denom));
		event.setNewSpeed((float) Math.max(0.01, applyMiningSpeedModifiers(player, tool, normalized)));
	}

	private static double getStardewAxeDamageMultiplier(ItemStack tool) {
		boolean powerful = StardewEnchantments.has(tool, StardewEnchantments.POWERFUL);
		if (tool.getItem() instanceof StardewAxeItem stardewAxe) {
			int tier = stardewAxe.getStardewTier().ordinal();
			if (powerful) {
				tier = Math.min(4, tier + 2);
			}
			return switch (tier) {
				case 0 -> OTHER_AXE_DAMAGE_MULTIPLIER;
				case 1 -> 0.75;
				case 2 -> 1.0;
				case 3 -> 1.5;
				default -> 5.0;
			};
		}
		// Any other axe acts like tier-0 when chopping our wild trees.
		return OTHER_AXE_DAMAGE_MULTIPLIER;
	}

	private static WildTrees.Def findByTrunk0(BlockState state) {
		for (WildTrees.Def def : WildTrees.ALL) {
			if (state.getBlock() == def.trunk0().get()) {
				return def;
			}
		}
		return null;
	}

	private static WildTrees.Def findChoppableTreeDef(BlockState state) {
		WildTrees.Def def = findByTrunk0(state);
		if (def != null) {
			return def;
		}
		for (WildTrees.Def candidate : WildTrees.ALL) {
			if (isModernWood(candidate, state)) {
				return candidate;
			}
		}
		return null;
	}

	private static BlockPos findChopPivot(Level level, BlockPos pos, WildTrees.Def def) {
		BlockState state = level.getBlockState(pos);
		if (def.isModernRoot(state)) {
			return pos;
		}
		if (def.isModernLog(state)) {
			BlockPos root = WildTrees.findModernRootFromLog(level, pos, def);
			return root == null ? pos : root;
		}
		if (def.isModernBranch(state)) {
			return pos;
		}
		if (def.isTrunk0(state)) {
			return findLowestTrunk0(level, pos, def);
		}
		return null;
	}

	private static int computeTreeHealth(Level level, BlockPos trunk0Pos, WildTrees.Def def) {
		TreeParts parts = collectTreeParts(level, trunk0Pos, def);
		if (parts == null) {
			return -1;
		}
		int trunkSegments = Math.max(0, parts.trunkColumn.size() - 1);
		int branches = parts.branches.size();
		int leaves = parts.leaves.size();
		// A valid tree must have at least some height, and either branches or leaves.
		// This also tolerates malformed trees that accidentally use trunk0 as trunk segments.
		if (trunkSegments < 1 || (branches < 1 && leaves < 1)) {
			return -1;
		}
		int leafFactor = Math.min(20, leaves / 8);
		return 10 + trunkSegments * 6 + branches * 4 + leafFactor;
	}

	@SuppressWarnings("null")
	@SubscribeEvent
	public static void onBlockBreak(BlockEvent.BreakEvent event) {
		if (event.isCanceled()) {
			return;
		}
		if (!(event.getPlayer() instanceof ServerPlayer player)) {
			return;
		}
		ServerLevel level = player.serverLevel();
		BlockPos pos = event.getPos();
		BlockState state = level.getBlockState(pos);

		// 预制树（schematic 树）有独立的占地登记与砍伐逻辑：凡落在其占地内的方块（含栏杆等
		// 装饰）都交给它处理，旧版/算法树逻辑对其让路。
		if (com.stardew.craft.tree.prefab.PrefabTreeChopHandler.onBlockBreak(player, level, pos, event)) {
			return;
		}

		WildTrees.Def def = WildTrees.findByAnyPart(state);
		if (def == null) {
			return;
		}

		if (level.dimension() == ModDimensions.STARDEW_VALLEY
				&& !player.isCreative()
				&& !CoalForestArea.containsColumn(pos)
				&& !canChopAt(player, level, pos)) {
			event.setCanceled(true);
			player.displayClientMessage(
					net.minecraft.network.chat.Component.translatable("stardewcraft.farm.build_farm_only"), true);
			return;
		}

		// 现代树（预制树 + 老存档算法生成树）统一由预制树处理器在 onBlockBreak 顶部接管
		// （算法树会被「收养」成预制实例）。能走到这里的只是不成树的零散现代方块，按普通方块处理。
		if (def.isModernPart(state) && !player.isCreative()) {
			return;
		}

		// --- Creative mode: instant tree removal, no drops, no animation ---
		if (player.isCreative()) {
			if (def.isTrunk0(state) || def.isModernRoot(state) || def.isModernLog(state)) {
				BlockPos pivotPos = findChopPivot(level, pos, def);
				if (pivotPos == null) {
					return;
				}
				TreeSnapshot snapshot = analyzeTree(level, pivotPos, def);
				if (snapshot != null) {
					for (BlockPos p : snapshot.allPositions) {
						if (p.equals(snapshot.pivotTrunk0Pos)) {
							continue;
						}
						level.removeBlock(p, false);
					}
					WildTreeSeedManager.get(level).untrackTree(level, pivotPos);
					scheduleNearbyLeafDecay(level, pivotPos, def);
				} else {
					WildTreeSeedManager.get(level).untrackTree(level, pos);
				}
				return;
			}
			if (state.getBlock() == def.leaves().get() || def.isModernLeaves(state)) {
				return;
			}
			BlockPos pivot = findPivotTrunk0(level, pos, def);
			TreeSnapshot snapshot = pivot == null ? null : analyzeTree(level, pivot, def);
			if (snapshot != null) {
				for (BlockPos p : snapshot.allPositions) {
					if (p.equals(snapshot.pivotTrunk0Pos)) {
						continue;
					}
					level.removeBlock(p, false);
				}
				WildTreeSeedManager.get(level).untrackTree(level, pivot);
				scheduleNearbyLeafDecay(level, pivot, def);
			}
			return;
		}

		// --- Survival mode ---
		ItemStack tool = player.getItemInHand(InteractionHand.MAIN_HAND);
		boolean choppableBase = def.isTrunk0(state) || def.isModernRoot(state) || def.isModernLog(state);

		if (state.getBlock() != def.leaves().get() && !choppableBase) {
			BlockPos pivot = findPivotTrunk0(level, pos, def);
			TreeSnapshot snapshot = pivot == null ? null : analyzeTree(level, pivot, def);
			if (snapshot != null) {
				com.stardew.craft.network.payload.HudHintPayload.send(player, "stardewcraft.message.tree.chop_root");
				event.setCanceled(true);
			}
			return;
		}

		if (!isAxeLike(tool)) {
			if (choppableBase) {
				event.setCanceled(true);
			}
			return;
		}

		if (level.dimension() == ModDimensions.STARDEW_VALLEY
				&& PlayerStardewDataAPI.getEnergy(player) <= 0.0f) {
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable("stardewcraft.message.player.exhausted"), true);
			event.setCanceled(true);
			return;
		}

		if (!choppableBase) {
			return;
		}

		BlockPos pivotPos = findChopPivot(level, pos, def);
		if (pivotPos == null) {
			return;
		}
		TreeSnapshot snapshot = analyzeTree(level, pivotPos, def);
		if (snapshot != null) {
			ModernTreeMarker marker = snapshot.modern ? generatedModernTreeMarkerForRoot(level, snapshot.pivotTrunk0Pos, def) : null;
			if (snapshot.modern && marker == null) {
				return;
			}
			int experience = snapshot.modern
					? countGeneratedModernWoodParts(level, snapshot, def, marker) * XP_MODERN_NATURAL_WOOD_PART
					: XP_FELL_TREE;
			consumeEnergyForTreeChop(player, level, pos);
			java.util.ArrayList<ItemStack> fallDrops = new java.util.ArrayList<>();
			ItemStack seeds = rollSeedsOnChop(level, def, player);
			if (!seeds.isEmpty()) {
				fallDrops.add(seeds);
			}
			fallDrops.add(new ItemStack(ModItems.SAP.get(), 5));
			int dropCount = getFelledWoodDropCount(level, def, player);
			if (dropCount > 0) {
				var woodItem = getWoodDropItem(def);
				fallDrops.add(new ItemStack(woodItem, dropCount));
			}
			int hardwoodBonus = getLumberjackBonusHardwood(level, def, player);
			if (hardwoodBonus > 0) {
				fallDrops.add(new ItemStack(ModItems.WOOD_HARD.get(), hardwoodBonus));
			}
			com.stardew.craft.book.BookAcquisitionService.recordTreeChoppedAndMaybeAddBook(player, fallDrops, level.random);
			ItemStack secretNote = SecretNoteService.tryCreateFromSource(player, level.random, 0.005F);
			if (!secretNote.isEmpty()) {
				fallDrops.add(secretNote);
			}
			WildTreeSeedManager.get(level).untrackTree(level, snapshot.pivotTrunk0Pos);
			Direction dir = computeFallDirection(player, snapshot.pivotTrunk0Pos);
			fellTree(level, snapshot, dir, def, fallDrops);
			restoreGeneratedModernStumpMarker(level, marker);
			if (experience > 0) {
				PlayerStardewDataAPI.addExperience(player, SkillType.FORAGING, experience);
			}
			event.setCanceled(true);
			return;
		}

		if (isLonelyTreeBase(level, pivotPos, def)) {
			boolean modernRoot = def.isModernRoot(level.getBlockState(pivotPos));
			ModernTreeMarker marker = modernRoot ? generatedModernTreeMarkerForRoot(level, pivotPos, def) : null;
			if (modernRoot && marker == null) {
				return;
			}
			consumeEnergyForTreeChop(player, level, pos);
			level.removeBlock(pivotPos, false);
			playStumpEffects(level, pivotPos, def);
			int dropCount = getStumpWoodDropCount(level, def, player);
			if (dropCount > 0) {
				var woodItem = getWoodDropItem(def);
				Block.popResource(level, pivotPos, new ItemStack(woodItem, dropCount));
			}
			int hardwoodBonus = getLumberjackBonusHardwood(level, def, player);
			if (hardwoodBonus > 0) {
				Block.popResource(level, pivotPos, new ItemStack(ModItems.WOOD_HARD.get(), hardwoodBonus));
			}
			PlayerStardewDataAPI.addExperience(player, SkillType.FORAGING, modernRoot ? XP_MODERN_NATURAL_WOOD_PART : XP_REMOVE_STUMP);
			WildTreeSeedManager.get(level).untrackTree(level, pivotPos);
			event.setCanceled(true);
		}
	}

	private static int countGeneratedModernWoodParts(ServerLevel level, TreeSnapshot snapshot, WildTrees.Def def, ModernTreeMarker marker) {
		if (marker == null) {
			return 0;
		}
		int count = 0;
		for (BlockPos partPos : snapshot.allPositions) {
			if (partPos.equals(snapshot.pivotTrunk0Pos) || !isModernWood(def, level.getBlockState(partPos))) {
				continue;
			}
			ModernTreeMarker partMarker = generatedModernTreeMarkerForPart(level, partPos, def);
			if (partMarker != null && marker.treeId().equals(partMarker.treeId())) {
				count++;
			}
		}
		return count;
	}

	private static ModernTreeMarker generatedModernTreeMarkerForPart(ServerLevel level, BlockPos pos, WildTrees.Def def) {
		if (!(level.getBlockEntity(pos) instanceof NewTreePartBlockEntity part) || !part.hasGeneratedTreeMarker()) {
			return null;
		}
		if (!def.id().equals(part.getGeneratedTreeSpecies())) {
			return null;
		}
		ModernTreeMarker rootMarker = generatedModernTreeMarkerForRoot(level, part.getGeneratedTreeRoot(), def);
		if (rootMarker == null || !rootMarker.treeId().equals(part.getGeneratedTreeId())) {
			return null;
		}
		return rootMarker;
	}

	private static ModernTreeMarker generatedModernTreeMarkerForRoot(ServerLevel level, BlockPos root, WildTrees.Def def) {
		if (root == null || !def.isModernRoot(level.getBlockState(root))) {
			return null;
		}
		if (!(level.getBlockEntity(root) instanceof NewTreePartBlockEntity rootPart) || !rootPart.hasGeneratedTreeMarker()) {
			return null;
		}
		if (!def.id().equals(rootPart.getGeneratedTreeSpecies()) || !root.equals(rootPart.getGeneratedTreeRoot())) {
			return null;
		}
		return new ModernTreeMarker(rootPart.getGeneratedTreeId(), rootPart.getGeneratedTreeSpecies(), root.immutable());
	}

	private static void restoreGeneratedModernStumpMarker(ServerLevel level, ModernTreeMarker marker) {
		if (marker == null || !(level.getBlockEntity(marker.root()) instanceof NewTreePartBlockEntity stump)) {
			return;
		}
		stump.markGeneratedTree(marker.treeId(), marker.species(), marker.root());
	}

	private static boolean canChopAt(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseInterior(level, pos)) {
			return FarmAreaProtectionEvents.canModifyGreenhouseAt(player, level, pos);
		}
		return FarmAreaProtectionEvents.canModifyAt(player, pos);
	}


	@SuppressWarnings("null")
	private static boolean isAxeLike(ItemStack tool) {
		if (tool.isEmpty()) {
			return false;
		}
		// Prefer vanilla tag if available.
		if (tool.is(ItemTags.AXES)) {
			return true;
		}
		// Ensure our axes are always recognized even if tag data isn't applied in some dev setups.
		if (tool.getItem() instanceof StardewAxeItem) {
			return true;
		}
		// Recognize any other modded axe-like tool via NeoForge tool ability.
		return tool.canPerformAction(ItemAbilities.AXE_DIG);
	}

	private static void consumeEnergyForTreeChop(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (player.isCreative()) {
			return;
		}
		if (StardewEnchantments.has(player.getMainHandItem(), StardewEnchantments.EFFICIENT)) {
			MINING.remove(player.getUUID());
			return;
		}
		if (player.level().dimension() != ModDimensions.STARDEW_VALLEY) {
			return;
		}
		MiningState mining = MINING.remove(player.getUUID());
		if (mining == null || !mining.pos.equals(pos)) {
			return;
		}
		long durationTicks = Math.max(1L, level.getGameTime() - mining.startTick);

		int foraging = PlayerStardewDataAPI.getSkillLevel(player, SkillType.FORAGING);
		float perSwing = 2.0f - (foraging * 0.1f);
		if (perSwing <= 0.0f) {
			return;
		}
		float swings = (float) durationTicks / (float) AXE_SWING_TICKS;
		float cost = perSwing * swings;
		if (cost > 0.0f) {
			PlayerStardewDataAPI.consumeEnergy(player, cost);
		}
	}

	private static boolean isModernWood(WildTrees.Def def, BlockState state) {
		return def.isModernRoot(state) || def.isModernLog(state) || def.isModernBranch(state);
	}

	private static int computeModernBlockHealth(BlockState state, WildTrees.Def def) {
		int health;
		if (def.isModernRoot(state)) {
			health = MODERN_ROOT_HEALTH;
		} else if (def.isModernBranch(state)) {
			health = MODERN_BRANCH_HEALTH;
		} else {
			health = MODERN_LOG_HEALTH;
		}
		return isHardwoodTree(def) ? Math.round(health * 1.2F) : health;
	}

	private static float applyMiningSpeedModifiers(Player player, ItemStack tool, float baseSpeed) {
		float speed = baseSpeed;
		int efficiency = getItemEnchantmentLevel(player, tool, Enchantments.EFFICIENCY);
		if (efficiency > 0) {
			speed += (float) (efficiency * efficiency + 1);
		}

		@SuppressWarnings("null")
		MobEffectInstance haste = player.getEffect(MobEffects.DIG_SPEED);
		if (haste != null) {
			speed *= 1.0F + 0.2F * (haste.getAmplifier() + 1);
		}

		@SuppressWarnings("null")
		MobEffectInstance fatigue = player.getEffect(MobEffects.DIG_SLOWDOWN);
		if (fatigue != null) {
			float mult = switch (fatigue.getAmplifier()) {
				case 0 -> 0.3F;
				case 1 -> 0.09F;
				case 2 -> 0.0027F;
				case 3 -> 8.1E-4F;
				default -> 2.43E-4F;
			};
			speed *= mult;
		}

		if (player.isInWater()) {
			speed /= 5.0F;
		}
		if (!player.onGround()) {
			speed /= 5.0F;
		}
		return speed;
	}

	@SuppressWarnings({ "null", "deprecation" })
	private static int getItemEnchantmentLevel(Player player, ItemStack stack, net.minecraft.resources.ResourceKey<Enchantment> enchantmentKey) {
		@SuppressWarnings("null")
		var lookup = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		@SuppressWarnings("null")
		var holder = lookup.getOrThrow(enchantmentKey);
		return EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
	}

	private static ItemStack rollSeedsOnChop(ServerLevel level, WildTrees.Def def, ServerPlayer player) {
		int foragingLevel = com.stardew.craft.player.PlayerStardewDataAPI.getSkillLevel(player, com.stardew.craft.player.SkillType.FORAGING);
		if (foragingLevel < 1) {
			return ItemStack.EMPTY;
		}
		if (level.random.nextFloat() >= def.seedOnChopChance()) {
			return ItemStack.EMPTY;
		}
		var seedItem = WildTreeSeedManager.getSeedItem(def);
		if (seedItem == null) {
			return ItemStack.EMPTY;
		}
		@SuppressWarnings("null")
		int count = Mth.nextInt(level.random, 1, 2);
		return new ItemStack(seedItem, count);
	}

	private static net.minecraft.world.item.Item getWoodDropItem(WildTrees.Def def) {
		// Stardew-like: Mahogany and Mystic Tree drop Hardwood.
		String id = def.id();
		if ("mahogany".equals(id) || "mystic_tree".equals(id)) {
			return ModItems.WOOD_HARD.get();
		}
		return ModItems.WOOD_NORMAL.get();
	}

	@SuppressWarnings("null")
	private static int getFelledWoodDropCount(ServerLevel level, WildTrees.Def def, ServerPlayer player) {
		// Match Stardew Valley ranges (approx):
		// - Normal trees: 12-16 Wood
		// - Mahogany: 8-13 Hardwood
		// - Mystic Tree: 7-11 Hardwood
		String id = def.id();
		int base = switch (id) {
			case "mahogany" -> Mth.nextInt(level.random, 8, 13);
			case "mystic_tree" -> Mth.nextInt(level.random, 7, 11);
			default -> Mth.nextInt(level.random, 12, 16);
		};
		if (!isHardwoodTree(def) && PlayerStardewDataAPI.hasProfession(player, ProfessionType.FORESTER)) {
			base = Math.max(1, Mth.floor(base * 1.25f));
		}
		if (StardewEnchantments.has(player.getMainHandItem(), StardewEnchantments.SHAVING)) {
			base += isHardwoodTree(def) ? level.random.nextInt(2) + 1 : level.random.nextInt(4) + 2;
		}
		return BookPowerEffects.applyWoodcuttingDouble(PlayerDataManager.getPlayerData(player), base, level.random);
	}

	@SuppressWarnings("null")
	private static int getStumpWoodDropCount(ServerLevel level, WildTrees.Def def, ServerPlayer player) {
		// Match Stardew Valley stump behavior (approx):
		// - Normal stumps: 5-9 Wood
		// - Mystic stump: +1 Hardwood
		// - Mahogany stump: treat as 1-3 Hardwood (close enough; exact rules vary by version)
		String id = def.id();
		int base = switch (id) {
			case "mystic_tree" -> 1;
			case "mahogany" -> Mth.nextInt(level.random, 1, 3);
			default -> Mth.nextInt(level.random, 5, 9);
		};
		if (!isHardwoodTree(def) && PlayerStardewDataAPI.hasProfession(player, ProfessionType.FORESTER)) {
			base = Math.max(1, Mth.floor(base * 1.25f));
		}
		if (StardewEnchantments.has(player.getMainHandItem(), StardewEnchantments.SHAVING)) {
			base += isHardwoodTree(def) ? 1 : level.random.nextInt(3) + 1;
		}
		return BookPowerEffects.applyWoodcuttingDouble(PlayerDataManager.getPlayerData(player), base, level.random);
	}

	private static boolean isHardwoodTree(WildTrees.Def def) {
		String id = def.id();
		return "mahogany".equals(id) || "mystic_tree".equals(id);
	}

	private static int getLumberjackBonusHardwood(ServerLevel level, WildTrees.Def def, ServerPlayer player) {
		if (isHardwoodTree(def)) {
			return 0;
		}
		if (!PlayerStardewDataAPI.hasProfession(player, ProfessionType.LUMBERJACK)) {
			return 0;
		}
		return level.random.nextFloat() < 0.25f ? 1 : 0;
	}

	private static Direction computeFallDirection(ServerPlayer player, BlockPos rootPos) {
		int dx = rootPos.getX() - player.blockPosition().getX();
		int dz = rootPos.getZ() - player.blockPosition().getZ();
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx >= 0 ? Direction.EAST : Direction.WEST;
		}
		return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	@SuppressWarnings("null")
	private static TreeParts collectTreeParts(Level level, BlockPos trunk0Pos, WildTrees.Def def) {
		@SuppressWarnings("null")
		BlockState base = level.getBlockState(trunk0Pos);
		boolean modern = def.isModernRoot(base);
		if (!modern && base.getBlock() != def.trunk0().get()) {
			return null;
		}

		Set<BlockPos> trunkColumn = new HashSet<>();
		trunkColumn.add(trunk0Pos.immutable());
		for (int i = 1; i <= MAX_TRUNK1_HEIGHT; i++) {
			BlockPos p = trunk0Pos.above(i);
			@SuppressWarnings("null")
			Block b = level.getBlockState(p).getBlock();
			if (modern && b == def.modernLog().get()) {
				trunkColumn.add(p.immutable());
				continue;
			}
			// Tolerate malformed legacy trees that accidentally use trunk0 as a trunk segment.
			if (!modern && (b == def.trunk1().get() || b == def.trunk0().get())) {
				trunkColumn.add(p.immutable());
				continue;
			}
			break;
		}

		Set<BlockPos> branches = new HashSet<>();
		for (BlockPos tp : trunkColumn) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				@SuppressWarnings("null")
				BlockPos bp = tp.relative(d);
				@SuppressWarnings("null")
				Block b = level.getBlockState(bp).getBlock();
				if (modern && b == def.modernBranch().get()) {
					branches.add(bp.immutable());
				} else if (!modern && (b == def.branch1().get() || b == def.branch2().get())) {
					branches.add(bp.immutable());
				}
			}
		}

		// Collect leaves that belong to this tree, without flood-filling through neighbor trunks.
		Block leavesBlock = modern ? def.modernLeaves().get() : def.leaves().get();
		Set<BlockPos> wood = new HashSet<>();
		wood.addAll(trunkColumn);
		wood.addAll(branches);
		Set<BlockPos> leaves = new HashSet<>();
		ArrayDeque<BlockPos> q = new ArrayDeque<>();
		Set<BlockPos> visited = new HashSet<>();
		for (BlockPos wp : wood) {
			for (Direction d : Direction.values()) {
				@SuppressWarnings("null")
				BlockPos lp = wp.relative(d);
				if (level.getBlockState(lp).getBlock() == leavesBlock && visited.add(lp)) {
					q.add(lp);
				}
			}
		}
		while (!q.isEmpty() && leaves.size() < MAX_LEAF_BLOCKS) {
			BlockPos lp = q.removeFirst();
			if (isLeafClaimedByOtherWood(level, lp, wood, def, modern)) {
				continue;
			}
			leaves.add(lp.immutable());
			for (Direction d : Direction.values()) {
				@SuppressWarnings("null")
				BlockPos np = lp.relative(d);
				if (!visited.add(np)) {
					continue;
				}
				if (level.getBlockState(np).getBlock() == leavesBlock) {
					q.add(np);
				}
			}
		}

		// Safety: if trunk column is absurdly large, treat as invalid.
		if (trunkColumn.size() > MAX_CONNECTED_BLOCKS) {
			return null;
		}
		return new TreeParts(trunk0Pos.immutable(), trunkColumn, branches, leaves, modern);
	}

	private static boolean isLeafClaimedByOtherWood(Level level, BlockPos leafPos, Set<BlockPos> wood, WildTrees.Def def, boolean modern) {
		Block trunk0 = modern ? def.modernRoot().get() : def.trunk0().get();
		Block trunk1 = modern ? def.modernLog().get() : def.trunk1().get();
		Block branch1 = modern ? def.modernBranch().get() : def.branch1().get();
		Block branch2 = modern ? def.modernBranch().get() : def.branch2().get();
		for (Direction d : Direction.values()) {
			@SuppressWarnings("null")
			BlockPos np = leafPos.relative(d);
			@SuppressWarnings("null")
			Block b = level.getBlockState(np).getBlock();
			if (b == trunk0 || b == trunk1 || b == branch1 || b == branch2) {
				if (!wood.contains(np)) {
					return true;
				}
			}
		}
		return false;
	}

	private static void fellTree(ServerLevel level, TreeSnapshot snapshot, Direction direction, WildTrees.Def def, java.util.List<ItemStack> fallDrops) {
		BlockPos pivot = snapshot.pivotTrunk0Pos;
		// Remove the tree's parts (wood + leaves). Keep the pivot as a stump/root.
		for (BlockPos pos : snapshot.allPositions) {
			@SuppressWarnings("null")
			BlockState state = level.getBlockState(pos);
			Block b = state.getBlock();
			if (!isTreePartBlock(b, def, snapshot.modern)) {
				continue;
			}
			if (pos.equals(pivot)) {
				continue;
			}
			level.removeBlock(pos, false);
		}

		// Leaves -> seed drops. Keep them aligned with impact timing by adding to fallDrops.
		var seedItem = WildTreeSeedManager.getSeedItem(def);
		if (seedItem != null) {
			int leafCount = snapshot.leavesCount;
			int rolls = Math.max(1, leafCount / 12);
			int total = 0;
			for (int i = 0; i < rolls; i++) {
				if (level.random.nextFloat() < 0.20f) {
					total += 1 + level.random.nextInt(2);
				}
			}
			if (total > 0) {
				fallDrops.add(new ItemStack(seedItem, total));
			}
		}

		// Make leaf decay happen quickly by scheduling ticks for nearby leaves.
		scheduleNearbyLeafDecay(level, pivot, def);

		// Drops are handled when the fall animation completes (entity impact).
		FallenOakTreeEntity.spawn(level, pivot, direction, snapshot.fallPieces, FALL_ANIM_TICKS, fallDrops);
	}

	@SuppressWarnings({ "null", "deprecation" })
	private static void playStumpEffects(ServerLevel level, BlockPos pos, WildTrees.Def def) {
		BlockState state = level.getBlockState(pos);
		BlockState dustState = def.isModernRoot(state)
				? def.modernLog().get().defaultBlockState()
				: def.trunk1().get().defaultBlockState();
		var breakSound = dustState.getSoundType().getBreakSound();
		level.playSound(null, pos, breakSound, net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.1F);
		level.sendParticles(
				new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, dustState),
				pos.getX() + 0.5,
				pos.getY() + 0.5,
				pos.getZ() + 0.5,
				18,
				0.35,
				0.35,
				0.35,
				0.05
		);
	}

	@SuppressWarnings("null")
	private static boolean isLonelyTreeBase(Level level, BlockPos pos, WildTrees.Def def) {
		BlockState state = level.getBlockState(pos);
		boolean modern = def.isModernRoot(state);
		if (!modern && state.getBlock() != def.trunk0().get()) {
			return false;
		}
		// Stump/root stage: base only.
		@SuppressWarnings("null")
		Block above = level.getBlockState(pos.above()).getBlock();
		if (modern && above == def.modernLog().get()) {
			return false;
		}
		if (!modern && (above == def.trunk1().get() || above == def.trunk0().get())) {
			return false;
		}
		for (Direction d : Direction.Plane.HORIZONTAL) {
			@SuppressWarnings("null")
			Block b = level.getBlockState(pos.relative(d)).getBlock();
			if (modern && b == def.modernBranch().get()) {
				return false;
			}
			if (!modern && (b == def.branch1().get() || b == def.branch2().get())) {
				return false;
			}
		}
		return true;
	}

	@SuppressWarnings("null")
	private static TreeSnapshot analyzeTree(ServerLevel level, BlockPos trunk0Pos, WildTrees.Def def) {
		TreeParts parts = collectTreeParts(level, trunk0Pos, def);
		if (parts == null) {
			return null;
		}
		int trunkSegments = Math.max(0, parts.trunkColumn.size() - 1);
		int branches = parts.branches.size();
		int leaves = parts.leaves.size();
		// Definition: must have at least some height, and either branches or leaves.
		if (trunkSegments < 1 || (branches < 1 && leaves < 1)) {
			return null;
		}

		int leafFactor = Math.min(20, leaves / 8);
		int health = 10 + trunkSegments * 6 + branches * 4 + leafFactor;
		int woodDrop = Math.max(1, trunkSegments * 2 + branches);

		Set<BlockPos> all = new HashSet<>();
		all.addAll(parts.trunkColumn);
		all.addAll(parts.branches);
		all.addAll(parts.leaves);

		var fallPieces = new java.util.ArrayList<FallenOakTreeEntity.Piece>();
		for (BlockPos pos : all) {
			if (pos.equals(trunk0Pos)) {
				continue;
			}
			BlockState s = level.getBlockState(pos);
			Block b = s.getBlock();
			// Tolerate malformed legacy trees that accidentally use trunk0 as trunk segments.
			if (!isTreeWoodBlock(b, def, parts.modern)) {
				continue;
			}
			int dx = pos.getX() - trunk0Pos.getX();
			int dy = pos.getY() - trunk0Pos.getY();
			int dz = pos.getZ() - trunk0Pos.getZ();

			int blockId = BuiltInRegistries.BLOCK.getId(b);
			int facing2d = 0;
			if (s.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)) {
				facing2d = s.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).get2DDataValue();
			} else if (s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)) {
				@SuppressWarnings("null")
				var dir = s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
				if (dir.getAxis().isHorizontal()) {
					facing2d = dir.get2DDataValue();
				}
			}
			fallPieces.add(new FallenOakTreeEntity.Piece(dx, dy, dz, blockId, facing2d));
		}

		return new TreeSnapshot(trunk0Pos, all, fallPieces, health, woodDrop, parts.leaves.size(), parts.modern);
	}

	private static BlockPos findPivotTrunk0(ServerLevel level, BlockPos pos, WildTrees.Def def) {
		@SuppressWarnings("null")
		BlockState s = level.getBlockState(pos);
		if (def.isModernRoot(s)) {
			return pos.immutable();
		}
		if (def.isModernLog(s)) {
			return WildTrees.findModernRootFromLog(level, pos, def);
		}
		if (s.getBlock() == def.trunk0().get()) {
			return findLowestTrunk0(level, pos, def);
		}
		// If this is trunk1, the pivot is directly below along the column.
		if (s.getBlock() == def.trunk1().get()) {
			BlockPos p = pos;
			for (int i = 0; i <= MAX_TRUNK1_HEIGHT; i++) {
				p = p.below();
				@SuppressWarnings("null")
				Block b = level.getBlockState(p).getBlock();
				if (b == def.trunk0().get()) {
					return findLowestTrunk0(level, p, def);
				}
				if (b != def.trunk1().get() && b != def.trunk0().get()) {
					break;
				}
			}
			return null;
		}
		// If this is a branch or leaves, try to find an adjacent trunk1/trunk0 and then walk down.
		for (Direction d : Direction.values()) {
			@SuppressWarnings("null")
			BlockPos np = pos.relative(d);
			@SuppressWarnings("null")
			BlockState ns = level.getBlockState(np);
			Block nb = ns.getBlock();
			if (def.isModernRoot(ns)) {
				return np.immutable();
			}
			if (def.isModernLog(ns)) {
				return WildTrees.findModernRootFromLog(level, np, def);
			}
			if (nb == def.trunk0().get()) {
				return findLowestTrunk0(level, np, def);
			}
			if (nb == def.trunk1().get()) {
				return findPivotTrunk0(level, np, def);
			}
		}
		return null;
	}

	@SuppressWarnings("null")
	private static BlockPos findLowestTrunk0(Level level, BlockPos trunk0Pos, WildTrees.Def def) {
		BlockPos p = trunk0Pos;
		for (int i = 0; i < MAX_TRUNK1_HEIGHT; i++) {
			BlockPos below = p.below();
			if (level.getBlockState(below).getBlock() == def.trunk0().get()) {
				p = below;
				continue;
			}
			break;
		}
		return p;
	}

	@SuppressWarnings("null")
	private static void scheduleNearbyLeafDecay(ServerLevel level, BlockPos pivot, WildTrees.Def def) {
		boolean modern = def.isModernRoot(level.getBlockState(pivot));
		Block leaves = modern ? def.modernLeaves().get() : def.leaves().get();
		Block trunk0 = modern ? def.modernRoot().get() : def.trunk0().get();
		Block trunk1 = modern ? def.modernLog().get() : def.trunk1().get();
		Block branch1 = modern ? def.modernBranch().get() : def.branch1().get();
		Block branch2 = modern ? def.modernBranch().get() : def.branch2().get();
		int r = LEAF_DECAY_SCHEDULE_RADIUS;
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					BlockPos p = pivot.offset(dx, dy, dz);
					if (level.getBlockState(p).getBlock() != leaves) {
						continue;
					}
					// Only decay leaves that are not adjacent to any living wood block.
					// Leaves next to a trunk/branch belong to a surviving tree.
					if (isAdjacentToWood(level, p, trunk0, trunk1, branch1, branch2)) {
						continue;
					}
					level.scheduleTick(p, leaves, 2 + level.random.nextInt(6));
				}
			}
		}
	}

	@SuppressWarnings("null")
	private static boolean isAdjacentToWood(ServerLevel level, BlockPos leafPos,
			Block trunk0, Block trunk1, Block branch1, Block branch2) {
		for (Direction d : Direction.values()) {
			Block b = level.getBlockState(leafPos.relative(d)).getBlock();
			if (b == trunk0 || b == trunk1 || b == branch1 || b == branch2) {
				return true;
			}
		}
		return false;
	}

	private static boolean isTreePartBlock(Block block, WildTrees.Def def, boolean modern) {
		if (modern) {
			return block == def.modernRoot().get()
					|| block == def.modernLog().get()
					|| block == def.modernBranch().get()
					|| block == def.modernLeaves().get();
		}
		return block == def.trunk0().get()
				|| block == def.trunk1().get()
				|| block == def.branch1().get()
				|| block == def.branch2().get()
				|| block == def.leaves().get();
	}

	private static boolean isTreeWoodBlock(Block block, WildTrees.Def def, boolean modern) {
		if (modern) {
			return block == def.modernRoot().get()
					|| block == def.modernLog().get()
					|| block == def.modernBranch().get();
		}
		return block == def.trunk0().get()
				|| block == def.trunk1().get()
				|| block == def.branch1().get()
				|| block == def.branch2().get();
	}

	private record TreeParts(BlockPos pivotTrunk0, Set<BlockPos> trunkColumn, Set<BlockPos> branches, Set<BlockPos> leaves, boolean modern) {
	}

	private record ModernTreeMarker(UUID treeId, String species, BlockPos root) {
	}

	private static final class TreeSnapshot {
		final BlockPos pivotTrunk0Pos;
		final Set<BlockPos> allPositions;
		final java.util.List<FallenOakTreeEntity.Piece> fallPieces;
		final int leavesCount;
		final boolean modern;

		TreeSnapshot(BlockPos pivotTrunk0Pos, Set<BlockPos> allPositions, java.util.List<FallenOakTreeEntity.Piece> fallPieces, int health, int woodDrop, int leavesCount, boolean modern) {
			this.pivotTrunk0Pos = pivotTrunk0Pos;
			this.allPositions = allPositions;
			this.fallPieces = fallPieces;
			this.leavesCount = leavesCount;
			this.modern = modern;
		}
	}

}
