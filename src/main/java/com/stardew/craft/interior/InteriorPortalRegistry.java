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
    private static volatile Map<ResourceLocation, PortalTarget> dataTargets = Map.of();

    private InteriorPortalRegistry() {
    }

    public static DefinitionSnapshot<StardewPortalDefinition> snapshot() {
        return STORE.snapshot();
    }

    public static Optional<PortalTarget> resolve(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        ResourceLocation dataId = normalized.indexOf(':') >= 0
                ? ResourceLocation.tryParse(normalized)
                : ResourceLocation.tryBuild(StardewCraft.MODID, normalized);
        PortalTarget dataTarget = dataId == null ? null : dataTargets.get(dataId);
        return Optional.ofNullable(dataTarget != null ? dataTarget : RUNTIME_TARGETS.get(normalized));
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
            var result = STORE.applyLocal(definitions, sources, diagnostics);
            for (DefinitionDiagnostic diagnostic : result.diagnostics()) {
                if (diagnostic.severity() == DefinitionDiagnostic.Severity.ERROR) {
                    StardewCraft.LOGGER.error("[Interior portals] {}", diagnostic.message());
                } else {
                    StardewCraft.LOGGER.warn("[Interior portals] {}", diagnostic.message());
                }
            }
            if (!result.accepted()) {
                StardewCraft.LOGGER.error("[Interior portals] Rejected reload; keeping {} targets", dataTargets.size());
                return;
            }
            Map<ResourceLocation, PortalTarget> prepared = new LinkedHashMap<>();
            result.snapshot().definitions().forEach((id, definition) -> prepared.put(id, new PortalTarget(
                    definition.x(), definition.y(), definition.z(), definition.yaw(), definition.pitch(),
                    PortalMode.valueOf(definition.mode().name()))));
            dataTargets = Map.copyOf(prepared);
            StardewCraft.LOGGER.info("[Interior portals] Applied {} data targets", dataTargets.size());
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
