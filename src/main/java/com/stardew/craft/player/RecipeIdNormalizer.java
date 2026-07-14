package com.stardew.craft.player;

import com.stardew.craft.StardewCraft;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Locale;

/** Shared conversion between recipe definition IDs, shop pseudo IDs and player storage IDs. */
public final class RecipeIdNormalizer {
    private static final String SHOP_PREFIX = "recipe:";

    private RecipeIdNormalizer() {
    }

    /**
     * Normalizes a definition ID or a shop pseudo ID to the ID stored in player data.
     * StardewCraft IDs retain the legacy path-only representation; addon namespaces are preserved.
     */
    public static String storageId(@Nullable String rawId) {
        ResourceLocation definitionId = definitionId(rawId);
        return definitionId == null ? "" : storageId(definitionId);
    }

    public static String storageId(ResourceLocation definitionId) {
        return StardewCraft.MODID.equals(definitionId.getNamespace())
                ? definitionId.getPath()
                : definitionId.toString();
    }

    @Nullable
    public static ResourceLocation definitionId(@Nullable String rawId) {
        if (rawId == null) {
            return null;
        }
        String normalized = rawId.trim();
        if (normalized.startsWith(SHOP_PREFIX)) {
            normalized = normalized.substring(SHOP_PREFIX.length());
            if (normalized.startsWith(SHOP_PREFIX)) {
                return null;
            }
        }
        if (normalized.isBlank()) {
            return null;
        }
        return normalized.indexOf(':') >= 0
                ? ResourceLocation.tryParse(normalized)
                : ResourceLocation.tryBuild(StardewCraft.MODID, normalized.toLowerCase(Locale.ROOT));
    }
}
