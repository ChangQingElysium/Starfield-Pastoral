package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModTags;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.book.BookPowerEffects;
import com.stardew.craft.enchantment.StardewEnchantments;
import com.stardew.craft.item.tool.StardewPickaxeItem;
import com.stardew.craft.mining.MineGenerationBalance;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.ProfessionType;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.ItemTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * 矿石挖掘事件处理器 - 实现星露谷风格的镐子tier系统
 * 事件在 StardewCraft 主类中手动注册
 */
@SuppressWarnings("null")
public final class MinePickaxeEvents {
	private static final double GEODE_BASE_CHANCE = 0.013; // 低于原版 0.022
	private static final double OMNI_GEODE_EXTRA_CHANCE = 0.0025; // 低于原版 0.005
	// 3D 球形炸弹一次覆盖约为原版 2D 圆形的 5 倍方块；附带奖励按概率折算。
	private static final double BOMB_INCIDENTAL_DROP_SCALE = 0.20;

	private static final float SDV_PICKAXE_SWING_TICKS = 8.0F;

	private MinePickaxeEvents() {
	}

	@SuppressWarnings("null")
	@SubscribeEvent
	public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
		Player player = event.getEntity();
		if (player == null) {
			return;
		}
		BlockState state = event.getState();
		if (!isStardewMineBlock(state)) {
			return;
		}

		// SDV parity: 只能在矿井维度或自己/授权的农场上挖模组石头。
		// 小镇（公共区域）和别人没授权的农场禁止挖掘。
		if (player.level() instanceof ServerLevel sl && !player.isCreative()) {
			BlockPos p = event.getPosition().orElse(BlockPos.ZERO);
			if (sl.dimension() == ModDimensions.STARDEW_VALLEY) {
				if (!(player instanceof ServerPlayer sp) || !FarmAreaProtectionEvents.canModifyAt(sp, p)) {
					event.setNewSpeed(0.0F);
					return;
				}
			} else if (sl.dimension() != ModMiningDimensions.STARDEW_MINING) {
				event.setNewSpeed(0.0F);
				return;
			}
		}

		ItemStack tool = player.getMainHandItem();
		int stardewTier = getEffectiveStardewPickaxeTier(tool);
		int requiredTier = getRequiredTier(state);

		// 非模组镐子：只能挖 tier 0 的石头和煤矿，不能挖铁矿及以上、宝石矿、矿物节点
		if (stardewTier < 0) {
			if (requiredTier > 0 || isStardewOre(state) || isMineralBlock(state)) {
				event.setNewSpeed(0.0F);
				return;
			}
		} else if (requiredTier > 0 && stardewTier < requiredTier) {
			// 模组镐子等级不足
			event.setNewSpeed(0.0F);
			return;
		}

		boolean isPickaxeLike = isPickaxeLike(tool);
		int speedTier = stardewTier >= 0 ? stardewTier : (isPickaxeLike ? 0 : -1);
		int strikes = MineGenerationBalance.pickaxeStrikes(
				getSdvStoneHealth(state), speedTier);
		float hardness = event.getPosition()
				.map(pos -> state.getDestroySpeed(player.level(), pos))
				.orElse(1.0F);
		float baseToolSpeed = hardness > 0.0F
				? 30.0F * hardness / (strikes * SDV_PICKAXE_SWING_TICKS)
				: 0.0F;
		float speed = computeDigSpeed(player, tool, baseToolSpeed);
		if (isMineralBlock(state)) {
			speed *= 4.0F; // Loose surface items are normally picked up directly.
		}

