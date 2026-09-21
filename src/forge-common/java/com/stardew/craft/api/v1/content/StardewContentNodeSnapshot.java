package com.stardew.craft.api.v1.content;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/** Immutable resolved view of one cross-system content node. */
public record StardewContentNodeSnapshot(
        StardewContentKey key,
        ResourceLocation source,
        List<StardewContentReferenceSnapshot> references,
        List<String> issues
) {
    public StardewContentNodeSnapshot {
        key = Objects.requireNonNull(key, "key");
        source = Objects.requireNonNull(source, "source");
        references = List.copyOf(references);
        issues = List.copyOf(issues);
    }

    public boolean healthy() {
        return issues.isEmpty() && references.stream()
                .noneMatch(reference ->
                        reference.required() && !reference.resolved());
    }
}
