package com.stardew.craft.cutscene.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.content.AtomicDefinitionStore;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import com.stardew.craft.api.v1.content.DefinitionSnapshot;
import com.stardew.craft.cutscene.network.SyncEventRegistryPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Atomic, versioned registry for datapack cutscene definitions. */
public final class EventRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicDefinitionStore<EventData> STORE = new AtomicDefinitionStore<>();

    private static volatile Catalog catalog = Catalog.empty();

    private EventRegistry() {
    }

    public static EventData getById(String id) {
        if (id == null) {
            return null;
        }
        RegistryState state = catalog.state();
        String canonical = state.canonicalByAlias().getOrDefault(id, id);
        return state.byId().get(canonical);
    }

    /** Resolve a former numeric/legacy event ID to the current semantic ID. */
    public static String canonicalId(String id) {
        if (id == null) {
            return null;
        }
        return catalog.state().canonicalByAlias().getOrDefault(id, id);
    }

    /** All IDs which represent the same event, including legacy save keys. */
    public static Set<String> equivalentIds(String id) {
        if (id == null) {
            return Set.of();
        }
        RegistryState state = catalog.state();
        String canonical = state.canonicalByAlias().getOrDefault(id, id);
        return state.equivalentIdsByCanonical().getOrDefault(canonical, Set.of(canonical));
    }

    public static List<EventData> getByLocation(String location) {
        return catalog.state().byLocation()
                .getOrDefault(location, List.of());
    }

    public static List<EventData> getByNpc(String npcId) {
        return catalog.state().byNpc()
                .getOrDefault(npcId, List.of());
    }

    public static List<EventData> getTimeCheckEvents() {
        return catalog.state().timeCheckEvents();
    }

    public static Collection<EventData> all() {
        return catalog.state().byId().values();
    }

    public static Map<String, String> getRawJsonMap() {
        return catalog.state().rawJsonById();
    }

    public static long version() {
        return catalog.definitions().version();
    }

    public static String contentHash() {
        return catalog.definitions().contentHash();
    }

    public static DefinitionSnapshot<EventData> snapshot() {
        return catalog.definitions();
    }

    public static SyncEventRegistryPayload currentSyncPayload() {
        Catalog current = catalog;
        return new SyncEventRegistryPayload(
                current.definitions().version(),
                current.definitions().contentHash(),
                new HashMap<>(
                        current.state().rawJsonById()));
    }

    /** Compatibility entry point for callers that do not yet carry sync metadata. */
    public static void loadFromJsonStrings(Map<String, String> jsonMap) {
        Candidate candidate = parseSynced(jsonMap);
        applyLocalCandidate(candidate, "synced");
    }

    /** Client-side atomic application of one server-authoritative snapshot. */
    public static void loadFromJsonStrings(long version, String hash, Map<String, String> jsonMap) {
        Candidate candidate = parseSynced(jsonMap);
        applyRemoteCandidate(
                version, hash, candidate, "synced");
    }

    public static synchronized void reset() {
        STORE.reset();
        catalog = Catalog.empty();
    }

    private static Candidate parseSynced(Map<String, String> jsonMap) {
        Map<ResourceLocation, String> sources = new LinkedHashMap<>();
        List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
        for (Map.Entry<String, String> entry : jsonMap.entrySet()) {
            ResourceLocation source = definitionId(entry.getKey());
            if (source == null) {
                diagnostics.add(DefinitionDiagnostic.error(
                        null, null, "Invalid synced cutscene ID: " + entry.getKey()));
                continue;
            }
            sources.put(source, entry.getValue());
        }
        return parseSources(sources, diagnostics);
    }

    private static Candidate parseResources(Map<ResourceLocation, JsonElement> objects) {
        Map<ResourceLocation, String> sources = new LinkedHashMap<>();
        List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
            JsonElement element = entry.getValue();
            if (element == null || !element.isJsonObject()) {
                diagnostics.add(DefinitionDiagnostic.error(
                        entry.getKey(), null, "Cutscene definition must be a JSON object"));
                continue;
            }
            sources.put(entry.getKey(), GSON.toJson(element.getAsJsonObject()));
        }
        return parseSources(sources, diagnostics);
    }

    private static Candidate parseSources(
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics
    ) {
        Map<ResourceLocation, EventData> definitions = new LinkedHashMap<>();
        Map<ResourceLocation, String> canonicalSources = new LinkedHashMap<>();
        Map<String, String> rawJsonById = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> sourceByDefinition = new HashMap<>();

        for (Map.Entry<ResourceLocation, String> entry : sources.entrySet()) {
            ResourceLocation source = entry.getKey();
            try {
                JsonObject object = JsonParser.parseString(entry.getValue()).getAsJsonObject();
                EventData data = EventData.fromJson(object);
                ResourceLocation id = definitionId(data.id());
                if (id == null) {
                    diagnostics.add(DefinitionDiagnostic.error(
                            source, null, "Invalid cutscene definition ID: " + data.id()));
                    continue;
                }
                ResourceLocation previousSource = sourceByDefinition.putIfAbsent(id, source);
                if (previousSource != null) {
                    diagnostics.add(DefinitionDiagnostic.error(
                            source, id, "Duplicate cutscene ID; already defined by " + previousSource));
                    continue;
                }
                validateTrigger(source, id, data.trigger(), diagnostics);
                definitions.put(id, data);
                canonicalSources.put(id, entry.getValue());
                rawJsonById.put(data.id(), entry.getValue());
            } catch (Exception exception) {
                diagnostics.add(DefinitionDiagnostic.error(
                        source, null, "Failed to parse cutscene: " + exception.getMessage()));
            }
        }
        validateLegacyIds(definitions.values(), sourceByDefinition, diagnostics);
        return new Candidate(definitions, canonicalSources, rawJsonById, diagnostics);
    }

    private static void validateLegacyIds(
            Collection<EventData> definitions,
            Map<ResourceLocation, ResourceLocation> sourceByDefinition,
            List<DefinitionDiagnostic> diagnostics
    ) {
        Map<String, String> ownerById = new HashMap<>();
        for (EventData data : definitions) {
            ownerById.put(data.id(), data.id());
        }
        for (EventData data : definitions) {
            ResourceLocation definitionId = definitionId(data.id());
            ResourceLocation source = sourceByDefinition.get(definitionId);
            for (String legacyId : data.legacyIds()) {
                if (legacyId == null || legacyId.isBlank() || definitionId(legacyId) == null) {
                    diagnostics.add(DefinitionDiagnostic.error(
                            source, definitionId, "Invalid legacy cutscene ID: " + legacyId));
                    continue;
                }
                String previousOwner = ownerById.putIfAbsent(legacyId, data.id());
                if (previousOwner != null) {
                    diagnostics.add(DefinitionDiagnostic.error(
                            source, definitionId,
                            "Legacy cutscene ID '" + legacyId + "' is already owned by " + previousOwner));
                }
            }
        }
    }

    private static void validateTrigger(
            ResourceLocation source,
            ResourceLocation id,
            EventTrigger trigger,
            List<DefinitionDiagnostic> diagnostics
    ) {
        if (trigger == null) {
            diagnostics.add(DefinitionDiagnostic.error(source, id, "Cutscene is missing trigger"));
            return;
        }
        switch (trigger.type()) {
            case "enter_area" -> {
                if (trigger.location() == null || trigger.location().isBlank()) {
                    diagnostics.add(DefinitionDiagnostic.error(
                            source, id, "enter_area trigger is missing location"));
                }
                if ((trigger.areaMin() == null) != (trigger.areaMax() == null)) {
                    diagnostics.add(DefinitionDiagnostic.error(
                            source, id, "enter_area trigger must define both area_min and area_max"));
                }
            }
            case "interact_npc" -> {
                if (trigger.npc() == null || trigger.npc().isBlank()) {
                    diagnostics.add(DefinitionDiagnostic.error(
                            source, id, "interact_npc trigger is missing npc"));
                }
            }
            case "time_check", "wake_up", "manual" -> {
            }
            default -> {
                ResourceLocation customType = ResourceLocation.tryParse(trigger.type());
                if (customType == null || trigger.type().indexOf(':') < 1) {
                    diagnostics.add(DefinitionDiagnostic.error(
                            source, id, "Unknown cutscene trigger type: " + trigger.type()));
                    return;
                }
                com.stardew.craft.api.v1.cutscene.StardewCutsceneTriggers
                        .validate(customType, trigger.raw())
                        .error()
                        .ifPresent(error -> diagnostics.add(DefinitionDiagnostic.error(
                                source, id, error.message())));
            }
        }
    }

    private static ResourceLocation definitionId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        return rawId.indexOf(':') >= 0
                ? ResourceLocation.tryParse(rawId)
                : ResourceLocation.tryBuild(StardewCraft.MODID, rawId);
    }

    private static synchronized boolean applyLocalCandidate(
            Candidate candidate,
            String operation
    ) {
        var result = STORE.applyLocal(
                candidate.definitions(),
                candidate.sources(),
                candidate.diagnostics());
        return finishApply(result, candidate, operation);
    }

    private static synchronized boolean applyRemoteCandidate(
            long version,
            String hash,
            Candidate candidate,
            String operation
    ) {
        var result = STORE.replaceRemote(
                version,
                hash,
                candidate.definitions(),
                candidate.sources(),
                candidate.diagnostics());
        return finishApply(result, candidate, operation);
    }

    private static boolean finishApply(
            AtomicDefinitionStore.ApplyResult<EventData> result,
            Candidate candidate,
            String operation
    ) {
        if (!result.accepted()) {
            logDiagnostics(result.diagnostics());
            LOGGER.error("Rejected {} cutscene snapshot; keeping version {} with {} events",
                    operation,
                    catalog.definitions().version(),
                    catalog.state().byId().size());
            return false;
        }
        if (result.changed()) {
            catalog = new Catalog(
                    result.snapshot(),
                    RegistryState.build(
                            result.snapshot().definitions(),
                            candidate.rawJsonById()));
        }
        logDiagnostics(result.diagnostics());
        LOGGER.info("Applied {} cutscene snapshot v{} ({}, {} events)",
                operation,
                catalog.definitions().version(),
                shortHash(catalog.definitions().contentHash()),
                catalog.state().byId().size());
        return result.changed();
    }

    private static void logDiagnostics(List<DefinitionDiagnostic> diagnostics) {
        for (DefinitionDiagnostic diagnostic : diagnostics) {
            String location = diagnostic.source() == null ? "<sync>" : diagnostic.source().toString();
            if (diagnostic.severity() == DefinitionDiagnostic.Severity.ERROR) {
                LOGGER.error("Cutscene definition error [{}]: {}", location, diagnostic.message());
            } else {
                LOGGER.warn("Cutscene definition warning [{}]: {}", location, diagnostic.message());
            }
        }
    }

    private static String shortHash(String hash) {
        return hash.length() <= 12 ? hash : hash.substring(0, 12);
    }

    private static void syncOnlinePlayers() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getPlayerList().getPlayerCount() == 0) {
            return;
        }
        server.execute(() -> PacketDistributor.sendToAllPlayers(SyncEventRegistryPayload.current()));
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "cutscene_events");
        }

        @Override
        protected void apply(
                @javax.annotation.Nonnull Map<ResourceLocation, JsonElement> objects,
                @javax.annotation.Nonnull ResourceManager resourceManager,
                @javax.annotation.Nonnull ProfilerFiller profiler
        ) {
            Candidate candidate = parseResources(objects);
            if (applyLocalCandidate(candidate, "reload")) {
                syncOnlinePlayers();
            }
        }
    }

    private record Candidate(
            Map<ResourceLocation, EventData> definitions,
            Map<ResourceLocation, String> sources,
            Map<String, String> rawJsonById,
            List<DefinitionDiagnostic> diagnostics
    ) {
    }

    static Catalog catalog() {
        return catalog;
    }

    record Catalog(
            DefinitionSnapshot<EventData> definitions,
            RegistryState state
    ) {
        Catalog {
            definitions = java.util.Objects.requireNonNull(
                    definitions, "definitions");
            state = java.util.Objects.requireNonNull(
                    state, "state");
        }

        private static Catalog empty() {
            return new Catalog(
                    DefinitionSnapshot.empty(),
                    RegistryState.empty());
        }
    }

    record RegistryState(
            Map<String, EventData> byId,
            Map<String, List<EventData>> byLocation,
            Map<String, List<EventData>> byNpc,
            List<EventData> timeCheckEvents,
            Map<String, String> rawJsonById,
            Map<String, String> canonicalByAlias,
            Map<String, Set<String>> equivalentIdsByCanonical
    ) {
        private static RegistryState empty() {
            return new RegistryState(
                    Map.of(), Map.of(), Map.of(), List.of(), Map.of(), Map.of(), Map.of());
        }

        private static RegistryState build(
                Map<ResourceLocation, EventData> definitions,
                Map<String, String> rawJsonById
        ) {
            Map<String, EventData> byId = new LinkedHashMap<>();
            Map<String, List<EventData>> byLocation = new HashMap<>();
            Map<String, List<EventData>> byNpc = new HashMap<>();
            List<EventData> timeChecks = new ArrayList<>();
            Map<String, String> canonicalByAlias = new HashMap<>();
            Map<String, Set<String>> equivalentIdsByCanonical = new HashMap<>();

            for (EventData data : definitions.values()) {
                byId.put(data.id(), data);
                canonicalByAlias.put(data.id(), data.id());
                LinkedHashSet<String> equivalentIds = new LinkedHashSet<>();
                equivalentIds.add(data.id());
                for (String legacyId : data.legacyIds()) {
                    canonicalByAlias.put(legacyId, data.id());
                    equivalentIds.add(legacyId);
                }
                equivalentIdsByCanonical.put(data.id(), Set.copyOf(equivalentIds));
                switch (data.trigger().type()) {
                    case "enter_area" -> byLocation
                            .computeIfAbsent(data.trigger().location(), ignored -> new ArrayList<>())
                            .add(data);
                    case "interact_npc" -> byNpc
                            .computeIfAbsent(data.trigger().npc(), ignored -> new ArrayList<>())
                            .add(data);
                    case "time_check" -> timeChecks.add(data);
                    default -> {
                    }
                }
            }
            return new RegistryState(
                    Map.copyOf(byId), immutableLists(byLocation), immutableLists(byNpc),
                    List.copyOf(timeChecks), Map.copyOf(rawJsonById),
                    Map.copyOf(canonicalByAlias), Map.copyOf(equivalentIdsByCanonical));
        }

        private static Map<String, List<EventData>> immutableLists(
                Map<String, List<EventData>> source
        ) {
            Map<String, List<EventData>> result = new HashMap<>();
            source.forEach((key, value) -> result.put(key, List.copyOf(value)));
            return Map.copyOf(result);
        }
    }
}
