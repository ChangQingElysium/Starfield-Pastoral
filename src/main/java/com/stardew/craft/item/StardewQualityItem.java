package com.stardew.craft.item;

import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Stardew-style object item with optional quality scaling.
 * Uses Stardew Valley formulas for price/energy/health.
 */
public class StardewQualityItem extends Item implements IStardewItem {
	private static final int[] SDV_QUALITY = new int[] { 0, 1, 2, 4 };
	private static final float[] PRICE_MULTIPLIERS = new float[] { 1.0f, 1.25f, 1.5f, 2.0f };
	private static final int INEDIBLE_THRESHOLD = -300;

	private final String typeKey;
	private final int basePrice;
	private final int edibility;
	private final boolean supportsQuality;
	private final boolean edible;
	private final int[] priceByQuality;
	private final int[] energyByQuality;
	private final int[] healthByQuality;
	private final boolean drinkAnimation;

	@SuppressWarnings("null")
	public StardewQualityItem(String typeKey, int basePrice, int edibility, boolean supportsQuality, Properties properties) {
		this(typeKey, basePrice, edibility, supportsQuality, properties, false);
	}

	@SuppressWarnings("null")
	public StardewQualityItem(String typeKey, int basePrice, int edibility, boolean supportsQuality, Properties properties, boolean drinkAnimation) {
		this(typeKey, basePrice, edibility, supportsQuality, properties, drinkAnimation, 0.45f);
	}

	/** healthRatio：原版 Object.healthRecoveredOnConsumption 的体力→生命系数（默认 0.45，Bug Steak 0.68，Energy Tonic 0）。 */
	@SuppressWarnings("null")
	public StardewQualityItem(String typeKey, int basePrice, int edibility, boolean supportsQuality, Properties properties, boolean drinkAnimation, float healthRatio) {
		super(isEdible(edibility)
				? properties.food(new FoodProperties.Builder().nutrition(2).saturationMod(0.3f).alwaysEat().build())
				: properties);
		this.typeKey = typeKey;
		this.basePrice = basePrice;
		this.edibility = edibility;
		this.supportsQuality = supportsQuality;
		this.edible = isEdible(edibility);
		this.priceByQuality = buildPriceByQuality(basePrice);
		this.energyByQuality = buildEnergyByQuality(edibility);
		this.healthByQuality = buildHealthByQuality(edibility, this.energyByQuality, healthRatio);
		this.drinkAnimation = drinkAnimation;
	}

	private static boolean isEdible(int edibility) {
		return edibility > INEDIBLE_THRESHOLD;
	}

	private static int[] buildPriceByQuality(int basePrice) {
		int[] out = new int[4];
		for (int i = 0; i < 4; i++) {
			out[i] = (int) Math.floor(basePrice * PRICE_MULTIPLIERS[i]);
		}
		return out;
	}

	private static int[] buildEnergyByQuality(int edibility) {
		int[] out = new int[] { 0, 0, 0, 0 };
		if (!isEdible(edibility)) {
			return out;
		}
		int base = (int) Math.ceil(edibility * 2.5);
		for (int i = 0; i < 4; i++) {
			out[i] = base + SDV_QUALITY[i] * edibility;
		}
		return out;
	}

	private static int[] buildHealthByQuality(int edibility, int[] energyByQuality, float healthRatio) {
		int[] out = new int[4];
		if (edibility < 0) {
			return out; // 原版 Object.healthRecoveredOnConsumption：Edibility<0 → 0，不扣血
		}
		for (int i = 0; i < 4; i++) {
			out[i] = (int) (energyByQuality[i] * healthRatio);
		}
		return out;
	}

	@Override
	public String getItemTypeKey() {
		return typeKey;
	}

	public boolean supportsQuality() {
		return supportsQuality;
	}

