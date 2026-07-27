package com.stardew.craft.api.v1.progress;

import com.stardew.craft.StardewCraft;
import net.minecraft.resources.ResourceLocation;

/** Canonical progress domains shared by built-in systems and add-on integrations. */
public final class StardewProgressDomains {
    public static final ResourceLocation QUEST = id("quest");
    public static final ResourceLocation SPECIAL_ORDER = id("special_order");
    public static final ResourceLocation MAIL = id("mail");
    public static final ResourceLocation COMMUNITY_CENTER = id("community_center");
    public static final ResourceLocation MUSEUM = id("museum");
    public static final ResourceLocation FESTIVAL = id("festival");

    private StardewProgressDomains() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, path);
    }
}
