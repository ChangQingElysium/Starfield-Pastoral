package com.stardew.craft.block.utility;

import com.stardew.craft.StardewCraft;
import net.minecraft.resources.ResourceLocation;

/** The placeable magic buildings currently backed by supplied GeckoLib models. */
public enum WizardBuildingKind {
    JUNIMO_HUT("junimo_hut"),
    EARTH_OBELISK("earth_obelisk"),
    WATER_OBELISK("water_obelisk"),
    DESERT_OBELISK("desert_obelisk"),
    ISLAND_OBELISK("island_obelisk"),
    GOLD_CLOCK("gold_clock");

    private final String id;
    private final ResourceLocation model;
    private final ResourceLocation texture;

    WizardBuildingKind(String id) {
        this.id = id;
        this.model = resource("geo/block/utility/" + id + ".geo.json");
        this.texture = resource("textures/block/utility/" + id + ".png");
    }

    public String id() {
        return id;
    }

    public ResourceLocation model() {
        return model;
    }

    public ResourceLocation texture() {
        return texture;
    }

    public boolean isJunimoHut() {
        return this == JUNIMO_HUT;
    }

    public boolean isGoldClock() {
        return this == GOLD_CLOCK;
    }

    public String shapeModelId() {
        return model + "#aabb";
    }

    public ResourceLocation goldClockElementModel(boolean enabled) {
        return resource("blockbench/block/utility/gold_clock_" + (enabled ? "on" : "off") + ".json");
    }

    private static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, path);
    }
}
