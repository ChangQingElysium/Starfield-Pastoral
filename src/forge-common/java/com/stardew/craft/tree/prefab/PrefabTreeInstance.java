package com.stardew.craft.tree.prefab;

import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.Set;

/**
 * 世界中一棵已放置的预制树实例。
 *
 * <p>{@code members} 是放置时记录的所有「属于这棵树」的方块世界坐标（含树根、原木、树枝、
 * 树叶以及栏杆等装饰）。砍伐 / 破坏保护都基于这个集合判定——因为预制树用到原版方块
 * （栏杆等），无法靠方块类型区分，只能靠放置时登记的占地。
 *
 * <p>砍倒后转为「树桩」状态：{@code felled=true}，{@code members} 只剩树根那一格，
 * 再砍一次即移除树桩。
 */
public final class PrefabTreeInstance {
	private final BlockPos root;
	private final String species;
	private final int variant;
	private final Set<BlockPos> members;
	private boolean felled;
	private int bombDamage;

	public PrefabTreeInstance(BlockPos root, String species, int variant, Set<BlockPos> members, boolean felled) {
		this.root = root.immutable();
		this.species = species;
		this.variant = variant;
		this.members = members;
		this.felled = felled;
	}

	public BlockPos root() {
		return root;
	}

	public String species() {
		return species;
	}

	public int variant() {
		return variant;
	}

	public Set<BlockPos> members() {
		return members;
	}

	public boolean felled() {
		return felled;
	}

	public int bombDamage() { return bombDamage; }
	void setBombDamage(int damage) { bombDamage = Math.max(0, damage); }
	boolean damageByBomb(int damage) {
		bombDamage += damage;
		return bombDamage >= (felled ? 5 : 10);
	}

	void markFelled() {
		this.felled = true;
		this.bombDamage = 0;
		this.members.clear();
		this.members.add(root);
	}

	static PrefabTreeInstance ofMembers(BlockPos root, String species, int variant, Set<BlockPos> source) {
		Set<BlockPos> copy = new HashSet<>(source.size());
		for (BlockPos pos : source) {
			copy.add(pos.immutable());
		}
		copy.add(root.immutable());
		return new PrefabTreeInstance(root, species, variant, copy, false);
	}
}
