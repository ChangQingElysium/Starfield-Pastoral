package com.stardew.craft.api.v1.content;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable, versioned view of one reloadable definition registry. */
public record DefinitionSnapshot<T>(
        long version,
        String contentHash,
        Map<ResourceLocation, T> definitions,
        List<DefinitionDiagnostic> diagnostics
) {
    public DefinitionSnapshot {
        definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
        diagnostics = List.copyOf(diagnostics);
    }

    public static <T> DefinitionSnapshot<T> empty() {
        return new DefinitionSnapshot<>(0L, AtomicDefinitionStore.EMPTY_HASH, Map.of(), List.of());
    }
}
