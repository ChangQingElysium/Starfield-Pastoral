package com.stardew.craft.deco;

import com.stardew.craft.forge.ForgeBootstrap;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DecorationStyleRegistry {
    private static final ResourceLocation WALLS_AND_FLOORS = new ResourceLocation(ForgeBootstrap.MOD_ID, "textures/deco/walls_and_floors.png");
    private static final ResourceLocation WALLPAPERS_2 = new ResourceLocation(ForgeBootstrap.MOD_ID, "textures/deco/wallpapers_2.png");
    private static final ResourceLocation FLOORS_2 = new ResourceLocation(ForgeBootstrap.MOD_ID, "textures/deco/floors_2.png");

    private static final List<DecorationStyle> WALLPAPERS;
    private static final List<DecorationStyle> FLOORINGS;
    private static final Map<String, DecorationStyle> WALLPAPER_BY_ID;
    private static final Map<String, DecorationStyle> FLOORING_BY_ID;

    static {
        List<DecorationStyle> wallpapers = new ArrayList<>();
        List<DecorationStyle> floorings = new ArrayList<>();

        int order = 0;
        for (int i = 0; i < 112; i++) {
            String styleId = Integer.toString(i);
            wallpapers.add(new DecorationStyle(
                DecorationType.WALLPAPER,
                styleId,
                WALLS_AND_FLOORS,
                256,
                688,
                (i % 16) * 16,
                (i / 16) * 48 + 8,
                16,
                28,
                resolveUnlockHintKey(DecorationType.WALLPAPER, styleId),
                order++
            ));
        }

        for (int i = 0; i < 56; i++) {
            String styleId = Integer.toString(i);
            floorings.add(new DecorationStyle(
                DecorationType.FLOORING,
                styleId,
                WALLS_AND_FLOORS,
                256,
                688,
                (i % 8) * 32,
                336 + (i / 8) * 32,
                28,
                26,
                resolveUnlockHintKey(DecorationType.FLOORING, styleId),
                order++
            ));
        }

        for (int i = 0; i < 26; i++) {
            String styleId = "MoreWalls:" + i;
            wallpapers.add(new DecorationStyle(
                DecorationType.WALLPAPER,
                styleId,
                WALLPAPERS_2,
                256,
                96,
                (i % 16) * 16,
                (i / 16) * 48 + 8,
                16,
                28,
                resolveUnlockHintKey(DecorationType.WALLPAPER, styleId),
                order++
            ));
        }

        for (int i = 0; i < 9; i++) {
            String styleId = "MoreFloors:" + i;
            floorings.add(new DecorationStyle(
                DecorationType.FLOORING,
                styleId,
                FLOORS_2,
                256,
                64,
                (i % 8) * 32,
                (i / 8) * 32,
                28,
                26,
                resolveUnlockHintKey(DecorationType.FLOORING, styleId),
                order++
            ));
        }

        WALLPAPERS = Collections.unmodifiableList(wallpapers);
        FLOORINGS = Collections.unmodifiableList(floorings);
        WALLPAPER_BY_ID = byIdMap(wallpapers);
        FLOORING_BY_ID = byIdMap(floorings);
    }

    private DecorationStyleRegistry() {
    }

    private static Map<String, DecorationStyle> byIdMap(List<DecorationStyle> styles) {
        Map<String, DecorationStyle> out = new LinkedHashMap<>();
        for (DecorationStyle style : styles) {
            out.put(style.id(), style);
        }
        return Collections.unmodifiableMap(out);
    }

    public static List<DecorationStyle> getStyles(DecorationType type) {
        return type == DecorationType.WALLPAPER ? WALLPAPERS : FLOORINGS;
    }

    public static DecorationStyle getStyle(DecorationType type, String id) {
        if (id == null) {
            return null;
        }
        return type == DecorationType.WALLPAPER ? WALLPAPER_BY_ID.get(id) : FLOORING_BY_ID.get(id);
    }

    public static int getVisualIndex(DecorationType type, String id) {
        if (id == null) {
            return 0;
        }
        List<DecorationStyle> styles = getStyles(type);
        for (int i = 0; i < styles.size(); i++) {
            if (styles.get(i).id().equals(id)) {
                return i;
            }
        }
        return 0;
    }

    public static String getDefaultStyleId(DecorationType type) {
        return "0";
    }

    private static String resolveUnlockHintKey(DecorationType type, String styleId) {
        if (type == DecorationType.WALLPAPER) {
            return resolveWallpaperHintKey(styleId);
        }
        return resolveFlooringHintKey(styleId);
    }

    private static String resolveWallpaperHintKey(String styleId) {
        return switch (styleId) {
            case "12", "MoreWalls:9", "MoreWalls:10" -> "stardewcraft.deco.unlock_hint.wallpaper_wizard_catalogue";
            case "21" -> "stardewcraft.deco.unlock_hint.wallpaper_joja_shop";
            case "MoreWalls:12", "MoreWalls:13", "MoreWalls:14" -> "stardewcraft.deco.unlock_hint.wallpaper_junimo_catalogue";
            case "MoreWalls:16", "MoreWalls:17", "MoreWalls:18" -> "stardewcraft.deco.unlock_hint.wallpaper_retro_catalogue";
            case "MoreWalls:22", "MoreWalls:23" -> "stardewcraft.deco.unlock_hint.wallpaper_trash_catalogue";
            case "MoreWalls:24", "MoreWalls:25" -> "stardewcraft.deco.unlock_hint.wallpaper_casino";
            case "MoreWalls:20" -> "stardewcraft.deco.unlock_hint.wallpaper_catalogue_only";
            case "MoreWalls:19" -> "stardewcraft.deco.unlock_hint.wallpaper_winter_festival";
            case "MoreWalls:0", "MoreWalls:1", "MoreWalls:2", "MoreWalls:3", "MoreWalls:4", "MoreWalls:5", "MoreWalls:6" -> "stardewcraft.deco.unlock_hint.wallpaper_desert_festival";
            default -> "stardewcraft.deco.unlock_hint.wallpaper_default";
        };
    }

    private static String resolveFlooringHintKey(String styleId) {
        return switch (styleId) {
            case "8" -> "stardewcraft.deco.unlock_hint.flooring_joja_catalogue";
            case "49", "MoreFloors:5" -> "stardewcraft.deco.unlock_hint.flooring_junimo_catalogue";
            case "51" -> "stardewcraft.deco.unlock_hint.flooring_wizard_catalogue";
            case "1" -> "stardewcraft.deco.unlock_hint.flooring_junimo_wizard_catalogue";
            case "MoreFloors:1", "MoreFloors:2", "MoreFloors:3", "MoreFloors:4", "MoreFloors:7" -> "stardewcraft.deco.unlock_hint.flooring_retro_catalogue";
            case "MoreFloors:8" -> "stardewcraft.deco.unlock_hint.flooring_casino";
            default -> "stardewcraft.deco.unlock_hint.flooring_default";
        };
    }
}
