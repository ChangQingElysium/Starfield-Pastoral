package com.stardew.craft.client.animal;

import com.stardew.craft.api.v1.agriculture.StardewAnimalPurchaseDisplays;
import com.stardew.craft.client.gui.FarmFolioScreen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class LivestockPortrait {
    private LivestockPortrait() {}

    public static Component name(CompoundTag row) {
        if (!row.getString("DisplayName").isBlank())
            return Component.literal(row.getString("DisplayName"));
        return row.getString("NameKey").isBlank()
                ? Component.literal(row.getString("Species"))
                : Component.translatable(row.getString("NameKey"));
    }

    public static void draw(
            GuiGraphics g, CompoundTag row, int x, int y, int w, int h, boolean black) {
        var display = StardewAnimalPurchaseDisplays.display(row.getString("Species"));
        ResourceLocation texture =
                display == null
                        ? ResourceLocation.tryParse(row.getString("Texture"))
                        : display.texture();
        int tw = display == null ? row.getInt("TextureWidth") : display.textureWidth(),
                th = display == null ? row.getInt("TextureHeight") : display.textureHeight();
        if (texture == null
                || tw <= 0
                || th <= 0
                || net.minecraft.client.Minecraft.getInstance()
                        .getResourceManager()
                        .getResource(texture)
                        .isEmpty()) {
            FarmFolioScreen.image(
                    g,
                    new ResourceLocation("stardewcraft:textures/gui/farm_buildings/pet.png"),
                    16,
                    16,
                    x,
                    y,
                    w,
                    h,
                    black);
            return;
        }
        FarmFolioScreen.image(g, texture, tw, th, x, y, w, h, black);
    }
}
