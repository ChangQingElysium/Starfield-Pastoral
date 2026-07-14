package com.stardew.craft.api.v1.quest;

import net.minecraft.resources.ResourceLocation;

/** Stable event IDs emitted by the built-in quest facade. */
public final class QuestProgressEvents {
    public static final ResourceLocation MONSTER_SLAIN = id("monster_slain");
    public static final ResourceLocation FISH_CAUGHT = id("fish_caught");
    public static final ResourceLocation ITEM_RECEIVED = id("item_received");
    public static final ResourceLocation ITEM_OFFERED_TO_NPC = id("item_offered_to_npc");
    public static final ResourceLocation RECIPE_CRAFTED = id("recipe_crafted");
    public static final ResourceLocation NPC_SOCIALIZED = id("npc_socialized");
    public static final ResourceLocation WARPED = id("warped");
    public static final ResourceLocation BUILDING_EXISTS = id("building_exists");
    public static final ResourceLocation MINE_FLOOR_REACHED = id("mine_floor_reached");

    private QuestProgressEvents() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("stardewcraft", path);
    }
}
