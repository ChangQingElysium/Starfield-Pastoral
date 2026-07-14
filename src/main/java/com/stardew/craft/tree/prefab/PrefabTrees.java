package com.stardew.craft.tree.prefab;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.tree.WildTrees;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * 预制树（结构树）的静态映射工具。
 *
 * <p>新树系统由算法生成改为「预制树」：每个树种有 5 个手工制作的结构变体，
 * 编号 1_1 .. 5_5（首位是树种：1=橡树 2=枫树 3=松树 4=桃花心木 5=神秘树；末位是变体 1-5）。
 * 变体占地：1 = 5x5，2/3/4 = 7x7，5 = 9x9。
 *
 * <p>结构文件为 vanilla NBT 结构格式，放在 resources 的
 * {@code data/stardewcraft/structures/tree/<idx>_<variant>.nbt}。
 */
public final class PrefabTrees {
	private PrefabTrees() {
	}

	public static final int VARIANTS_PER_SPECIES = 5;
	private static final String STRUCTURE_DIR = "data/stardewcraft/structures/tree/";

	/** 1=oak 2=maple 3=pine 4=mahogany 5=mystic_tree（与文件命名首位一致）。 */
	public static int speciesIndex(WildTrees.Def def) {
		return switch (def.id()) {
			case "oak" -> 1;
			case "maple" -> 2;
			case "pine" -> 3;
			case "mahogany" -> 4;
			case "mystic_tree" -> 5;
			default -> 0;
		};
	}

	public static WildTrees.Def defById(String id) {
		for (WildTrees.Def def : WildTrees.ALL) {
			if (def.id().equals(id)) {
				return def;
			}
		}
		return null;
	}

	/** 资源路径，如 {@code data/stardewcraft/structures/tree/1_3.nbt}。 */
	public static String structurePath(int speciesIndex, int variant) {
		return STRUCTURE_DIR + speciesIndex + "_" + variant + ".nbt";
	}

	/** 砍伐时掉落的「原木」物品（每个算作原木的方块掉 1 个）。 */
	public static Item logItem(WildTrees.Def def) {
		return switch (def.id()) {
			case "oak" -> ModItems.OAK_LOG.get();
			case "maple" -> ModItems.MAPLE_LOG.get();
			case "pine" -> ModItems.PINE_LOG.get();
			case "mahogany" -> ModItems.MAHOGANY_LOG.get();
			case "mystic_tree" -> ModItems.MYSTIC_TREE_LOG.get();
			default -> null;
		};
	}

	/**
	 * 判断一个方块是否算作「原木」用于掉落计数：原木、树枝，以及原木楼梯 / 原木台阶。
	 * （木板及其楼梯/台阶/栅栏等是加工件，属装饰，不计入掉落。）
	 */
	public static boolean countsAsLog(WildTrees.Def def, Block block) {
		if (block == def.modernLog().get() || block == def.modernBranch().get()) {
			return true;
		}
		DeferredBlock<? extends Block> stairs = ModBlocks.NEW_TREE_BUILDING_BLOCKS.get(def.id() + "_log_stairs");
		DeferredBlock<? extends Block> slab = ModBlocks.NEW_TREE_BUILDING_BLOCKS.get(def.id() + "_log_slab");
		return (stairs != null && block == stairs.get()) || (slab != null && block == slab.get());
	}
}
