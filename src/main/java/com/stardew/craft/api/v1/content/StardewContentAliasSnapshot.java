package com.stardew.craft.api.v1.content;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable diagnostic view of one content alias resolution. */
public record StardewContentAliasSnapshot(
        StardewContentKey alias,
        StardewContentKey target,
        @Nullable StardewContentKey canonicalTarget,
        ResourceLocation source,
        List<String> issues
) {
    public StardewContentAliasSnapshot {
        alias = Objects.requireNonNull(alias, "alias");
        target = Objects.requireNonNull(target, "target");
        source = Objects.requireNonNull(source, "source");
        issues = List.copyOf(issues);
    }

    public boolean resolved() {
        return canonicalTarget != null && issues.isEmpty();
    }

    public Optional<StardewContentKey> resolvedTarget() {
        return Optional.ofNullable(canonicalTarget);
    }
}
