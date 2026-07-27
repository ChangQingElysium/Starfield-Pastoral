package com.stardew.craft.api.v1.farm;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.world.StardewMapSlotRoles;
import net.minecraft.resources.ResourceLocation;

/** Stable IDs projected from the legacy built-in farm layout fields. */
public final class StardewFarmLayoutAttachmentKeys {
    public static final ResourceLocation SPAWN = core("spawn");
    public static final ResourceLocation GREENHOUSE = core("greenhouse");
    public static final ResourceLocation FARM_TOTEM = core("farm_totem");
    public static final ResourceLocation ENTRY_SOUTH = core("entry_south");
    public static final ResourceLocation ENTRY_EAST = core("entry_east");
    public static final ResourceLocation ENTRY_WEST = core("entry_west");
    public static final ResourceLocation CAVE_EXIT = core("cave_exit");

    public static final ResourceLocation NPC = StardewMapSlotRoles.NPC;
    public static final ResourceLocation FESTIVAL =
            StardewMapSlotRoles.FESTIVAL;
    public static final ResourceLocation BUILDING =
            StardewMapSlotRoles.BUILDING;
    public static final ResourceLocation PORTAL =
            StardewMapSlotRoles.PORTAL;

    private StardewFarmLayoutAttachmentKeys() {
    }

    private static ResourceLocation core(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                StardewCraft.MODID, path);
    }
}
