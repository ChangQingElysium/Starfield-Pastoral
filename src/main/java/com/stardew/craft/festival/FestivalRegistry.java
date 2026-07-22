package com.stardew.craft.festival;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.content.AtomicDefinitionStore;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import com.stardew.craft.api.v1.content.DefinitionSnapshot;
import com.stardew.craft.api.v1.festival.StardewFestivalDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Atomic, namespaced festival metadata with legacy ID lookup for existing saves and handlers. */
@SuppressWarnings("null")
public final class FestivalRegistry {
    public static final int SPRING = 0;
    public static final int SUMMER = 1;
    public static final int FALL = 2;
    public static final int WINTER = 3;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<ResourceLocation> PLAYER_CONTEXT_CONDITIONS = Set.of(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "has_item"),
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "money"),
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "flag"),
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "skill")
    );
    private static final AtomicDefinitionStore<StardewFestivalDefinition> STORE = new AtomicDefinitionStore<>();
    private static volatile Map<String, FestivalDefinition> aliases = Map.of();
    private static volatile List<FestivalDefinition> ordered = List.of();
    private static volatile List<FestivalDefinition> activeOrdered = List.of();
    private static volatile List<FestivalDefinition> passiveOrdered = List.of();
    private static volatile String cachedJson = "{}";

    private FestivalRegistry() {
    }

    public static DefinitionSnapshot<StardewFestivalDefinition> snapshot() {
        return STORE.snapshot();
    }

    public static Optional<FestivalDefinition> get(ResourceLocation id) {
        return id == null ? Optional.empty() : get(id.toString());
    }

    public static Optional<FestivalDefinition> get(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return Optional.ofNullable(aliases.get(normalizeId(id)));
    }

    public static Optional<FestivalDefinition> getByOverlayId(String overlayId) {
        if (overlayId == null || overlayId.isBlank()) return Optional.empty();
        String normalized = overlayId.toLowerCase(Locale.ROOT);
        return ordered.stream()
                .filter(definition -> definition.mapOverlayId().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }

    public static Collection<FestivalDefinition> all() {
        return ordered;
    }

    public static List<FestivalDefinition> activeFestivals() {
        return activeOrdered;
    }

    public static List<FestivalDefinition> passiveFestivals() {
        return passiveOrdered;
    }

    public static String getCachedJson() {
        return cachedJson;
    }

    public static void applyFromJson(String json) {
        if (json == null || json.isBlank()) return;
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (RuntimeException exception) {
            StardewCraft.LOGGER.error("[Festival] Failed to parse synchronized definitions", exception);
            return;
        }
        if (!root.isJsonObject()) return;
        Map<ResourceLocation, StardewFestivalDefinition> definitions = new LinkedHashMap<>();
        Map<ResourceLocation, String> sources = new LinkedHashMap<>();
        List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
        root.getAsJsonObject().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> decode(ResourceLocation.tryParse(entry.getKey()), entry.getValue(),
                        definitions, sources, diagnostics));
        applyCandidate(definitions, sources, diagnostics, false);
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "festivals");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager,
                             ProfilerFiller profiler) {
            Map<ResourceLocation, StardewFestivalDefinition> definitions = new LinkedHashMap<>();
            Map<ResourceLocation, String> sources = new LinkedHashMap<>();
            List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
            objects.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                    .forEach(entry -> decode(entry.getKey(), entry.getValue(), definitions, sources, diagnostics));
            applyCandidate(definitions, sources, diagnostics, true);
        }
    }

    private static void decode(ResourceLocation id, JsonElement json,
                               Map<ResourceLocation, StardewFestivalDefinition> definitions,
                               Map<ResourceLocation, String> sources,
                               List<DefinitionDiagnostic> diagnostics) {
        if (id == null) {
            diagnostics.add(DefinitionDiagnostic.error(null, null, "Invalid festival ID"));
            return;
        }
        StardewFestivalDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(message -> diagnostics.add(DefinitionDiagnostic.error(id, id, message)))
                .ifPresent(definition -> {
                    List<ResourceLocation> playerConditions = definition.availableWhen().stream()
                            .map(condition -> condition.type())
                            .filter(FestivalRegistry::requiresPlayerContext)
                            .distinct()
                            .toList();
                    if (!playerConditions.isEmpty()) {
                        diagnostics.add(DefinitionDiagnostic.error(id, id,
                                "Festival available_when is world-scoped and cannot use player conditions: "
                                        + playerConditions));
                        return;
                    }
                    if (definitions.putIfAbsent(id, definition) != null) {
                        diagnostics.add(DefinitionDiagnostic.error(id, id, "Duplicate festival ID"));
                        return;
                    }
                    StardewFestivalDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
                            .resultOrPartial(message -> diagnostics.add(DefinitionDiagnostic.error(id, id, message)))
                            .ifPresent(encoded -> sources.put(id, GSON.toJson(encoded)));
                });
    }

    static boolean requiresPlayerContext(ResourceLocation conditionType) {
        return PLAYER_CONTEXT_CONDITIONS.contains(conditionType);
    }

    private static void applyCandidate(Map<ResourceLocation, StardewFestivalDefinition> definitions,
                                       Map<ResourceLocation, String> sources,
                                       List<DefinitionDiagnostic> diagnostics,
                                       boolean updateCache) {
        var result = STORE.applyLocal(definitions, sources, diagnostics);
        for (DefinitionDiagnostic diagnostic : result.diagnostics()) {
            if (diagnostic.severity() == DefinitionDiagnostic.Severity.ERROR) {
                StardewCraft.LOGGER.error("[Festival] {}", diagnostic.message());
            } else {
                StardewCraft.LOGGER.warn("[Festival] {}", diagnostic.message());
            }
        }
        if (!result.accepted()) {
            StardewCraft.LOGGER.error("[Festival] Rejected reload; keeping {} festivals", ordered.size());
            return;
        }

        List<FestivalDefinition> next = result.snapshot().definitions().entrySet().stream()
                .map(entry -> toRuntime(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(FestivalDefinition::season)
                        .thenComparingInt(FestivalDefinition::startDay)
                        .thenComparing(definition -> definition.resourceId().toString()))
                .toList();
        Map<String, FestivalDefinition> nextAliases = new LinkedHashMap<>();
        for (FestivalDefinition definition : next) {
            putAlias(nextAliases, definition.resourceId().toString(), definition);
            putAlias(nextAliases, definition.id(), definition);
            if (definition.resourceId().getNamespace().equals(StardewCraft.MODID)) {
                putAlias(nextAliases, definition.resourceId().getPath(), definition);
            }
        }
        ordered = List.copyOf(next);
        activeOrdered = ordered.stream()
                .filter(definition -> definition.type() == FestivalType.ACTIVE)
                .toList();
        passiveOrdered = ordered.stream()
                .filter(definition -> definition.type() == FestivalType.PASSIVE)
                .toList();
        aliases = Map.copyOf(nextAliases);
        if (updateCache) {
            var root = new com.google.gson.JsonObject();
            sources.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> root.add(entry.getKey().toString(), JsonParser.parseString(entry.getValue())));
            cachedJson = GSON.toJson(root);
        }
        StardewCraft.LOGGER.info("[Festival] Applied {} festivals", ordered.size());
    }

    private static FestivalDefinition toRuntime(ResourceLocation resourceId, StardewFestivalDefinition source) {
        String runtimeId = source.legacyId().isBlank() ? resourceId.toString() : source.legacyId();
        return new FestivalDefinition(
                resourceId, runtimeId,
                source.type() == StardewFestivalDefinition.FestivalKind.ACTIVE
                        ? FestivalType.ACTIVE : FestivalType.PASSIVE,
                source.displayName(), source.presentation().displayToken(), source.legacyCondition(), source.availableWhen(),
                source.season(), source.startDay(), source.endDay(), source.startTime(), source.endTime(),
                source.showOnCalendar(), source.onlyShowStartMessageOnFirstDay(), source.presentation().startMessageToken(),
                source.startMessageKey(), source.announcementMailId(), source.world().location(), source.world().mapOverlay(),
                source.world().mapReplacements(), source.world().shops(), source.world().mechanicId());
    }

    private static void putAlias(Map<String, FestivalDefinition> target, String alias,
                                 FestivalDefinition definition) {
        if (alias == null || alias.isBlank()) return;
        FestivalDefinition previous = target.putIfAbsent(normalizeId(alias), definition);
        if (previous != null && previous != definition) {
            StardewCraft.LOGGER.warn("[Festival] Alias {} is already owned by {}; ignoring {}",
                    alias, previous.resourceId(), definition.resourceId());
        }
    }

    private static String normalizeId(String id) {
        return id.trim().toLowerCase(Locale.ROOT);
    }
}
