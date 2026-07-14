package com.stardew.craft.item.tool;

import com.stardew.craft.item.IStardewItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class ScytheItem extends Item implements IStardewItem {
	public enum Tier {
		// 范围几何统一在事件里按“前方扇形”实现。
		// Tier 差异用于：冷却、以及（铱）更广泛的作物收割能力。
		NORMAL,
		GOLD,
		IRIDIUM
	}

	private final Tier tier;

	public ScytheItem(Tier tier, Properties properties) {
		super(properties.stacksTo(1));
		this.tier = tier;
	}

	public Tier getTier() {
		return tier;
	}

	public int getCooldownTicks() {
		return 10;
	}

	@Override
	public String getItemTypeKey() {
		return "stardewcraft.type.tool";
	}

	@Override
	public boolean isEnchantable(@SuppressWarnings("null") net.minecraft.world.item.ItemStack stack) {
		return stack.getMaxStackSize() == 1;
	}

	@Override
	public int getEnchantmentValue() {
		return switch (tier) {
			case NORMAL -> 5;
			case GOLD -> 15;
			case IRIDIUM -> 20;
		};
	}

	@Override
	public boolean canAttackBlock(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") Player player) {
		// 镰刀不用于挖掘/破坏方块；左键逻辑由事件驱动（收割/清理）。
		return false;
	}
}
