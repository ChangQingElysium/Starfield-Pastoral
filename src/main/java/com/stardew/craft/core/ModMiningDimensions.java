package com.stardew.craft.core;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * 矿井维度 Keys。
 *
 * 说明：维度本体与维度类型使用 datapack JSON（见 data/stardewcraft/dimension/*）。
 */
public final class ModMiningDimensions {
	private ModMiningDimensions() {}

	@SuppressWarnings("null")
	public static final ResourceKey<Level> STARDEW_MINING = ResourceKey.create(
		Registries.DIMENSION,
		new ResourceLocation(StardewCraft.MODID, "stardew_mining")
	);

	@SuppressWarnings("null")
	public static final ResourceKey<DimensionType> STARDEW_MINING_TYPE = ResourceKey.create(
		Registries.DIMENSION_TYPE,
		new ResourceLocation(StardewCraft.MODID, "stardew_mining")
	);
}
