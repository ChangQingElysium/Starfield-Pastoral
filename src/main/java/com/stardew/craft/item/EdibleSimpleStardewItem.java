package com.stardew.craft.item;

import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public class EdibleSimpleStardewItem extends SimpleStardewItem {
	private final int energy;
	private final int health;
	private final boolean drink;

	public EdibleSimpleStardewItem(String typeKey, int sellPrice, int energy, int health, boolean drink, Properties properties) {
		super(typeKey, sellPrice, properties.food(new FoodProperties.Builder()
				.nutrition(1)
				.saturationMod(0.1f)
				.alwaysEat()
				.build()));
		this.energy = energy;
		this.health = health;
		this.drink = drink;
	}

	@Override
	public boolean isFood() {
		return true;
	}

	@Override
	public int getEnergy(ItemStack stack) {
		return energy;
	}

	@Override
	public int getHealth(ItemStack stack) {
		return health;
	}

	@Override
	public UseAnim getUseAnimation(@javax.annotation.Nonnull ItemStack stack) {
		return drink ? UseAnim.DRINK : super.getUseAnimation(stack);
	}

	@Override
	public @javax.annotation.Nonnull ItemStack finishUsingItem(@javax.annotation.Nonnull ItemStack stack,
			@javax.annotation.Nonnull Level level, @javax.annotation.Nonnull LivingEntity livingEntity) {
		ItemStack result = super.finishUsingItem(stack, level, livingEntity);
		if (!level.isClientSide && livingEntity instanceof ServerPlayer serverPlayer) {
			if (health != 0) {
				int current = PlayerStardewDataAPI.getHealth(serverPlayer);
				int max = PlayerStardewDataAPI.getMaxHealth(serverPlayer);
				PlayerStardewDataAPI.setHealth(serverPlayer, Math.max(0, Math.min(max, current + health)));
			}
			if (energy != 0) {
				if (energy > 0) {
					PlayerStardewDataAPI.restoreEnergy(serverPlayer, energy);
				} else {
					PlayerStardewDataAPI.consumeEnergy(serverPlayer, -energy);
				}
			}
		}
		return result;
	}
}