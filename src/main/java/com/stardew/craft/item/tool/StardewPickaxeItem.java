package com.stardew.craft.item.tool;

import com.stardew.craft.item.IStardewItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

@SuppressWarnings("null")
public class StardewPickaxeItem extends PickaxeItem implements IStardewItem {
	private final int stardewTier;
	private final float vanillaLikeSpeed;
	private final float extraVanillaSpeed;

	/**
	 * @param stardewTier 0..4, used for mod-specific gating.
	 * @param tier        Vanilla tier (WOOD/STONE/IRON/DIAMOND/NETHERITE) for normal MC mining.
	 * @param extraVanillaSpeed Extra speed added on vanilla pickaxe-mineable blocks (used for tier4 being "a bit faster").
	 */
	public StardewPickaxeItem(int stardewTier, Tier tier, float extraVanillaSpeed, Properties properties) {
		super(tier, properties.stacksTo(1)
				.component(DataComponents.UNBREAKABLE, new Unbreakable(false)));
		this.stardewTier = stardewTier;
		this.vanillaLikeSpeed = tier.getSpeed();
		this.extraVanillaSpeed = extraVanillaSpeed;
	}

	public int getStardewTier() {
		return stardewTier;
	}

	/**
	 * Base mining speed used by our BreakSpeed override logic.
	 */
	public float getBaseMiningSpeed() {
		return vanillaLikeSpeed + extraVanillaSpeed;
	}

	@Override
	public boolean mineBlock(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") LivingEntity entityLiving) {
		return true; // 不调用 super 避免任何耗久逻辑
	}

	@Override
	public boolean hurtEnemy(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") LivingEntity target, @SuppressWarnings("null") LivingEntity attacker) {
		return true; // 不调用 super 避免任何耗久逻辑
	}

	@Override
	public String getItemTypeKey() {
		return "stardewcraft.type.tool";
	}

	@Override
	public int getSellPrice(ItemStack stack) {
		// 星露谷工具不可出售
		return -1;
	}

	@Override
	public boolean isEnchantable(@SuppressWarnings("null") ItemStack stack) {
		return stack.getMaxStackSize() == 1;
	}

	@Override
	public int getEnchantmentValue() {
		return Math.max(1, (stardewTier + 1) * 5);
	}

	@SuppressWarnings("null")
	@Override
	public float getDestroySpeed(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") BlockState state) {
		// Keep normal vanilla behavior, but allow tier4 to be slightly faster on vanilla pickaxe-mineables.
		@SuppressWarnings("null")
		float base = super.getDestroySpeed(stack, state);
		if (extraVanillaSpeed > 0 && state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
			return base + extraVanillaSpeed;
		}
		return base;
	}
}
