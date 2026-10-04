package com.stardew.craft.client.gui.common;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.hud.StardewHotbarHud;
import com.stardew.craft.client.model.terrain.TerrainSeasonTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;

import java.util.HashMap;
import java.util.Map;

/** Seasonal E-screen artwork at vanilla coordinates, sharing the hotbar's calendar and dimension scope. */
public final class StardewInventoryGui {
    private static final String[] SEASONS = {"spring", "summer", "fall", "winter"};
    private static final Map<ResourceLocation, ResourceLocation[]> RESOURCES = new HashMap<>();
    private static final ResourceLocation[] HOVER = new ResourceLocation[4];

    static {
        register("textures/gui/container/inventory.png", "inventory", true);
        // Only the completed E-screen art is enabled; archived pages keep their native resources.
        for (String equipment : new String[]{"helmet", "chestplate", "leggings", "boots", "shield"}) {
            register("item/empty_armor_slot_" + equipment, "empty_" + equipment, false);
        }
        for (String name : new String[]{"button", "button_highlighted"}) {
            register("recipe_book/" + name, "recipe_" + name, false);
        }
        for (int season = 0; season < SEASONS.length; season++) {
            HOVER[season] = sprite(season, "hover");
        }
    }

    private StardewInventoryGui() {}

    private static ResourceLocation sprite(int season, String name) {
        return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID,
                "inventory/seasonal/" + SEASONS[season] + "/" + name);
    }

    private static void register(String original, String name, boolean texture) {
        ResourceLocation[] variants = new ResourceLocation[4];
        for (int season = 0; season < variants.length; season++) {
            variants[season] = texture ? ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID,
                    "textures/gui/inventory/" + SEASONS[season] + "/" + name + ".png") : sprite(season, name);
        }
        RESOURCES.put(ResourceLocation.withDefaultNamespace(original), variants);
    }

    static ResourceLocation resolve(ResourceLocation original, int season, boolean active) {
        ResourceLocation[] variants = active ? RESOURCES.get(original) : null;
        return variants == null ? original : variants[TerrainSeasonTextures.textureSet(season)];
    }

    public static boolean isActive(Screen screen) {
        return screen instanceof InventoryScreen
                && StardewHotbarHud.isSeasonalThemeEnabled();
    }

    public static ResourceLocation replaceResource(ResourceLocation original) {
        return resolve(original, TerrainSeasonTextures.currentTextureSet(), isActive(Minecraft.getInstance().screen));
    }

    public static boolean renderEmptySlot(Screen screen, GuiGraphics graphics, TextureAtlasSprite original,
                                          int x, int y, int z, int width, int height) {
        if (!isActive(screen)) return false;
        ResourceLocation name = original.contents().name();
        ResourceLocation replacement = resolve(name, TerrainSeasonTextures.currentTextureSet(), true);
        if (replacement == name) return false;
        graphics.blitSprite(replacement, x, y, z, width, height);
        return true;
    }

    /** The 18px outline surrounds the native 16px hit area and leaves the ItemStack completely clear. */
    public static boolean renderSlotHighlight(Screen screen, GuiGraphics graphics, Slot slot) {
        if (!isActive(screen)) return false;
        if (slot.isHighlightable()) {
            graphics.blitSprite(HOVER[TerrainSeasonTextures.currentTextureSet()], slot.x - 1, slot.y - 1, 18, 18);
        }
        return true;
    }
}
