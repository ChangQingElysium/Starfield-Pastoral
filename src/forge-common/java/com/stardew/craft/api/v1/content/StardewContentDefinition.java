package com.stardew.craft.api.v1.content;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/**
 * Provider-facing projection of content already owned by a domain system.
 * Registering a projection does not register or replace the underlying
 * content.
 */
public record StardewContentDefinition(
        StardewContentKey key,
        ResourceLocation source,
        List<StardewContentReference> references,
        List<String> issues
) {
    public StardewContentDefinition {
        key = Objects.requireNonNull(key, "key");
        source = Objects.requireNonNull(source, "source");
        references = List.copyOf(
                references == null ? List.of() : references);
        issues = List.copyOf(issues == null ? List.of() : issues);
    }

    public StardewContentDefinition(
            StardewContentKey key,
            ResourceLocation source,
            List<StardewContentReference> references
    ) {
        this(key, source, references, List.of());
    }
}
