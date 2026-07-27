package com.stardew.craft.api.v1.content;

import com.stardew.craft.StardewCraft;
import net.minecraft.resources.ResourceLocation;

/** Built-in content kinds used by cross-system references. */
public final class StardewContentTypes {
    public static final ResourceLocation LOCATION = id("location");
    public static final ResourceLocation REGION = id("region");
    public static final ResourceLocation WORLD_ANCHOR = id("world_anchor");
    public static final ResourceLocation PORTAL = id("portal");
    public static final ResourceLocation FORAGE_ZONE = id("forage_zone");
    public static final ResourceLocation MINE_THEME = id("mine_theme");
    public static final ResourceLocation CROP_TYPE = id("crop_type");
    public static final ResourceLocation TREE_TYPE = id("tree_type");
    public static final ResourceLocation ANIMAL_TYPE = id("animal_type");
    public static final ResourceLocation ENTITY_TYPE = id("entity_type");
    public static final ResourceLocation FESTIVAL = id("festival");
    public static final ResourceLocation FESTIVAL_MAP_OVERLAY =
            id("festival_map_overlay");
    public static final ResourceLocation NPC = id("npc");
    public static final ResourceLocation NPC_GIFT_TASTE_PATCH =
            id("npc_gift_taste_patch");
    public static final ResourceLocation SHOP = id("shop");
    public static final ResourceLocation SHOP_INVENTORY_PROVIDER =
            id("shop_inventory_provider");
    public static final ResourceLocation QUEST = id("quest");
    public static final ResourceLocation SKILL = id("skill");
    public static final ResourceLocation PROFESSION = id("profession");
    public static final ResourceLocation PROFESSION_EFFECT_HANDLER =
            id("profession_effect_handler");
    public static final ResourceLocation MASTERY_REWARD =
            id("mastery_reward");
    public static final ResourceLocation UNLOCK_SOURCE =
            id("unlock_source");
    public static final ResourceLocation SECRET_NOTE = id("secret_note");
    public static final ResourceLocation LOST_BOOK = id("lost_book");
    public static final ResourceLocation CUTSCENE_EVENT =
            id("cutscene_event");
    public static final ResourceLocation DAILY_QUEST_POOL =
            id("daily_quest_pool");
    public static final ResourceLocation MAIL = id("mail");
    public static final ResourceLocation SPECIAL_ORDER =
            id("special_order");
    public static final ResourceLocation BUILDING_BLUEPRINT =
            id("building_blueprint");
    public static final ResourceLocation FARM_LAYOUT = id("farm_layout");
    public static final ResourceLocation FARM_LAYOUT_ATTACHMENT =
            id("farm_layout_attachment");
    public static final ResourceLocation COMMUNITY_BUNDLE =
            id("community_bundle");
    public static final ResourceLocation MACHINE = id("machine");
    public static final ResourceLocation ARTISAN_RECIPE =
            id("artisan_recipe");
    public static final ResourceLocation CRAFTING_RECIPE =
            id("crafting_recipe");
    public static final ResourceLocation COOKING_RECIPE =
            id("cooking_recipe");
    public static final ResourceLocation ITEM = id("item");
    public static final ResourceLocation BLOCK = id("block");
    public static final ResourceLocation CURRENCY = id("currency");
    public static final ResourceLocation GEODE_DROP = id("geode_drop");
    public static final ResourceLocation PRIZE_TICKET_REWARD =
            id("prize_ticket_reward");
    public static final ResourceLocation MINE_CHEST_REWARD =
            id("mine_chest_reward");
    public static final ResourceLocation WORLD_LOOT_POOL =
            id("world_loot_pool");
    public static final ResourceLocation FISHING_TREASURE_POOL =
            id("fishing_treasure_pool");
    public static final ResourceLocation FISHING_POOL =
            id("fishing_pool");
    public static final ResourceLocation FISH_POND_RULE =
            id("fish_pond_rule");
    public static final ResourceLocation MUSEUM_REWARD =
            id("museum_reward");
    public static final ResourceLocation MONSTER_SLAYER_GOAL =
            id("monster_slayer_goal");
    public static final ResourceLocation ARTIFACT_SPOT_POOL =
            id("artifact_spot_pool");
    public static final ResourceLocation ARTIFACT_SPOT_DROP_PROVIDER =
            id("artifact_spot_drop_provider");
    public static final ResourceLocation MINE_MONSTER_PROFILE =
            id("mine_monster_profile");
    public static final ResourceLocation MINE_MONSTER_SPAWN_TABLE =
            id("mine_monster_spawn_table");

    private StardewContentTypes() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                StardewCraft.MODID, path);
    }
}
