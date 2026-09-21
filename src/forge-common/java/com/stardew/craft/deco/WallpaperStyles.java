package com.stardew.craft.deco;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stable wallpaper IDs and their frozen legacy visual indices. */
public final class WallpaperStyles {
    private static final List<String> ALL_STYLE_IDS;
    private static final Map<String, Integer> VISUAL_INDEX_BY_ID;

    static {
        List<String> ids = new ArrayList<>(138);
        for (int i = 0; i < 112; i++) {
            ids.add(Integer.toString(i));
        }
        for (int i = 0; i < 26; i++) {
            ids.add("MoreWalls:" + i);
        }
        ALL_STYLE_IDS = Collections.unmodifiableList(ids);

        Map<String, Integer> indices = new LinkedHashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            indices.put(ids.get(i), i);
        }
        VISUAL_INDEX_BY_ID = Collections.unmodifiableMap(indices);
    }

    private WallpaperStyles() {
    }

    public static List<String> allStyleIds() {
        return ALL_STYLE_IDS;
    }

    public static boolean contains(String styleId) {
        return VISUAL_INDEX_BY_ID.containsKey(styleId);
    }

    public static String fromLegacyVisualIndex(int visualIndex) {
        if (visualIndex < 0 || visualIndex >= ALL_STYLE_IDS.size()) {
            return "0";
        }
        return ALL_STYLE_IDS.get(visualIndex);
    }

    public static int visualIndex(String styleId) {
        return VISUAL_INDEX_BY_ID.getOrDefault(styleId, 0);
    }

    public static String registryPath(String styleId) {
        if (styleId.startsWith("MoreWalls:")) {
            return "wallpaper_morewalls_" + styleId.substring("MoreWalls:".length());
        }
        return "wallpaper_" + styleId;
    }
}