		// Do not cap to originalSpeed here: originalSpeed depends on vanilla tags/tool-components and may be
		// extremely low if the block isn't recognized as pickaxe-mineable. Our design intentionally overrides it.
		event.setNewSpeed(Math.max(0.0F, speed));
	}

	@SuppressWarnings("null")
	@SubscribeEvent
	public static void onBlockBreak(BlockEvent.BreakEvent event) {
		if (!(event.getPlayer() instanceof ServerPlayer player)) {
			return;
		}
		if (player.isCreative()) {
			return;
		}

		BlockState state = event.getState();
		if (!isStardewMineBlock(state)) {
			return;
		}

		// SDV parity: 模组石头/矿石只能在矿井维度和有权限的农场上破坏。
		// 小镇等公共区域以及别人没授权的农场禁止挖掘。
		if (event.getLevel() instanceof ServerLevel sl) {
			if (sl.dimension() == ModDimensions.STARDEW_VALLEY) {
				if (!FarmAreaProtectionEvents.canModifyAt(player, event.getPos())) {
					event.setCanceled(true);
					player.displayClientMessage(
							Component.translatable("stardewcraft.farm.build_farm_only"), true);
					return;
				}
			} else if (sl.dimension() != ModMiningDimensions.STARDEW_MINING) {
				event.setCanceled(true);
				return;
			}
		}

		ItemStack tool = player.getMainHandItem();
		int requiredTier = getRequiredTier(state);
		int stardewTier = getEffectiveStardewPickaxeTier(tool);

		if (!isPickaxeLike(tool)) {
			event.setCanceled(true); // 不是镐子，阻止破坏
			return;
		}

		// 非模组镐子：只能挖 tier 0 的石头和煤矿，不能挖铁矿及以上、宝石矿、矿物节点
		if (stardewTier < 0 && (requiredTier > 0 || isStardewOre(state) || isMineralBlock(state))) {
			event.setCanceled(true);
			return;
		}

		if (requiredTier > 0 && stardewTier < requiredTier) {
			event.setCanceled(true); // tier不够，阻止破坏
			return;
		}

		if (!(event.getLevel() instanceof ServerLevel level)) {
			return;
		}

		// 能量不足时，禁止继续挖矿
		if (PlayerStardewDataAPI.getEnergy(player) <= 0.0f) {
			player.displayClientMessage(Component.translatable("stardewcraft.message.player.exhausted"), true);
			event.setCanceled(true);
			return;
		}

		// 所有检查通过：让原版正常破坏方块（声音、粒子、破坏动画走原版流程），
		// 自定义掉落由 onBlockDrops 接管（替换原版战利品表结果）。
		// 梯子生成由 MiningBlockBreakHandler.onBlockBreak 订阅同一事件完成。
		int miningExp = getMiningExperienceForBlock(state);
		if (miningExp > 0) {
			PlayerStardewDataAPI.addExperience(player, SkillType.MINING, miningExp);
		}
		com.stardew.craft.player.PlayerDataManager.getPlayerData(player)
				.recordMineBlockBroken(isStardewOre(state), isGemOre(state), isMineralBlock(state));

		consumeMiningEnergy(player, state);
	}

	/**
	 * 替换模组石头/矿石的掉落物，用星露谷数量公式覆盖原版战利品表。
	 * 矿石：清空原版掉落，改投对应 Stardew 产物（按采矿等级/职业/幸运计算数量）。
	 * 普通石头：保留原版掉落（掉落自身），额外滚晶洞 / 从石头中掉煤的概率。
	 * 矿物节点（水晶/火石英等）：保留原版掉落（掉落自身）。
	 */
	@SubscribeEvent
	public static void onBlockDrops(BlockDropsEvent event) {
		if (!(event.getBreaker() instanceof ServerPlayer player)) {
			return;
		}
		if (player.isCreative()) {
			return;
		}
		BlockState state = event.getState();
		if (!isStardewMineBlock(state)) {
			return;
		}
		ServerLevel level = event.getLevel();
		BlockPos pos = event.getPos();

		if (com.stardew.craft.festival.desert.DesertFestivalMineService.isCalicoEggStone(state)) {
			event.getDrops().clear();
			com.stardew.craft.festival.desert.DesertFestivalMineService.dropCalicoEggStone(level, player, pos, level.getRandom());
			addSecretNoteFromStone(event, level, player, pos, state);
			PlayerStardewDataAPI.addExperience(player, SkillType.MINING, 50);
			return;
		}

		if (isStardewOre(state)) {
			Item dropItem = getOreDropItem(state);
			if (dropItem != null) {
				int count = isGemOre(state) ? computeGemOreCount(level, player) : computeStardewOreCount(player, pos);
				if (count <= 0) {
					count = 1;
				}
				event.getDrops().clear();
				event.getDrops().add(makeDrop(level, pos, new ItemStack(dropItem, count)));

				// SDV parity: 所有铱矿石（stone 765）3.5% 概率额外掉落五彩碎片
				// 见 GameLocation.cs::breakStone case "765": `if (r.NextDouble() < 0.035)`
				if (state.is(ModTags.Blocks.IRIDIUM_ORES)) {
					if (level.getRandom().nextDouble() < 0.035) {
						event.getDrops().add(makeDrop(level, pos,
							new ItemStack(ModItems.PRISMATIC_SHARD.get(), 1)));
					}
				}
			}
			addSecretNoteFromStone(event, level, player, pos, state);
			return;
		}

		// 矿物节点（水晶等）不滚晶洞/煤；普通石头才走额外掉落概率。
		if (!isMineralBlock(state)) {
			addGeodeDrop(event, level, player, pos, state);
			addCoalFromStoneDrop(event, level, player, pos, state);
			com.stardew.craft.festival.desert.DesertFestivalMineService.tryAddStoneEggDrop(level, player, pos, level.getRandom());
			addSecretNoteFromStone(event, level, player, pos, state);
		}
	}

	private static void addSecretNoteFromStone(BlockDropsEvent event, ServerLevel level,
											 ServerPlayer player, BlockPos pos, BlockState state) {
		if (!state.is(ModTags.Blocks.STARDEW_STONES) && !state.is(ModTags.Blocks.STARDEW_ORES)) {
			return;
		}
		ItemStack secretNote = com.stardew.craft.secretnote.SecretNoteService
				.tryCreateFromSource(player, level.getRandom(), 0.0075F);
		if (!secretNote.isEmpty()) {
			event.getDrops().add(makeDrop(level, pos, secretNote));
		}
	}

	private static ItemEntity makeDrop(ServerLevel level, BlockPos pos, ItemStack stack) {
		return new ItemEntity(level,
				pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
	}

	private static int getMiningExperienceForBlock(BlockState state) {
		@SuppressWarnings("null")
		String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();

		if (path.contains("prismatic_shard")) return 50;
		if (path.contains("diamond_ore") || path.contains("amethyst_ore") || path.contains("aquamarine_ore")
				|| path.contains("emerald_ore") || path.contains("jade_ore") || path.contains("ruby_ore")
				|| path.contains("topaz_ore")) {
			return 25;
		}
		if (path.contains("iridium_ore")) return 25;
		if (path.contains("gold_ore")) return 15;
		if (path.contains("iron_ore")) return 10;
		if (path.contains("coal_ore") || path.contains("copper_ore")) return 5;

		if (state.is(ModTags.Blocks.STARDEW_STONES)
				|| path.equals("earth_shale")
				|| path.equals("frost_gneiss")
				|| path.equals("lava_basalt")
				|| path.equals("dark_earth_shale")
				|| path.equals("dark_frost_gneiss")
				|| path.equals("dark_lava_basalt")
				|| path.equals("banded_marble")
				|| path.equals("limestone")
				|| path.equals("mossy_sandstone")
				|| path.equals("cracked_slate")
				|| path.equals("scoria")
				|| path.equals("salt_rock")) {
			return 1;
		}

		return 0;
	}

	@SuppressWarnings("null")
	private static void addGeodeDrop(BlockDropsEvent event, ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		if (level.dimension() != ModMiningDimensions.STARDEW_MINING) {
			return;
		}
		if (isStardewOre(state)) {
			return;
		}

		int floorNumber = getFloorNumber(pos);
		int luckLevel = PlayerStardewDataAPI.getLuckLevel(player);
		double dailyLuck = PlayerStardewDataAPI.getDailyLuck(player);
		int miningLevel = PlayerStardewDataAPI.getSkillLevel(player, SkillType.MINING);
		double chanceModifier = dailyLuck / 2.0 + miningLevel * 0.005 + luckLevel * 0.001;
		int excavatorMultiplier = PlayerStardewDataAPI.hasProfession(player, ProfessionType.EXCAVATOR) ? 2 : 1;

		// SDV GameLocation.cs:14833 + MineShaft.cs:3671 — Dwarf Statue _4: geode 概率 ×1.25
		double dwarfGeodeMultiplier = player.hasEffect(com.stardew.craft.effect.ModMobEffects.DWARF_STATUE_4) ? 1.25 : 1.0;
		double geodeChance = GEODE_BASE_CHANCE * (1.0 + chanceModifier) * excavatorMultiplier * dwarfGeodeMultiplier;
		if (level.getRandom().nextDouble() < geodeChance) {
			Item geode = pickGeodeItemForFloor(floorNumber);
			if (geode != null) {
				event.getDrops().add(makeDrop(level, pos, new ItemStack(geode, 1)));
			}
		}

		// 普通矿井 20 层以上仍可额外掉落万能晶洞
		if (floorNumber >= 20 && level.getRandom().nextDouble() < OMNI_GEODE_EXTRA_CHANCE * (1.0 + chanceModifier) * excavatorMultiplier * dwarfGeodeMultiplier) {
			event.getDrops().add(makeDrop(level, pos, new ItemStack(ModItems.OMNI_GEODE.get(), 1)));
		}

	}

	private static Item pickGeodeItemForFloor(int floorNumber) {
		if (floorNumber >= 80) {
			return ModItems.MAGMA_GEODE.get();
		}
		if (floorNumber >= 40) {
			return ModItems.FROZEN_GEODE.get();
		}
		return ModItems.GEODE.get();
	}

	private static int getFloorNumber(BlockPos pos) {
		int z = pos.getZ();
		int floor = Math.round((z - 14) / (float) com.stardew.craft.mining.MiningCoordinates.FLOOR_SPACING);
		return Math.max(0, floor);
	}

	@SubscribeEvent
	public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		// No per-player state to clean up after energy rework
	}

	/**
	 * SDV parity: 体力按实际需要的原版挥镐次数结算。
	 * 每次挥镐消耗 2 - MiningLevel×0.1；石头生命值除以镐子伤害决定次数。
	 */
	private static void consumeMiningEnergy(ServerPlayer player, BlockState state) {
		if (StardewEnchantments.has(player.getMainHandItem(), StardewEnchantments.EFFICIENT)) {
			return;
		}
		int tier = getEffectiveStardewPickaxeTier(player.getMainHandItem());
		if (tier < 0) {
			tier = 0;
		}
		int miningLevel = PlayerStardewDataAPI.getSkillLevel(player, SkillType.MINING);
		float cost = MineGenerationBalance.miningEnergyCost(
				getSdvStoneHealth(state), tier, miningLevel);
		PlayerStardewDataAPI.consumeEnergy(player, cost);
	}

	private static int getSdvStoneHealth(BlockState state) {
		@SuppressWarnings("null")
		String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK
				.getKey(state.getBlock()).getPath();
		if (path.contains("iridium_ore")) return 16;
		if (path.equals("diamond_ore")) return 10;
		if (path.contains("gold_ore")) return 8;
		if (getGemOreDropItem(path) != null) return 5;
		if (path.contains("iron_ore")) return 4;
		if (path.contains("copper_ore")) return 3;
		if (path.contains("coal_ore")) return 2;
		if (path.contains("desert_bedrock")) return 5;
		if (path.contains("lava_basalt")) return 4;
		if (path.contains("frost_gneiss")) return 3;
		if (path.contains("earth_shale")) return 1;
		if (path.equals("sulfur_rock") || path.equals("weathered_stone")) return 5;
		if (path.equals("scoria")) return 4;
		if (path.equals("banded_marble")
				|| path.equals("cracked_slate")
				|| path.equals("salt_rock")) return 3;
		if (path.equals("limestone") || path.equals("mossy_sandstone")) return 1;
		if (isMineralBlock(state)) return 1;
		return 3;
	}

	private static Item getOreDropItem(BlockState state) {
		@SuppressWarnings("null")
		String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		if (path.contains("coal_ore")) {
			return ModItems.COAL.get();
		}
		if (path.contains("copper_ore")) {
			return ModItems.COPPER_ORE.get();
		}
		if (path.contains("iron_ore")) {
			return ModItems.IRON_ORE.get();
		}
		if (path.contains("gold_ore")) {
			return ModItems.GOLD_ORE.get();
		}
		if (path.contains("iridium_ore")) {
			return ModItems.IRIDIUM_ORE.get();
		}
		return getGemOreDropItem(path);
	}

	private static boolean isGemOre(BlockState state) {
		@SuppressWarnings("null")
		String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		return getGemOreDropItem(path) != null;
	}

	private static Item getGemOreDropItem(String path) {
		return switch (path) {
			case "amethyst_ore" -> ModItems.AMETHYST.get();
			case "aquamarine_ore" -> ModItems.AQUAMARINE.get();
			case "diamond_ore" -> ModItems.DIAMOND.get();
			case "emerald_ore" -> ModItems.EMERALD.get();
			case "jade_ore" -> ModItems.JADE.get();
			case "ruby_ore" -> ModItems.RUBY.get();
			case "topaz_ore" -> ModItems.TOPAZ.get();
			default -> null;
		};
	}

	private static int computeGemOreCount(ServerLevel level, ServerPlayer player) {
		int count = 1;
		if (PlayerStardewDataAPI.hasProfession(player, ProfessionType.GEOLOGIST) && level.getRandom().nextFloat() < 0.5f) {
			count += 1;
		}
		return count;
	}

	/**
	 * SDV parity: MineShaft.checkStoneForItems
	 * 挖普通石头时 5% 基础概率触发「矿石掉落」，在该 5% 内 25% 概率掉煤炭。
	 * 有效煤炭掉率 ≈ 1.25%。Prospector 职业使煤炭子概率 ×2 (50%)。
	 */
	private static void addCoalFromStoneDrop(BlockDropsEvent event, ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		if (level.dimension() != ModMiningDimensions.STARDEW_MINING) {
			return;
		}
		// 只对普通石头生效，矿石和宝石矿物节点不走这条路径
		if (isStardewOre(state) || isMineralBlock(state)) {
			return;
		}
		int miningLevel = PlayerStardewDataAPI.getSkillLevel(player, SkillType.MINING);
		double dailyLuck = PlayerStardewDataAPI.getDailyLuck(player);
		int luckLevel = PlayerStardewDataAPI.getLuckBuffLevel(player);
		double chanceModifier = dailyLuck / 2.0 + miningLevel * 0.005 + luckLevel * 0.001;

		// SDV: 5% × (1 + chanceModifier) × oreModifier(0.8~1.2, 我们统一用1.0)
		double oreDropChance = 0.05 * (1.0 + chanceModifier);
		RandomSource r = level.getRandom();
		if (r.nextDouble() < oreDropChance) {
			// SDV: 在矿石掉落内，25% 概率额外掉煤炭；Prospector(Burrower) ×2
			int burrowerMultiplier = PlayerStardewDataAPI.hasProfession(player, ProfessionType.PROSPECTOR) ? 2 : 1;
			// SDV MineShaft.cs:3698 — Dwarf Statue _2: 矿洞内额外 +0.10 coal chance
			double dwarfCoalBonus = player.hasEffect(com.stardew.craft.effect.ModMobEffects.DWARF_STATUE_2) ? 0.10 : 0.0;
			if (r.nextDouble() < 0.25 * burrowerMultiplier + dwarfCoalBonus) {
				event.getDrops().add(makeDrop(level, pos, new ItemStack(ModItems.COAL.get(), 1)));
			}
		}

		// SDV parity: 玩家到过矿井 120 层后，普通石头才可能偶尔掉落五彩碎片（对应 SDV Mystic Stone 的降维近似）
		com.stardew.craft.mining.MiningPlayerData miningData = com.stardew.craft.mining.MiningDataManager.getPlayerData(player);
		int maxReached = miningData != null ? miningData.getMaxFloorReached() : 0;
		if (maxReached >= 120) {
			// 基础 0.005%（约 1/20000），按采矿等级、每日幸运、幸运等级线性缩放
			double base = 0.00005;
			double shardChance = base + base * miningLevel * 0.008 + base * (dailyLuck / 2.0) + base * luckLevel * 0.05;
			if (r.nextDouble() < shardChance) {
				event.getDrops().add(makeDrop(level, pos, new ItemStack(ModItems.PRISMATIC_SHARD.get(), 1)));
			}
		}

		if (BookPowerEffects.shouldDropDiamondFromStone(PlayerDataManager.getPlayerData(player), r)) {
			event.getDrops().add(makeDrop(level, pos, new ItemStack(ModItems.DIAMOND.get(), 1)));
		}
	}

	private static int computeStardewOreCount(ServerPlayer player, BlockPos pos) {
		// Match Stardew Valley's node logic for copper/iron/gold/iridium/coal:
		// count = addedOres + r.Next(1, 4) + (rand < luckLevel/100 ? 1 : 0) + (rand < miningLevel/100 ? 1 : 0)
		// (note: SV uses LuckLevel (buff) here, not DailyLuck)
		int addedOres = PlayerStardewDataAPI.hasProfession(player, ProfessionType.MINER) ? 1 : 0;
		// SDV GameLocation.cs:14861 — Dwarf Statue _0: 每个采矿点 +1 矿石
		if (player.hasEffect(com.stardew.craft.effect.ModMobEffects.DWARF_STATUE_0)) {
			addedOres++;
		}
		int luckLevel = PlayerStardewDataAPI.getLuckBuffLevel(player);
		int miningLevel = PlayerStardewDataAPI.getSkillLevel(player, SkillType.MINING);

		RandomSource r = RandomSource.create(makeDailyPosSeed(pos));
		int count = addedOres + (r.nextInt(3) + 1);
		if (r.nextDouble() < (luckLevel / 100.0)) {
			count += 1;
		}
		if (r.nextDouble() < (miningLevel / 100.0)) {
			count += 1;
		}
		return Mth.clamp(count, 1, 999);
	}

	/**
	 * 公开供炸弹爆炸时复用：模拟"玩家挖掉这块矿/石头"的全部 SDV 风格掉落。
	 * <p>调用方应在调用本方法<b>之后</b>再 removeBlock。本方法只负责掉落（不破坏方块），
	 * 与 onBlockDrops 的逻辑保持一致：
	 * <ul>
	 *   <li>矿石：按 Miner/Geologist/采矿等级/幸运算 count；铱矿 3.5% 五彩碎片；记采矿经验</li>
	 *   <li>普通石头：调用方需另行掉落自身；本方法只补 5% 矿石额外掉落（25% 内出煤；Prospector x2）、
	 *       晶洞、玩家到过 120 层后基础 0.005% 五彩碎片；记采矿经验</li>
	 *   <li>矿物节点（水晶/火石英 等）：调用方掉落自身；本方法只记采矿经验</li>
	 * </ul>
	 * 仅在玩家非创造模式时调用；炸弹本身已限定矿井维度/可破坏方块。
	 *
	 * @return 矿石的产物 Item（用于让调用方知道矿石已被本方法处理，不要再掉落自身），其他方块返回 null
	 */
	public static Item applyPlayerStyleBombDrops(ServerLevel level, ServerPlayer player,
												 BlockPos pos, BlockState state) {
		if (!isStardewMineBlock(state)) {
			return null;
		}
		com.stardew.craft.player.PlayerDataManager.getPlayerData(player).addMineBlocksBombed(1);
		Item oreProduct = null;
		if (com.stardew.craft.festival.desert.DesertFestivalMineService.isCalicoEggStone(state)) {
			com.stardew.craft.festival.desert.DesertFestivalMineService.dropCalicoEggStone(level, player, pos, level.getRandom());
			tryDropBombSecretNote(level, player, pos, state);
			PlayerStardewDataAPI.addExperience(player, SkillType.MINING, 50);
			return ModItems.CALICO_EGG.get();
		}
		if (isStardewOre(state)) {
			oreProduct = getOreDropItem(state);
			if (oreProduct != null) {
				int count = isGemOre(state) ? computeGemOreCount(level, player) : computeStardewOreCount(player, pos);
				if (count <= 0) count = 1;
				net.minecraft.world.level.block.Block.popResource(level, pos, new ItemStack(oreProduct, count));
				if (state.is(ModTags.Blocks.IRIDIUM_ORES) && level.getRandom().nextDouble() < 0.035) {
					net.minecraft.world.level.block.Block.popResource(level, pos,
						new ItemStack(ModItems.PRISMATIC_SHARD.get(), 1));
				}
			}
		} else if (!isMineralBlock(state)) {
			rollBombStoneExtras(level, player, pos);
			com.stardew.craft.festival.desert.DesertFestivalMineService.tryAddStoneEggDrop(level, player, pos, level.getRandom());
		}
		tryDropBombSecretNote(level, player, pos, state);
		int miningExp = getMiningExperienceForBlock(state);
		if (miningExp > 0) {
			PlayerStardewDataAPI.addExperience(player, SkillType.MINING, miningExp);
		}
		return oreProduct;
	}

	private static void tryDropBombSecretNote(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		if (!state.is(ModTags.Blocks.STARDEW_STONES) && !state.is(ModTags.Blocks.STARDEW_ORES)) {
			return;
		}
		ItemStack secretNote = com.stardew.craft.secretnote.SecretNoteService
				.tryCreateFromSource(player, level.getRandom(),
						(float) (0.0075F * BOMB_INCIDENTAL_DROP_SCALE));
		if (!secretNote.isEmpty()) {
			net.minecraft.world.level.block.Block.popResource(level, pos, secretNote);
		}
	}

	/** 炸弹打石头时的额外掉落（晶洞/煤/五彩碎片），与 addGeodeDrop + addCoalFromStoneDrop 等价但直接 popResource。 */
	private static void rollBombStoneExtras(ServerLevel level, ServerPlayer player, BlockPos pos) {
		if (level.dimension() != ModMiningDimensions.STARDEW_MINING) return;

		int floorNumber = getFloorNumber(pos);
		int luckLevel = PlayerStardewDataAPI.getLuckLevel(player);
		int luckBuff = PlayerStardewDataAPI.getLuckBuffLevel(player);
		double dailyLuck = PlayerStardewDataAPI.getDailyLuck(player);
		int miningLevel = PlayerStardewDataAPI.getSkillLevel(player, SkillType.MINING);
		double chanceMod = dailyLuck / 2.0 + miningLevel * 0.005 + luckLevel * 0.001;
		int excavatorMul = PlayerStardewDataAPI.hasProfession(player, ProfessionType.EXCAVATOR) ? 2 : 1;
		RandomSource r = level.getRandom();

		// 主晶洞
		double dwarfGeodeMultiplier = player.hasEffect(com.stardew.craft.effect.ModMobEffects.DWARF_STATUE_4) ? 1.25 : 1.0;
		if (r.nextDouble() < GEODE_BASE_CHANCE * (1.0 + chanceMod) * excavatorMul
				* dwarfGeodeMultiplier * BOMB_INCIDENTAL_DROP_SCALE) {
			Item geode = pickGeodeItemForFloor(floorNumber);
			if (geode != null) {
				net.minecraft.world.level.block.Block.popResource(level, pos, new ItemStack(geode, 1));
			}
		}
		// 万能晶洞
		if (floorNumber >= 20 && r.nextDouble() < OMNI_GEODE_EXTRA_CHANCE * (1.0 + chanceMod)
				* excavatorMul * dwarfGeodeMultiplier * BOMB_INCIDENTAL_DROP_SCALE) {
			net.minecraft.world.level.block.Block.popResource(level, pos, new ItemStack(ModItems.OMNI_GEODE.get(), 1));
		}
		// 5% 矿石额外掉落 → 25% 内出煤（Prospector x2）
		double oreDropChance = 0.05 * (1.0 + (dailyLuck / 2.0 + miningLevel * 0.005 + luckBuff * 0.001));
		if (r.nextDouble() < oreDropChance * BOMB_INCIDENTAL_DROP_SCALE) {
			int burrowerMul = PlayerStardewDataAPI.hasProfession(player, ProfessionType.PROSPECTOR) ? 2 : 1;
			double dwarfCoalBonus = player.hasEffect(com.stardew.craft.effect.ModMobEffects.DWARF_STATUE_2) ? 0.10 : 0.0;
			if (r.nextDouble() < 0.25 * burrowerMul + dwarfCoalBonus) {
				net.minecraft.world.level.block.Block.popResource(level, pos, new ItemStack(ModItems.COAL.get(), 1));
			}
		}
		// 普通石头出五彩碎片（仅 120 层以上玩家）
		com.stardew.craft.mining.MiningPlayerData md = com.stardew.craft.mining.MiningDataManager.getPlayerData(player);
		int maxReached = md != null ? md.getMaxFloorReached() : 0;
		if (maxReached >= 120) {
			double base = 0.00005;
			double shardChance = base + base * miningLevel * 0.008 + base * (dailyLuck / 2.0) + base * luckBuff * 0.05;
			if (r.nextDouble() < shardChance * BOMB_INCIDENTAL_DROP_SCALE) {
				net.minecraft.world.level.block.Block.popResource(level, pos,
					new ItemStack(ModItems.PRISMATIC_SHARD.get(), 1));
			}
		}
	}

	private static long makeDailyPosSeed(BlockPos pos) {
		StardewTimeManager time = StardewTimeManager.get();
		int year = time.getCurrentYear();
		int season = time.getCurrentSeason();
		int day = time.getCurrentDay();
		int dateKey = ((year * 4) + season) * 28 + (day - 1);
		long seed = ((long) dateKey * 0x9E3779B97F4A7C15L) ^ pos.asLong() ^ 0xD1B54A32D192ED03L;
		return seed;
	}

	@SuppressWarnings("null")
	private static int getRequiredTier(BlockState state) {
		// 通过 tag 统一管理所有方块的挖掘等级（矿石 + 宝石 + 矿物节点 + 石头）
		if (state.is(ModTags.Blocks.REQUIRES_STARDEW_PICKAXE_TIER3)) {
			return 3;
		}
		if (state.is(ModTags.Blocks.REQUIRES_STARDEW_PICKAXE_TIER2)) {
			return 2;
		}
		if (state.is(ModTags.Blocks.REQUIRES_STARDEW_PICKAXE_TIER1)) {
			return 1;
		}
		return 0;
	}

	private static int getStardewPickaxeTier(ItemStack tool) {
		if (tool.getItem() instanceof StardewPickaxeItem stardewPickaxe) {
			return stardewPickaxe.getStardewTier();
		}
		@SuppressWarnings("null")
		var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(tool.getItem());
		if (key != null && StardewCraft.MODID.equals(key.getNamespace())) {
			return switch (key.getPath()) {
				case "pickaxe" -> 0;
				case "copper_pickaxe" -> 1;
				case "steel_pickaxe" -> 2;
				case "gold_pickaxe" -> 3;
				case "iridium_pickaxe" -> 4;
				default -> -1;
			};
		}
		return -1;
	}

	private static int getEffectiveStardewPickaxeTier(ItemStack tool) {
		int tier = getStardewPickaxeTier(tool);
		if (tier >= 0 && StardewEnchantments.has(tool, StardewEnchantments.POWERFUL)) {
			return tier + 1;
		}
		return tier;
	}

	@SuppressWarnings("null")
	private static boolean isPickaxeLike(ItemStack tool) {
		return tool.is(ItemTags.PICKAXES)
				|| tool.getItem() instanceof PickaxeItem
				|| tool.is(Items.WOODEN_PICKAXE);
	}

	@SuppressWarnings("null")
	private static boolean isStardewMineBlock(BlockState state) {
		return state.is(ModTags.Blocks.STARDEW_STONES)
				|| state.is(ModTags.Blocks.STARDEW_ORES)
				|| state.is(ModTags.Blocks.STARDEW_MINERALS);
	}

	@SuppressWarnings("null")
	private static boolean isStardewOre(BlockState state) {
		return state.is(ModTags.Blocks.STARDEW_ORES);
	}

	private static boolean isMineralBlock(BlockState state) {
		return state.is(ModTags.Blocks.STARDEW_MINERALS);
	}

	private static float computeDigSpeed(Player player, ItemStack tool, float baseToolSpeed) {
		float speed = baseToolSpeed;
		if (speed > 1.0F) {
			int efficiency = getItemEnchantmentLevel(player, tool, Enchantments.EFFICIENCY);
			if (efficiency > 0) {
				speed += (float) (efficiency * efficiency + 1);
			}
		}

		@SuppressWarnings("null")
		MobEffectInstance haste = player.getEffect(MobEffects.DIG_SPEED);
		if (haste != null) {
			speed *= 1.0F + 0.2F * (haste.getAmplifier() + 1);
		}

		@SuppressWarnings("null")
		MobEffectInstance fatigue = player.getEffect(MobEffects.DIG_SLOWDOWN);
		if (fatigue != null) {
			float mult;
			switch (fatigue.getAmplifier()) {
				case 0 -> mult = 0.3F;
				case 1 -> mult = 0.09F;
				case 2 -> mult = 0.0027F;
				case 3 -> mult = 8.1E-4F;
				default -> mult = 2.43E-4F;
			}
			speed *= mult;
		}

		if (player.isInWater() && !hasAquaAffinity(player)) {
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

	@SuppressWarnings({ "null", "deprecation" })
	private static boolean hasAquaAffinity(Player player) {
		@SuppressWarnings("null")
		var lookup = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		@SuppressWarnings("null")
		var holder = lookup.getOrThrow(Enchantments.AQUA_AFFINITY);
		for (ItemStack armor : player.getArmorSlots()) {
			if (EnchantmentHelper.getItemEnchantmentLevel(holder, armor) > 0) {
				return true;
			}
		}
		return false;
	}
}