	@SuppressWarnings("null")
	@Override
	public Component getName(@SuppressWarnings("null") ItemStack stack) {
		if (!supportsQuality) {
			return Component.translatable(this.getDescriptionId(stack)).withStyle(ChatFormatting.WHITE);
		}

		int quality = QualityHelper.getQuality(stack);
		Component prefix = QualityHelper.getQualityPrefix(quality);
		Component baseName = Component.translatable(this.getDescriptionId(stack)).withStyle(ChatFormatting.WHITE);


		if (quality == QualityHelper.NORMAL) {
			return baseName;
		}

		return Component.empty().append(prefix).append(baseName);
	}

	@Override
	public @Nonnull ItemStack finishUsingItem(@Nonnull ItemStack stack, @Nonnull net.minecraft.world.level.Level level,
			@Nonnull net.minecraft.world.entity.LivingEntity livingEntity) {
		ItemStack safeStack = Objects.requireNonNull(stack, "stack");
		var safeLevel = Objects.requireNonNull(level, "level");
		var safeEntity = Objects.requireNonNull(livingEntity, "livingEntity");
		if (!edible) {
			return Objects.requireNonNull(super.finishUsingItem(safeStack, safeLevel, safeEntity), "result");
		}

		int qualityIndex = getQualityIndex(safeStack);
		int health = healthByQuality[qualityIndex];
		int energy = energyByQuality[qualityIndex];
		if (!level.isClientSide
				&& livingEntity instanceof net.minecraft.server.level.ServerPlayer serverPlayer
				&& safeLevel instanceof net.minecraft.server.level.ServerLevel serverLevel
				&& com.stardew.craft.festival.desert.DesertFestivalMineService.isInFestivalSkullCavern(serverPlayer)
				&& com.stardew.craft.festival.desert.DesertFestivalMineService.meagerMealsActive(serverLevel)) {
			if (health > 0) {
				health = Math.max(1, health / 2);
			}
			if (energy > 0) {
				energy = Math.max(1, energy / 2);
			}
		}

		ItemStack result = super.finishUsingItem(safeStack, safeLevel, safeEntity);

		if (!level.isClientSide && livingEntity instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
			if (health != 0) {
				int currentSDHealth = com.stardew.craft.player.PlayerStardewDataAPI.getHealth(serverPlayer);
				int maxSDHealth = com.stardew.craft.player.PlayerStardewDataAPI.getMaxHealth(serverPlayer);
				int newHealth = Math.max(0, Math.min(maxSDHealth, currentSDHealth + health));
				com.stardew.craft.player.PlayerStardewDataAPI.setHealth(serverPlayer, newHealth);
			}

			if (energy != 0) {
				if (energy > 0) {
					com.stardew.craft.player.PlayerStardewDataAPI.restoreEnergy(serverPlayer, energy);
				} else {
					com.stardew.craft.player.PlayerStardewDataAPI.consumeEnergy(serverPlayer, -energy);
				}
			}
		}

		return Objects.requireNonNull(result, "result");
	}

	@Override
	public int getSellPrice(ItemStack stack) {
		if (basePrice <= 0) {
			return -1;
		}
		if (!supportsQuality) {
			return basePrice;
		}
		return priceByQuality[getQualityIndex(stack)];
	}

	@Override
	public int getBaseSellPrice(ItemStack stack) {
		return basePrice <= 0 ? -1 : basePrice;
	}

	@Override
	public int getEdibility(ItemStack stack) {
		return edibility;
	}

	@Override
	public boolean isFood() {
		return edible;
	}

	@SuppressWarnings("null")
	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return drinkAnimation ? UseAnim.DRINK : super.getUseAnimation(stack);
	}

	@Override
	public int getHealth(ItemStack stack) {
		if (!edible) {
			return 0;
		}
		return healthByQuality[getQualityIndex(stack)];
	}

	@Override
	public int getEnergy(ItemStack stack) {
		if (!edible) {
			return 0;
		}
		return energyByQuality[getQualityIndex(stack)];
	}

	private int getQualityIndex(ItemStack stack) {
		if (!supportsQuality) {
			return 0;
		}
		int quality = QualityHelper.getQuality(stack);
		return Math.max(0, Math.min(3, quality));
	}
}