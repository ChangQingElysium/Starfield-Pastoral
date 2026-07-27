package com.stardew.craft.api.v1.internal.extension;

import com.stardew.craft.api.v1.extension.StardewExtensionPointSnapshot;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Global read-only catalog of registries created through the shared extension kernel. */
public final class ExtensionPointCatalog {
    private static final Map<
            ResourceLocation,
            CatalogEntry> POINTS =
            new LinkedHashMap<>();
    private static boolean frozen;

    private ExtensionPointCatalog() {
    }

    static synchronized boolean register(
            ResourceLocation id,
            Supplier<StardewExtensionPointSnapshot> snapshot,
            Runnable freeze
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(freeze, "freeze");
        if (POINTS.putIfAbsent(
                id, new CatalogEntry(snapshot, freeze)) != null) {
            throw new IllegalStateException(
                    "Duplicate extension point: " + id);
        }
        return frozen;
    }

    public static synchronized void freezeAll() {
        if (frozen) {
            return;
        }
        frozen = true;
        POINTS.values().forEach(entry -> entry.freeze().run());
    }

    public static synchronized Optional<StardewExtensionPointSnapshot> snapshot(
            ResourceLocation id
    ) {
        CatalogEntry entry = POINTS.get(id);
        return entry == null
                ? Optional.empty()
                : Optional.of(entry.snapshot().get());
    }

    public static synchronized List<StardewExtensionPointSnapshot> snapshots() {
        ArrayList<StardewExtensionPointSnapshot> snapshots =
                new ArrayList<>(POINTS.size());
        POINTS.values().forEach(
                entry -> snapshots.add(entry.snapshot().get()));
        snapshots.sort(Comparator.comparing(
                snapshot -> snapshot.id().toString()));
        return List.copyOf(snapshots);
    }

    private record CatalogEntry(
            Supplier<StardewExtensionPointSnapshot> snapshot,
            Runnable freeze
    ) {
    }
}
