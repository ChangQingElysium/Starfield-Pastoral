package com.stardew.craft.interior;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.content.AtomicDefinitionStore;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import com.stardew.craft.api.v1.content.DefinitionSnapshot;
import com.stardew.craft.api.v1.world.StardewPortalDefinition;
import com.stardew.craft.api.v1.world.StardewMapSlots;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Namespaced portal targets with a reloadable data layer and Java compatibility registrations. */
public final class InteriorPortalRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicDefinitionStore<StardewPortalDefinition> STORE = new AtomicDefinitionStore<>();
    private static final Map<String, PortalTarget> RUNTIME_TARGETS = new ConcurrentHashMap<>();
    private static volatile Catalog catalog = Catalog.empty();

    private InteriorPortalRegistry() {
    }

    public static DefinitionSnapshot<StardewPortalDefinition> snapshot() {
        return catalog.definitions();
    }

    public static Optional<PortalTarget> resolve(String id) {
        return resolve(id, null);
    }

    public static Optional<PortalTarget> resolve(
            String id,
            ResourceLocation expectedDimension
    ) {
        if (id == null || id.isBlank()) return Optional.empty();
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        ResourceLocation dataId = normalized.indexOf(':') >= 0
                ? ResourceLocation.tryParse(normalized)
                : ResourceLocation.tryBuild(StardewCraft.MODID, normalized);
        PortalTarget dataTarget = dataId == null
                ? null
                : catalog.dataTargets().get(dataId);
        PortalTarget direct = dataTarget != null
                ? dataTarget : RUNTIME_TARGETS.get(normalized);
        if (direct != null) {
            return Optional.of(direct);
        }
        return StardewMapSlots.resolveWorldAnchor(normalized)
                .filter(slot -> expectedDimension == null
                        || expectedDimension.equals(slot.dimension()))
                .map(slot -> new PortalTarget(
                        slot.position().x,
                        slot.position().y,
                        slot.position().z,
                        slot.yaw(),
                        0.0F,
                        PortalMode.NONE));
    }

    /** Java registrations remain useful for generated/player-specific interiors. */
    public static void register(String id, PortalTarget target) {
        if (id == null || id.isBlank() || target == null) return;
        RUNTIME_TARGETS.put(id.trim().toLowerCase(Locale.ROOT), target);
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "interior_portals");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager,
                             ProfilerFiller profiler) {
            Map<ResourceLocation, StardewPortalDefinition> definitions = new LinkedHashMap<>();
            Map<ResourceLocation, String> sources = new LinkedHashMap<>();
            List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
            objects.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                    .forEach(entry -> StardewPortalDefinition.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                            .resultOrPartial(message -> diagnostics.add(
                                    DefinitionDiagnostic.error(entry.getKey(), entry.getKey(), message)))
                            .ifPresent(definition -> {
                                definitions.put(entry.getKey(), definition);
                                StardewPortalDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
                                        .resultOrPartial(message -> diagnostics.add(
                                                DefinitionDiagnostic.error(entry.getKey(), entry.getKey(), message)))
                                        .ifPresent(encoded -> sources.put(entry.getKey(), GSON.toJson(encoded)));
                            }));
            applyCandidate(definitions, sources, diagnostics);
        }
    }

    static synchronized void applyCandidate(
            Map<ResourceLocation, StardewPortalDefinition> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics
    ) {
        Map<ResourceLocation, PortalTarget> prepared =
                new LinkedHashMap<>();
        definitions.forEach((id, definition) ->
                prepared.put(id, new PortalTarget(
                        definition.x(),
                        definition.y(),
                        definition.z(),
                        definition.yaw(),
                        definition.pitch(),
                        PortalMode.valueOf(
                                definition.mode().name()))));
        var result = STORE.applyLocal(
                definitions, sources, diagnostics);
        for (DefinitionDiagnostic diagnostic : result.diagnostics()) {
            if (diagnostic.severity() == DefinitionDiagnostic.Severity.ERROR) {
                StardewCraft.LOGGER.error(
                        "[Interior portals] {}", diagnostic.message());
            } else {
                StardewCraft.LOGGER.warn(
                        "[Interior portals] {}", diagnostic.message());
            }
        }
        if (!result.accepted()) {
            StardewCraft.LOGGER.error(
                    "[Interior portals] Rejected reload; keeping {} targets",
                    catalog.dataTargets().size());
            return;
        }
        catalog = new Catalog(result.snapshot(), prepared);
        StardewCraft.LOGGER.info(
                "[Interior portals] Applied {} data targets",
                catalog.dataTargets().size());
    }

    static Catalog catalog() {
        return catalog;
    }

    record Catalog(
            DefinitionSnapshot<StardewPortalDefinition> definitions,
            Map<ResourceLocation, PortalTarget> dataTargets
    ) {
        Catalog {
            definitions = java.util.Objects.requireNonNull(
                    definitions, "definitions");
            dataTargets = Map.copyOf(dataTargets);
        }

        private static Catalog empty() {
            return new Catalog(
                    DefinitionSnapshot.empty(), Map.of());
        }
    }

    public record PortalTarget(double x, double y, double z, float yaw, float pitch, PortalMode mode) {
    }

    public enum PortalMode {
        ENTRANCE,
        EXIT,
        NONE
    }
}
