package com.stardew.craft.platform.fabric.client.render;

import net.minecraft.resources.ResourceLocation;

/** Named layers consumed by the current project, in their actual native rendering order. */
public final class VanillaGuiLayers {
    public static final ResourceLocation HOTBAR = ResourceLocation.withDefaultNamespace("hotbar");
    public static final ResourceLocation PLAYER_HEALTH = ResourceLocation.withDefaultNamespace("player_health");
    public static final ResourceLocation FOOD_LEVEL = ResourceLocation.withDefaultNamespace("food_level");
    private VanillaGuiLayers() {}
}
