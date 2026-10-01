package com.stardew.craft.integration.jei;

import com.stardew.craft.StardewCraft;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Pre-loads and caches NPC portrait drawables for JEI display.
 * Each portrait is cropped from the top-left 64×64 region of the portrait sprite sheet.
 */
@SuppressWarnings("null")
public final class JeiPortraitCache {

    private static final Map<String, IDrawable> PORTRAITS = new HashMap<>();

    /** Default rendered size for portraits in JEI (28×28 pixels). */
    public static final int DEFAULT_SIZE = 28;

    private JeiPortraitCache() {}

    /**
     * Pre-load portrait drawables for the given NPC IDs.
     * Should be called once during {@code registerCategories}.
     *
     * @param guiHelper JEI gui helper
     * @param npcIds    lowercase NPC IDs matching portrait filenames (e.g. "pierre", "willy")
     */
    /** Actual texture pixel dimensions per NPC portrait. */
    private static final java.util.Map<String, int[]> TEX_SIZES = java.util.Map.ofEntries(
            java.util.Map.entry("pierre",  new int[]{128, 192}),
            java.util.Map.entry("willy",   new int[]{128, 128}),
            java.util.Map.entry("marnie",  new int[]{128, 192}),
            java.util.Map.entry("sandy",   new int[]{128, 128}),
            java.util.Map.entry("clint",   new int[]{128, 256}),
            java.util.Map.entry("gus",     new int[]{128, 128}),
            java.util.Map.entry("harvey",  new int[]{128, 384}),
            java.util.Map.entry("marlon",  new int[]{64, 64}),
            java.util.Map.entry("robin",   new int[]{128, 256})
    );

    public static void preload(IGuiHelper guiHelper, String... npcIds) {
        for (String npcId : npcIds) {
            if (PORTRAITS.containsKey(npcId)) continue;
            ResourceLocation tex = new ResourceLocation(
                    StardewCraft.MODID, "textures/portraits/" + npcId + ".png");
            int[] size = TEX_SIZES.getOrDefault(npcId, new int[]{128, 128});
            int faceW = Math.min(64, size[0]);
            int faceH = Math.min(64, size[1]);
            IDrawable drawable = guiHelper.drawableBuilder(tex, 0, 0, faceW, faceH)
                    .setTextureSize(size[0], size[1])
                    .build();
            PORTRAITS.put(npcId, drawable);
        }
    }

    /**
     * Get a cached portrait drawable for the given NPC ID.
     *
     * @return the IDrawable, or null if not preloaded
     */
    public static IDrawable get(String npcId) {
        return PORTRAITS.get(npcId);
    }

    /**
     * Map a shop ID to its owner NPC portrait file name (lowercase).
     */
    public static String shopIdToPortraitKey(String shopId) {
        return switch (shopId) {
            case "SeedShop" -> "pierre";
            case "FishShop" -> "willy";
            case "AnimalShop" -> "marnie";
            case "OasisShop" -> "sandy";
            case "Blacksmith" -> "clint";
            case "Saloon" -> "gus";
            case "Hospital" -> "harvey";
            case "AdventureShop" -> "marlon";
            case "CarpenterShop" -> "robin";
            default -> null;
        };
    }

    /** All portrait NPC IDs needed for shop display. */
    public static final String[] SHOP_NPC_IDS = {
            "pierre", "willy", "marnie", "sandy", "clint",
            "gus", "harvey", "marlon", "robin"
    };
}
