package com.stardew.craft.api.v1.world;

import net.minecraft.resources.ResourceLocation;

/**
 * Shared role IDs for discovering compatible map slots.
 *
 * <p>Roles are capabilities, not exclusive slot types. Addons may combine
 * these with their own namespaced roles.
 */
public final class StardewMapSlotRoles {
    public static final ResourceLocation NPC = core("npc");
    public static final ResourceLocation NPC_SCHEDULE =
            core("npc_schedule");
    public static final ResourceLocation FESTIVAL =
            core("festival");
    public static final ResourceLocation FESTIVAL_STAGE =
            core("festival_stage");
    public static final ResourceLocation BUILDING =
            core("building");
    public static final ResourceLocation PORTAL =
            core("portal");

    private StardewMapSlotRoles() {
    }

    private static ResourceLocation core(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                "stardewcraft", path);
    }
}
