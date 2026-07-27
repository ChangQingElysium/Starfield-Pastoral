package com.stardew.craft.npc.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.api.v1.content.AtomicDefinitionStore;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import com.stardew.craft.api.v1.content.DefinitionSnapshot;
import com.stardew.craft.api.v1.npc.StardewNpcGiftTastePatchDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Atomic snapshot of effective NPC gift-taste patch definitions. */
public final class NpcGiftTastePatchData {
    private static final AtomicDefinitionStore<
            StardewNpcGiftTastePatchDefinition> STORE =
            new AtomicDefinitionStore<>();
    private static volatile Catalog catalog = Catalog.empty();

    private NpcGiftTastePatchData() {
    }

    public static DefinitionSnapshot<StardewNpcGiftTastePatchDefinition>
    snapshot() {
        return catalog.definitions();
    }

    static void decode(
            ResourceLocation id,
            JsonElement json,
            Gson gson,
            Map<ResourceLocation, StardewNpcGiftTastePatchDefinition>
                    definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics
    ) {
        StardewNpcGiftTastePatchDefinition.CODEC
                .parse(JsonOps.INSTANCE, json)
                .resultOrPartial(message -> diagnostics.add(
                        DefinitionDiagnostic.error(id, id, message)))
                .ifPresent(definition -> {
                    definitions.put(id, definition);
                    StardewNpcGiftTastePatchDefinition.CODEC
                            .encodeStart(JsonOps.INSTANCE, definition)
                            .resultOrPartial(message -> diagnostics.add(
                                    DefinitionDiagnostic.error(
                                            id, id, message)))
                            .ifPresent(encoded ->
                                    sources.put(id, gson.toJson(encoded)));
                });
    }

    static synchronized AtomicDefinitionStore.ApplyResult<
            StardewNpcGiftTastePatchDefinition> apply(
            Map<ResourceLocation, StardewNpcGiftTastePatchDefinition>
                    definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics
    ) {
        var result =
                STORE.applyLocal(definitions, sources, diagnostics);
        if (result.accepted()) {
            List<Map.Entry<ResourceLocation,
                    StardewNpcGiftTastePatchDefinition>> prepared =
                    result.snapshot().definitions().entrySet().stream()
                    .sorted(Comparator
                            .<Map.Entry<ResourceLocation,
                                    StardewNpcGiftTastePatchDefinition>>
                                    comparingInt(entry ->
                                            entry.getValue().priority())
                            .thenComparing(entry ->
                                    entry.getKey().toString()))
                    .toList();
            catalog = new Catalog(result.snapshot(), prepared);
        }
        return result;
    }

    static List<Map.Entry<ResourceLocation,
            StardewNpcGiftTastePatchDefinition>> ordered() {
        return catalog.ordered();
    }

    static Catalog catalog() {
        return catalog;
    }

    record Catalog(
            DefinitionSnapshot<StardewNpcGiftTastePatchDefinition>
                    definitions,
            List<Map.Entry<ResourceLocation,
                    StardewNpcGiftTastePatchDefinition>> ordered
    ) {
        Catalog {
            definitions = java.util.Objects.requireNonNull(
                    definitions, "definitions");
            ordered = List.copyOf(ordered);
        }

        private static Catalog empty() {
            return new Catalog(
                    DefinitionSnapshot.empty(), List.of());
        }
    }
}
