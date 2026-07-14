package com.stardew.craft.core;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * TagKey helpers for this mod.
 */
public final class ModTags {
	private ModTags() {
	}

	public static final class Blocks {
		public static final TagKey<Block> STARDEW_STONES = tag("stardew_stones");
		public static final TagKey<Block> STARDEW_ORES = tag("stardew_ores");
		public static final TagKey<Block> STARDEW_MINERALS = tag("stardew_minerals");
		public static final TagKey<Block> IRIDIUM_ORES = tag("iridium_ores");
		public static final TagKey<Block> QUARRY_RESOURCES = tag("quarry_resources");

		public static final TagKey<Block> REQUIRES_STARDEW_PICKAXE_TIER1 = tag("requires_stardew_pickaxe_tier1");
		public static final TagKey<Block> REQUIRES_STARDEW_PICKAXE_TIER2 = tag("requires_stardew_pickaxe_tier2");
		public static final TagKey<Block> REQUIRES_STARDEW_PICKAXE_TIER3 = tag("requires_stardew_pickaxe_tier3");

		@SuppressWarnings("null")
		private static TagKey<Block> tag(String name) {
			return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, name));
		}
	}

	public static final class Items {
		public static final TagKey<Item> PICKAXES = tag("pickaxes");
		public static final TagKey<Item> CROPS = tag("crops");
		public static final TagKey<Item> SEEDMAKER_BANNED = tag("seedmaker_banned");
		public static final TagKey<Item> CRYSTALARIUM_BANNED = tag("crystalarium_banned");
		public static final TagKey<Item> BONE_ITEMS = tag("bone_items");
		public static final TagKey<Item> ALL_FISHING_CATCHES = tag("all_fishing_catches");
		/** SDV prevent_loss_on_death 等价：标记这些物品在死亡时不可丢失 */
		public static final TagKey<Item> PREVENT_LOSS_ON_DEATH = tag("prevent_loss_on_death");

		@SuppressWarnings("null")
		private static TagKey<Item> tag(String name) {
			return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, name));
		}
	}
}
