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
		public static final TagKey<Block> MACHINES = tag("machines");
		public static final TagKey<Block> ARTISAN_MACHINES = tag("machines/artisan");
		public static final TagKey<Block> UTILITY_MACHINES = tag("machines/utility");
		public static final TagKey<Block> FURNITURE = tag("furniture");
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
		public static final TagKey<Item> TOOLS = tag("tools");
		public static final TagKey<Item> PICKAXES = tag("pickaxes");
		public static final TagKey<Item> HOES = tag("hoes");
		public static final TagKey<Item> WATERING_CANS = tag("watering_cans");
		public static final TagKey<Item> SCYTHES = tag("scythes");
		public static final TagKey<Item> CROPS = tag("crops");
		public static final TagKey<Item> SEEDMAKER_BANNED = tag("seedmaker_banned");
		public static final TagKey<Item> CRYSTALARIUM_BANNED = tag("crystalarium_banned");
		public static final TagKey<Item> BONE_ITEMS = tag("bone_items");
		public static final TagKey<Item> ALL_FISHING_CATCHES = tag("all_fishing_catches");
		public static final TagKey<Item> FISHES = tag("fishes");
		public static final TagKey<Item> CRAB_POT_ITEMS = tag("crab_pot_items");
		public static final TagKey<Item> CRAFTING_LOGS = tag("crafting_logs");
		public static final TagKey<Item> CRAFTING_PLANKS = tag("crafting_planks");
		public static final TagKey<Item> CRAFTING_HARDWOOD_LOGS = tag("crafting_hardwood_logs");
		public static final TagKey<Item> CRAFTING_HARDWOOD_PLANKS = tag("crafting_hardwood_planks");
		public static final TagKey<Item> ORES = tag("ores");
		public static final TagKey<Item> BARS = tag("bars");
		public static final TagKey<Item> BLACKSMITH_PRICE_ITEMS = tag("profession_price/blacksmith");
		public static final TagKey<Item> GEMOLOGIST_PRICE_ITEMS = tag("profession_price/gemologist");
		public static final TagKey<Item> TAPPER_PRICE_ITEMS = tag("profession_price/tapper");
		public static final TagKey<Item> HIDDEN = tag("hidden");
		public static final TagKey<Item> WARDROBE_ACCEPTED = tag("wardrobe_accepted");
		/** SDV prevent_loss_on_death 等价：标记这些物品在死亡时不可丢失 */
		public static final TagKey<Item> PREVENT_LOSS_ON_DEATH = tag("prevent_loss_on_death");

		@SuppressWarnings("null")
		private static TagKey<Item> tag(String name) {
			return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, name));
		}
	}
}
