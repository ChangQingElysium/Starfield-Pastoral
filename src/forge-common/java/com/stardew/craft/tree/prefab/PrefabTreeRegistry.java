package com.stardew.craft.tree.prefab;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 每个维度持久化的预制树登记表。
 *
 * <p>用 {@link PrefabTreeInstance#members()} 记录每棵树占用的所有方块坐标，供：
 * <ul>
 *   <li>破坏保护：落在某棵树 members 内、且非树根的方块不可被普通破坏；</li>
 *   <li>砍伐：长按树根时整树倒下（移除全部 members）。</li>
 * </ul>
 *
 * <p>内存里额外维护 member->instance 的反查索引，破坏事件里 O(1) 命中。
 */
public final class PrefabTreeRegistry extends SavedData {
	private static final String DATA_NAME = "stardew_prefab_trees";

	private final Map<BlockPos, PrefabTreeInstance> byRoot = new HashMap<>();
	private final Map<BlockPos, PrefabTreeInstance> byMember = new HashMap<>();

	public static PrefabTreeRegistry get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(
				PrefabTreeRegistry::load,
				PrefabTreeRegistry::new,
				DATA_NAME);
	}

	private void index(PrefabTreeInstance instance) {
		byRoot.put(instance.root(), instance);
		for (BlockPos pos : instance.members()) {
			byMember.put(pos, instance);
		}
	}

	public void register(BlockPos root, String species, int variant, Set<BlockPos> members) {
		PrefabTreeInstance existing = byRoot.remove(root.immutable());
		if (existing != null) {
			for (BlockPos pos : existing.members()) {
				byMember.remove(pos);
			}
		}
		PrefabTreeInstance instance = PrefabTreeInstance.ofMembers(root, species, variant, members);
		index(instance);
		setDirty();
	}

	public PrefabTreeInstance getByMember(BlockPos pos) {
		return byMember.get(pos);
	}

	public PrefabTreeInstance getByRoot(BlockPos pos) {
		return byRoot.get(pos);
	}

	public boolean isMember(BlockPos pos) {
		return byMember.containsKey(pos);
	}

	/** 砍倒后：实例转为树桩状态（members 只剩树根）。 */
	public void markFelled(PrefabTreeInstance instance) {
		for (BlockPos pos : instance.members()) {
			byMember.remove(pos);
		}
		instance.markFelled();
		byMember.put(instance.root(), instance);
		setDirty();
	}

	public void unregister(PrefabTreeInstance instance) {
		byRoot.remove(instance.root());
		for (BlockPos pos : instance.members()) {
			byMember.remove(pos);
		}
		setDirty();
	}

	@Override
	public CompoundTag save(CompoundTag tag) {
		ListTag list = new ListTag();
		for (PrefabTreeInstance instance : byRoot.values()) {
			CompoundTag t = new CompoundTag();
			t.putLong("Root", instance.root().asLong());
			t.putString("Species", instance.species());
			t.putInt("Variant", instance.variant());
			t.putBoolean("Felled", instance.felled());
			t.putInt("BombDamage", instance.bombDamage());
			long[] members = new long[instance.members().size()];
			int i = 0;
			for (BlockPos pos : instance.members()) {
				members[i++] = pos.asLong();
			}
			t.putLongArray("Members", members);
			list.add(t);
		}
		tag.put("Trees", list);
		return tag;
	}

	public static PrefabTreeRegistry load(CompoundTag tag) {
		PrefabTreeRegistry data = new PrefabTreeRegistry();
		ListTag list = tag.getList("Trees", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompound(i);
			BlockPos root = BlockPos.of(t.getLong("Root"));
			String species = t.getString("Species");
			int variant = t.getInt("Variant");
			boolean felled = t.getBoolean("Felled");
			Set<BlockPos> members = new HashSet<>();
			for (long packed : t.getLongArray("Members")) {
				members.add(BlockPos.of(packed));
			}
			members.add(root);
			var instance = new PrefabTreeInstance(root, species, variant, members, felled);
			instance.setBombDamage(t.getInt("BombDamage"));
			data.index(instance);
		}
		return data;
	}
}
