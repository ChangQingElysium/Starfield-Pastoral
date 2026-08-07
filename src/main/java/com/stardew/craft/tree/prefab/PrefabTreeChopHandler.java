package com.stardew.craft.tree.prefab;

import com.stardew.craft.book.BookPowerEffects;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.enchantment.StardewEnchantments;
import com.stardew.craft.entity.FallenPrefabTreeEntity;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.tool.StardewAxeItem;
import com.stardew.craft.manager.WildTreeSeedManager;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.ProfessionType;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.secretnote.SecretNoteService;
import com.stardew.craft.tree.WildTrees;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 预制树的砍伐与破坏保护。由 {@code WildTreeChopEvents.onBlockBreak} 在顶部转发进来：
 * 凡是落在某棵预制树占地（{@link PrefabTreeInstance#members()}）内的方块都归这里处理。
 *
 * <p>行为对齐旧版野生树砍伐与 Stardew Valley 1.6 源码（Tree.cs / Axe.cs）：长按树根 → 除树根外
 * 整树倒下（动画 + treecrack/treethud + 粒子）→ 掉落原木（每个 log/branch/原木楼梯/原木台阶 1 个，
 * 再叠 Forester +25%、伐木书 5% 翻倍、Shaving 加成）+ 树种（采集≥1 时按 seedOnChopChance≈75%，1-2 颗）
 * + 5 树液（仅橡/枫/松）→ 树根留作可再砍的树桩。其余组件平时不可破坏。
 *
 * <p>能量与 SV 一致：每下 {@code 2 - 采集*0.1}，满树按斧档位 10/8/6/4/2 下（Efficient 附魔免能量）。
 * 摇树掉种走现成的 {@code WildTreeShakeEvents} + {@code WildTreeSeedManager}（放置时已登记）。
 *
 * <p>已知与 SV 的偏差：Lumberjack「任意树掉硬木」对预制普通树暂不实现（原木模型下无独立硬木掉落物）。
 */
public final class PrefabTreeChopHandler {
	private PrefabTreeChopHandler() {
	}

	// 必须与 sounds/tree/tree_crack.ogg 时长一致（20 ticks = 1s）。
	private static final int FALL_ANIM_TICKS = 20;
	// 采集经验：每个木质方块（原木/树枝/原木楼梯/原木台阶）给 1 XP；树桩再给 1 XP。
	private static final int XP_REMOVE_STUMP = 1;
	private static final int SAP_ON_FALL = 5;

	/**
	 * @return true 表示本次破坏属于预制树、已被本处理器接管（调用方应直接 return）
	 */
	public static boolean onBlockBreak(ServerPlayer player, ServerLevel level, BlockPos pos, BlockEvent.BreakEvent event) {
		PrefabTreeRegistry reg = PrefabTreeRegistry.get(level);
		PrefabTreeInstance inst = reg.getByMember(pos);
		if (inst == null) {
			// 收养老存档里的「算法生成现代树」（oak_root/log/branch/leaves，不在登记表里）：
			// 登记成预制实例后，与预制树共用同一套砍伐/掉落/树桩/保护逻辑。
			inst = tryAdoptModernTree(level, reg, pos);
			if (inst == null) {
				return false;
			}
		}

		// 创造模式：整树清除，无掉落。
		if (player.isCreative()) {
			clearAll(level, reg, inst);
			event.setCanceled(true);
			return true;
		}

		// 非树根组件：受保护，不可普通破坏，提示「请砍伐树根部位」。
		if (!pos.equals(inst.root())) {
			event.setCanceled(true);
			com.stardew.craft.network.payload.HudHintPayload.send(player, "stardewcraft.message.tree.chop_root");
			return true;
		}

		// 生存模式砍树根。
		ItemStack tool = player.getMainHandItem();
		if (!isAxeLike(tool)) {
			event.setCanceled(true);
			return true;
		}
		if (inst.felled()) {
			removeStump(level, reg, inst, player, pos, tool);
		} else {
			fell(level, reg, inst, player, pos, tool);
		}
		event.setCanceled(true);
		return true;
	}

	/**
	 * 把老存档里的算法生成现代树「收养」为预制实例：成员 = 连通木质 + 相连树叶。
	 * 之后砍伐/掉落/树桩/保护全部走预制逻辑，与预制树完全一致。variant=0 标记为收养树。
	 */
	private static PrefabTreeInstance tryAdoptModernTree(ServerLevel level, PrefabTreeRegistry reg, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		WildTrees.Def def = WildTrees.findByModernPart(state);
		if (def == null) {
			return null;
		}
		BlockPos root = WildTrees.findGeneratedModernRoot(level, pos, def);
		if (root == null) {
			return null;
		}
		PrefabTreeInstance existing = reg.getByRoot(root);
		if (existing != null) {
			return existing;
		}
		Set<BlockPos> members = WildTrees.collectGeneratedModernTreeMembers(level, root, def);
		Block logBlock = def.modernLog().get();
		boolean hasLog = false;
		for (BlockPos m : members) {
			if (level.getBlockState(m).getBlock() == logBlock) {
				hasLog = true;
				break;
			}
		}
		if (!hasLog) {
			return null;
		}
		reg.register(root, def.id(), 0, members);
		WildTrees.markGeneratedModernTree(level, root, def);
		return reg.getByRoot(root);
	}

	private static void fell(ServerLevel level, PrefabTreeRegistry reg, PrefabTreeInstance inst, ServerPlayer player, BlockPos root, ItemStack tool) {
		WildTrees.Def def = PrefabTrees.defById(inst.species());
		if (def == null) {
			clearAll(level, reg, inst);
			return;
		}
		if (!consumeChopEnergy(player, level, tool, hitsToFell(tool))) {
			return;
		}
		Block logBlock = def.modernLog().get();

		Direction dir = computeFallDirection(player, root);
		List<FallenPrefabTreeEntity.Piece> pieces = new ArrayList<>();
		int logCount = 0;
		for (BlockPos m : inst.members()) {
			if (m.equals(root)) {
				continue;
			}
			BlockState s = level.getBlockState(m);
			if (s.isAir()) {
				continue;
			}
			// 原木 = 原木 / 树枝 / 原木楼梯 / 原木台阶，各算 1 个原木。
			if (PrefabTrees.countsAsLog(def, s.getBlock())) {
				logCount++;
			}
			pieces.add(new FallenPrefabTreeEntity.Piece(
					m.getX() - root.getX(), m.getY() - root.getY(), m.getZ() - root.getZ(), s));
		}

		// 掉落：原木（叠 SV 修正）+ 树种 + 树液。
		List<ItemStack> drops = new ArrayList<>();
		int woodAmount = applyWoodModifiers(level, def, player, tool, logCount);
		addItemStacks(drops, PrefabTrees.logItem(def), woodAmount);
		ItemStack seeds = rollSeedsOnChop(level, def, player);
		if (!seeds.isEmpty()) {
			drops.add(seeds);
		}
		if (isSapTree(def)) {
			drops.add(new ItemStack(ModItems.SAP.get(), SAP_ON_FALL));
		}
		// SV: 伐木工让任意（非硬木）树也掉硬木——几何分布 while(rand.nextBool()) n++，平均约 1。
		if (!isHardwood(def) && PlayerStardewDataAPI.hasProfession(player, ProfessionType.LUMBERJACK)) {
			int hardwood = 0;
			while (hardwood < 16 && level.random.nextBoolean()) {
				hardwood++;
			}
			if (hardwood > 0) {
				drops.add(new ItemStack(ModItems.WOOD_HARD.get(), hardwood));
			}
		}
		ItemStack secretNote = SecretNoteService.tryCreateFromSource(player, level.random, 0.005F);
		if (!secretNote.isEmpty()) {
			drops.add(secretNote);
		}

		// 移除除树根外的全部方块。
		for (BlockPos m : inst.members()) {
			if (m.equals(root)) {
				continue;
			}
			if (!level.getBlockState(m).isAir()) {
				level.removeBlock(m, false);
			}
		}
		reg.markFelled(inst);

		FallenPrefabTreeEntity.spawn(level, root, dir, pieces, FALL_ANIM_TICKS, drops, logBlock.defaultBlockState());

		WildTreeSeedManager.get(level).untrackTree(level, root);
		// 每个木质方块给 1 采集经验。
		if (logCount > 0) {
			PlayerStardewDataAPI.addExperience(player, SkillType.FORAGING, logCount);
		}
	}

	private static void removeStump(ServerLevel level, PrefabTreeRegistry reg, PrefabTreeInstance inst, ServerPlayer player, BlockPos root, ItemStack tool) {
		WildTrees.Def def = PrefabTrees.defById(inst.species());
		if (!consumeChopEnergy(player, level, tool, Math.max(1, hitsToFell(tool) / 2))) {
			return;
		}
		BlockState rootState = level.getBlockState(root);
		level.removeBlock(root, false);
		playStumpEffects(level, root, def, rootState);
		if (def != null) {
			int amount = applyWoodModifiers(level, def, player, tool, 1 + level.random.nextInt(2));
			List<ItemStack> stumpDrops = new ArrayList<>();
			addItemStacks(stumpDrops, PrefabTrees.logItem(def), amount);
			if (isSapTree(def)) {
				stumpDrops.add(new ItemStack(ModItems.SAP.get(), 2));
			}
			for (ItemStack s : stumpDrops) {
				Block.popResource(level, root, s);
			}
		}
		reg.unregister(inst);
		WildTreeSeedManager.get(level).untrackTree(level, root);
		PlayerStardewDataAPI.addExperience(player, SkillType.FORAGING, XP_REMOVE_STUMP);
	}

	private static void clearAll(ServerLevel level, PrefabTreeRegistry reg, PrefabTreeInstance inst) {
		for (BlockPos m : new ArrayList<>(inst.members())) {
			if (!level.getBlockState(m).isAir()) {
				level.removeBlock(m, false);
			}
		}
		reg.unregister(inst);
		WildTreeSeedManager.get(level).untrackTree(level, inst.root());
	}

	// ── SV 对齐的掉落/能量 ───────────────────────────────────────────────

	/** SV: 满树血量 10，按斧档位伤害 1/1.25/1.67/2.5/5 → 砍倒需 10/8/6/4/2 下。 */
	private static int hitsToFell(ItemStack tool) {
		int tier = 0;
		if (tool.getItem() instanceof StardewAxeItem axe) {
			tier = axe.getStardewTier().ordinal();
			if (StardewEnchantments.has(tool, StardewEnchantments.POWERFUL)) {
				tier = Math.min(4, tier + 2);
			}
		}
		return switch (tier) {
			case 1 -> 8;
			case 2 -> 6;
			case 3 -> 4;
			case 4 -> 2;
			default -> 10;
		};
	}

	/** SV: 每下能量 {@code 2 - 采集*0.1}（与工具档位无关）；总能量 = 下数 × 每下。 */
	private static boolean consumeChopEnergy(ServerPlayer player, ServerLevel level, ItemStack tool, int hits) {
		if (player.isCreative()) {
			return true;
		}
		if (level.dimension() != ModDimensions.STARDEW_VALLEY) {
			return true;
		}
		if (StardewEnchantments.has(tool, StardewEnchantments.EFFICIENT)) {
			return true;
		}
		int foraging = PlayerStardewDataAPI.getSkillLevel(player, SkillType.FORAGING);
		float perSwing = Math.max(0.1F, 2.0F - foraging * 0.1F);
		return PlayerStardewDataAPI.consumeEnergyOrNotify(player, hits * perSwing);
	}

	/** 在原木基数上叠加 SV 修正：Forester +25%（非硬木）、Shaving 加成、伐木书 5% 翻倍。 */
	private static int applyWoodModifiers(ServerLevel level, WildTrees.Def def, ServerPlayer player, ItemStack tool, int base) {
		if (base <= 0) {
			return 0;
		}
		int amount = base;
		boolean hardwood = isHardwood(def);
		if (!hardwood && PlayerStardewDataAPI.hasProfession(player, ProfessionType.FORESTER)) {
			amount = Math.max(1, Mth.floor(amount * 1.25F));
		}
		if (StardewEnchantments.has(tool, StardewEnchantments.SHAVING)) {
			amount += hardwood ? (level.random.nextInt(2) + 1) : (level.random.nextInt(4) + 2);
		}
		return BookPowerEffects.applyWoodcuttingDouble(PlayerDataManager.getPlayerData(player), amount, level.random);
	}

	/** SV: 采集≥1 且 rand &lt; seedOnChopChance（橡/枫/松 0.75，桃花心木 0.5625，神秘树 0）→ 1-2 颗树种。 */
	private static ItemStack rollSeedsOnChop(ServerLevel level, WildTrees.Def def, ServerPlayer player) {
		if (PlayerStardewDataAPI.getSkillLevel(player, SkillType.FORAGING) < 1) {
			return ItemStack.EMPTY;
		}
		if (level.random.nextFloat() >= def.seedOnChopChance()) {
			return ItemStack.EMPTY;
		}
		Item seed = WildTreeSeedManager.getSeedItem(def);
		if (seed == null) {
			return ItemStack.EMPTY;
		}
		return new ItemStack(seed, 1 + level.random.nextInt(2));
	}

	private static void addItemStacks(List<ItemStack> out, Item item, int count) {
		if (item == null || count <= 0) {
			return;
		}
		int max = new ItemStack(item).getMaxStackSize();
		int remaining = count;
		while (remaining > 0) {
			int n = Math.min(max, remaining);
			out.add(new ItemStack(item, n));
			remaining -= n;
		}
	}

	private static boolean isHardwood(WildTrees.Def def) {
		String id = def.id();
		return "mahogany".equals(id) || "mystic_tree".equals(id);
	}

	private static boolean isSapTree(WildTrees.Def def) {
		String id = def.id();
		return "oak".equals(id) || "maple".equals(id) || "pine".equals(id);
	}

	@SuppressWarnings({ "null", "deprecation" })
	private static void playStumpEffects(ServerLevel level, BlockPos pos, WildTrees.Def def, BlockState rootState) {
		BlockState dust = def != null ? def.modernLog().get().defaultBlockState() : rootState;
		var breakSound = dust.getSoundType().getBreakSound();
		level.playSound(null, pos, breakSound, SoundSource.BLOCKS, 0.8F, 1.1F);
		level.sendParticles(
				new BlockParticleOption(ParticleTypes.BLOCK, dust),
				pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
				18, 0.35, 0.35, 0.35, 0.05);
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
	private static boolean isAxeLike(ItemStack tool) {
		if (tool.isEmpty()) {
			return false;
		}
		if (tool.is(ItemTags.AXES)) {
			return true;
		}
		if (tool.getItem() instanceof StardewAxeItem) {
			return true;
		}
		return tool.canPerformAction(ItemAbilities.AXE_DIG);
	}
}
