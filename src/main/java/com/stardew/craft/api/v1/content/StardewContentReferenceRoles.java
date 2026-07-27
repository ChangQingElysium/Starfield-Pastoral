package com.stardew.craft.api.v1.content;

import com.stardew.craft.StardewCraft;
import net.minecraft.resources.ResourceLocation;

/** Common semantic roles for content references. */
public final class StardewContentReferenceRoles {
    public static final ResourceLocation PARENT_LOCATION =
            id("parent_location");
    public static final ResourceLocation LOCATION = id("location");
    public static final ResourceLocation REGION = id("region");
    public static final ResourceLocation ORIGIN_ANCHOR =
            id("origin_anchor");
    public static final ResourceLocation MAP_OVERLAY = id("map_overlay");
    public static final ResourceLocation SHOP = id("shop");
    public static final ResourceLocation OWNER_NPC = id("owner_npc");
    public static final ResourceLocation INVENTORY_PROVIDER =
            id("inventory_provider");
    public static final ResourceLocation PRODUCT_ITEM =
            id("product_item");
    public static final ResourceLocation TRADE_ITEM = id("trade_item");
    public static final ResourceLocation NEXT_QUEST = id("next_quest");
    public static final ResourceLocation QUEST = id("quest");
    public static final ResourceLocation MAIL = id("mail");
    public static final ResourceLocation SECRET_NOTE = id("secret_note");
    public static final ResourceLocation EVENT_HISTORY =
            id("event_history");
    public static final ResourceLocation SKILL = id("skill");
    public static final ResourceLocation PARENT_PROFESSION =
            id("parent_profession");
    public static final ResourceLocation EFFECT_HANDLER =
            id("effect_handler");
    public static final ResourceLocation SPECIAL_ORDER =
            id("special_order");
    public static final ResourceLocation REQUESTER_NPC =
            id("requester_npc");
    public static final ResourceLocation TARGET_NPC = id("target_npc");
    public static final ResourceLocation GIFT_ITEM = id("gift_item");
    public static final ResourceLocation ATTACHED_ITEM =
            id("attached_item");
    public static final ResourceLocation REWARD_MAIL = id("reward_mail");
    public static final ResourceLocation REWARD_ITEM = id("reward_item");
    public static final ResourceLocation CLEANUP_MAIL = id("cleanup_mail");
    public static final ResourceLocation CLEANUP_ITEM = id("cleanup_item");
    public static final ResourceLocation MATERIAL_ITEM =
            id("material_item");
    public static final ResourceLocation RESULT_ITEM = id("result_item");
    public static final ResourceLocation PARENT_LAYOUT =
            id("parent_layout");
    public static final ResourceLocation BUNDLE_ITEM = id("bundle_item");
    public static final ResourceLocation MACHINE = id("machine");
    public static final ResourceLocation INPUT_ITEM = id("input_item");
    public static final ResourceLocation OUTPUT_ITEM = id("output_item");
    public static final ResourceLocation QUERY_ITEM = id("query_item");
    public static final ResourceLocation FORAGE_BLOCK =
            id("forage_block");
    public static final ResourceLocation TERRAIN_BLOCK =
            id("terrain_block");
    public static final ResourceLocation CROP_BLOCK = id("crop_block");
    public static final ResourceLocation SEED_ITEM = id("seed_item");
    public static final ResourceLocation PRODUCE_ITEM =
            id("produce_item");
    public static final ResourceLocation HARVEST_TOOL =
            id("harvest_tool");
    public static final ResourceLocation ANIMAL_ENTITY =
            id("animal_entity");
    public static final ResourceLocation ALTERNATE_ANIMAL =
            id("alternate_animal");
    public static final ResourceLocation INGREDIENT_ITEM =
            id("ingredient_item");
    public static final ResourceLocation LEARNED_RECIPE =
            id("learned_recipe");
    public static final ResourceLocation CONDITION_ITEM =
            id("condition_item");
    public static final ResourceLocation ACTION_ITEM = id("action_item");
    public static final ResourceLocation DISPLAY_ITEM = id("display_item");
    public static final ResourceLocation DISPLAY_BLOCK =
            id("display_block");
    public static final ResourceLocation SPAWN_ENTITY =
            id("spawn_entity");
    public static final ResourceLocation UNLOCK_SOURCE =
            id("unlock_source");
    public static final ResourceLocation OBJECTIVE_ITEM =
            id("objective_item");
    public static final ResourceLocation OBJECTIVE_LOCATION =
            id("objective_location");
    public static final ResourceLocation OBJECTIVE_RECIPE =
            id("objective_recipe");
    public static final ResourceLocation CATCH_ITEM = id("catch_item");
    public static final ResourceLocation POND_FISH = id("pond_fish");
    public static final ResourceLocation POND_PRODUCT =
            id("pond_product");
    public static final ResourceLocation POPULATION_GATE_ITEM =
            id("population_gate_item");
    public static final ResourceLocation REQUIRED_ITEM =
            id("required_item");
    public static final ResourceLocation DROP_ITEM = id("drop_item");
    public static final ResourceLocation MONSTER_PROFILE =
            id("monster_profile");
    public static final ResourceLocation MINE_THEME = id("mine_theme");
    public static final ResourceLocation EXCLUSIVE_QUEST =
            id("exclusive_quest");
    public static final ResourceLocation CURRENCY = id("currency");

    private StardewContentReferenceRoles() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                StardewCraft.MODID, path);
    }
}
