package com.stardew.craft.client;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

/** Pure client display fallbacks; these helpers never authorize gameplay. */
public final class ClientDisplayFallbacks {
    private ClientDisplayFallbacks() {
    }

    public static ResourceLocation availableResource(
            ResourceLocation requested,
            ResourceLocation fallback,
            Predicate<ResourceLocation> available
    ) {
        Objects.requireNonNull(fallback, "fallback");
        Objects.requireNonNull(available, "available");
        if (requested == null) {
            return fallback;
        }
        try {
            return available.test(requested) ? requested : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public static String readableId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return "?";
        }
        String path = stablePath(rawId, rawId.trim());
        String[] parts = path.split("[_\\s-]+");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                result.append(part.substring(1));
            }
        }
        return result.isEmpty() ? rawId : result.toString();
    }

    public static String stablePath(String rawId, String fallback) {
        if (rawId == null || rawId.isBlank()) {
            return fallback;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(
                rawId.trim().toLowerCase(Locale.ROOT));
        return parsed == null ? fallback : parsed.getPath();
    }
}
