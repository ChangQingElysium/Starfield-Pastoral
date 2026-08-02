package com.stardew.craft.quest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.content.AtomicDefinitionStore;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import com.stardew.craft.api.v1.content.DefinitionSnapshot;
import com.stardew.craft.api.v1.quest.QuestText;
import com.stardew.craft.api.v1.quest.StardewQuestDefinition;
import com.stardew.craft.api.v1.quest.StardewQuestObjective;
import com.stardew.craft.api.v1.quest.StardewQuestObjectives;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Atomic namespaced quest-definition registry with a legacy Quests.json adapter. */
@SuppressWarnings("null")
public final class QuestDataLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ResourceLocation LEGACY_SOURCE = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "legacy/quests.json");
    private static final AtomicDefinitionStore<StardewQuestDefinition> STORE = new AtomicDefinitionStore<>();

    private QuestDataLoader() {
    }

    /** Compatibility startup hook. Datapack reload normally initializes the registry first. */
    public static synchronized void load() {
        if (!STORE.snapshot().definitions().isEmpty()) {
            return;
        }
        Candidate candidate = parseCandidate(Map.of());
        finishApply(STORE.applyLocal(candidate.definitions(), candidate.sources(), candidate.diagnostics()), "startup");
    }

    @Nullable
    public static StardewQuest createQuest(String questId) {
        ResourceLocation id = normalizeId(questId);
        if (id == null) {
            return null;
        }
        StardewQuestDefinition definition = STORE.snapshot().definitions().get(id);
        return definition == null ? null : new DataDrivenQuest(id, definition);
    }

    @Nullable
    public static StardewQuestDefinition getDefinition(ResourceLocation id) {
        return STORE.snapshot().definitions().get(id);
    }

    @Nullable
    public static ResourceLocation normalizeId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        return rawId.indexOf(':') >= 0
                ? ResourceLocation.tryParse(rawId)
                : ResourceLocation.tryBuild(StardewCraft.MODID, rawId);
    }

    public static boolean idsEqual(String left, String right) {
        ResourceLocation leftId = normalizeId(left);
        ResourceLocation rightId = normalizeId(right);
        return leftId != null && leftId.equals(rightId);
    }

    public static Set<String> getAllQuestIds() {
        return STORE.snapshot().definitions().keySet().stream()
                .map(QuestDataLoader::displayId)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static Collection<ResourceLocation> getAllDefinitionIds() {
        return STORE.snapshot().definitions().keySet();
    }

    public static DefinitionSnapshot<StardewQuestDefinition> snapshot() {
        return STORE.snapshot();
    }

    static String displayId(ResourceLocation id) {
        if (StardewCraft.MODID.equals(id.getNamespace()) && id.getPath().matches("\\d+")) {
            return id.getPath();
        }
        return id.toString();
    }

    private static Candidate parseCandidate(Map<ResourceLocation, JsonElement> modernResources) {
        Map<ResourceLocation, StardewQuestDefinition> definitions = new LinkedHashMap<>();
        Map<ResourceLocation, String> sources = new LinkedHashMap<>();
        List<DefinitionDiagnostic> diagnostics = new ArrayList<>();

        parseLegacyDefinitions(definitions, sources, diagnostics);
        for (Map.Entry<ResourceLocation, JsonElement> entry : modernResources.entrySet()) {
            ResourceLocation id = entry.getKey();
            JsonElement root = entry.getValue();
            if (root == null || !root.isJsonObject()) {
                diagnostics.add(DefinitionDiagnostic.error(id, id, "Quest definition must be a JSON object"));
                continue;
            }
            StardewQuestDefinition.CODEC.parse(JsonOps.INSTANCE, root)
                    .resultOrPartial(message -> diagnostics.add(DefinitionDiagnostic.error(id, id, message)))
                    .ifPresent(definition -> {
                        definitions.put(id, definition);
                        sources.put(id, GSON.toJson(root));
                    });
        }
        return new Candidate(definitions, sources, diagnostics);
    }

    private static void parseLegacyDefinitions(
            Map<ResourceLocation, StardewQuestDefinition> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics
    ) {
        try (InputStream stream = QuestDataLoader.class.getResourceAsStream("/data/stardewcraft/quests.json")) {
            if (stream == null) {
                diagnostics.add(DefinitionDiagnostic.error(LEGACY_SOURCE, null, "Legacy quests.json was not found"));
                return;
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                Type mapType = new TypeToken<Map<String, String>>() { }.getType();
                Map<String, String> parsed = GSON.fromJson(reader, mapType);
                if (parsed == null) {
                    return;
                }
                for (Map.Entry<String, String> entry : parsed.entrySet()) {
                    ResourceLocation id = normalizeId(entry.getKey());
                    if (id == null) {
                        diagnostics.add(DefinitionDiagnostic.error(
                                LEGACY_SOURCE, null, "Invalid legacy quest ID: " + entry.getKey()));
                        continue;
                    }
                    try {
                        StardewQuestDefinition definition = parseLegacyDefinition(id, entry.getValue());
                        definitions.put(id, definition);
                        sources.put(id, entry.getValue());
                    } catch (RuntimeException exception) {
                        diagnostics.add(DefinitionDiagnostic.error(
                                LEGACY_SOURCE, id, "Failed to migrate legacy quest: " + exception.getMessage()));
                    }
                }
            }
        } catch (Exception exception) {
            diagnostics.add(DefinitionDiagnostic.error(
                    LEGACY_SOURCE, null, "Failed to read legacy quests.json: " + exception.getMessage()));
        }
    }

    private static StardewQuestDefinition parseLegacyDefinition(ResourceLocation id, String raw) {
        String[] parts = raw.split("/", -1);
        if (parts.length < 9) {
            throw new IllegalArgumentException("expected at least 9 slash-delimited fields, got " + parts.length);
        }
        JsonObject objectiveData = new JsonObject();
        String[] tokens = normalizedTokens(parts[4]);
        ResourceLocation objectiveType = legacyObjective(parts[0].trim(), tokens, objectiveData);
        if (parts.length > 9 && !parts[9].isBlank()) {
            objectiveData.addProperty("target_message", parts[9].trim());
        }
        StardewQuestObjective objective = StardewQuestObjectives.decode(objectiveType, objectiveData)
                .getOrThrow(message -> new IllegalArgumentException("objective: " + message));

        List<ResourceLocation> next = new ArrayList<>();
        String rawNext = parts[5].trim();
        if (!rawNext.isBlank() && !"null".equalsIgnoreCase(rawNext) && !"-1".equals(rawNext)) {
            for (String value : rawNext.split("\\s+")) {
                String cleaned = value.startsWith("h") ? value.substring(1) : value;
                ResourceLocation nextId = normalizeId(cleaned);
                if (nextId != null) {
                    next.add(nextId);
                }
            }
        }

        int money = parseNonNegative(parts[6]);
        Optional<QuestText> rewardDescription = legacyText(parts[7]).isEmpty()
                ? Optional.empty() : Optional.of(legacyText(parts[7]));
        return new StardewQuestDefinition(
                legacyText(parts[1]),
                legacyText(parts[2]),
                legacyText(parts[3]),
                objective,
                List.of(),
                List.of(),
                List.of(),
                next,
                money,
                rewardDescription,
                Boolean.parseBoolean(parts[8].trim()),
                -1
        );
    }

    private static ResourceLocation legacyObjective(String type, String[] tokens, JsonObject data) {
        switch (type) {
            case "Basic" -> {
                return objectiveId("basic");
            }
            case "ItemHarvest" -> {
                requireTokens(type, tokens, 2);
                data.addProperty("item", tokens[0]);
                data.addProperty("count", parsePositive(tokens[1], 1));
                return objectiveId("item_harvest");
            }
            case "Crafting" -> {
                requireTokens(type, tokens, 1);
                data.addProperty("recipe", tokens[0]);
                return objectiveId("crafting");
            }
            case "Building" -> {
                requireTokens(type, tokens, 1);
                data.addProperty("building", tokens[0]);
                return objectiveId("building");
            }
            case "Location" -> {
                requireTokens(type, tokens, 1);
                data.addProperty("location", tokens[0]);
                return objectiveId("location");
            }
            case "ItemDelivery" -> {
                requireTokens(type, tokens, 2);
                data.addProperty("target_npc", tokens[0]);
                data.addProperty("item", tokens[1]);
                data.addProperty("count", tokens.length >= 3 ? parsePositive(tokens[2], 1) : 1);
                return objectiveId("item_delivery");
            }
            case "LostItem" -> {
                requireTokens(type, tokens, 2);
                data.addProperty("target_npc", tokens[0]);
                data.addProperty("item", tokens[1]);
                data.addProperty("friendship", 250);
                return objectiveId("lost_item");
            }
            case "SecretLostItem" -> {
                requireTokens(type, tokens, 4);
                data.addProperty("target_npc", tokens[0]);
                data.addProperty("item", tokens[1]);
                data.addProperty("friendship", parseNonNegative(tokens[2]));
                data.addProperty("exclusive_quest", tokens[3]);
                return objectiveId("secret_lost_item");
            }
            case "Monster" -> {
                requireTokens(type, tokens, 2);
                data.addProperty("monster", tokens[0]);
                data.addProperty("count", parsePositive(tokens[1], 1));
                if (tokens.length >= 3) data.addProperty("target_npc", tokens[2]);
                return objectiveId("monster");
            }
            case "Fishing" -> {
                requireTokens(type, tokens, 2);
                int itemIndex = tokens.length >= 3 ? 1 : 0;
                if (tokens.length >= 3) data.addProperty("target_npc", tokens[0]);
                data.addProperty("item", tokens[itemIndex]);
                data.addProperty("count", parsePositive(tokens[itemIndex + 1], 1));
                return objectiveId("fishing");
            }
            case "Resource", "ResourceCollect" -> {
                requireTokens(type, tokens, 2);
                data.addProperty("item", tokens[0]);
                data.addProperty("count", parsePositive(tokens[1], 1));
                if (tokens.length >= 3) data.addProperty("target_npc", tokens[2]);
                return objectiveId("resource");
            }
            case "Social" -> {
                return objectiveId("socialize");
            }
            default -> throw new IllegalArgumentException("unsupported legacy quest type " + type);
        }
    }

    private static QuestText legacyText(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || "null".equalsIgnoreCase(normalized) || "-1".equals(normalized)) {
            return QuestText.empty();
        }
        return normalized.startsWith("stardewcraft.")
                ? QuestText.translated(normalized)
                : new QuestText("", normalized, List.of());
    }

    private static int parseNonNegative(String value) {
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static int parsePositive(String value, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String[] normalizedTokens(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isBlank() || "null".equalsIgnoreCase(normalized) || "-1".equals(normalized)
                ? new String[0] : normalized.split("\\s+");
    }

    private static void requireTokens(String type, String[] tokens, int required) {
        if (tokens.length < required) {
            throw new IllegalArgumentException(type + " objective expected " + required + " condition tokens");
        }
    }

    private static ResourceLocation objectiveId(String path) {
        return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, path);
    }

    private static void finishApply(
            AtomicDefinitionStore.ApplyResult<StardewQuestDefinition> result,
            String operation
    ) {
        for (DefinitionDiagnostic diagnostic : result.diagnostics()) {
            String source = diagnostic.source() == null ? "<quest reload>" : diagnostic.source().toString();
            if (diagnostic.severity() == DefinitionDiagnostic.Severity.ERROR) {
                StardewCraft.LOGGER.error("[Quest] Definition error [{}]: {}", source, diagnostic.message());
            } else {
                StardewCraft.LOGGER.warn("[Quest] Definition warning [{}]: {}", source, diagnostic.message());
            }
        }
        if (!result.accepted()) {
            StardewCraft.LOGGER.error("[Quest] Rejected {} snapshot; keeping v{} with {} definitions",
                    operation, result.snapshot().version(), result.snapshot().definitions().size());
            return;
        }
        StardewCraft.LOGGER.info("[Quest] Applied {} snapshot v{} ({} definitions)",
                operation, result.snapshot().version(), result.snapshot().definitions().size());
        if (result.changed()) {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null && server.getPlayerList().getPlayerCount() > 0) {
                server.execute(() -> server.getPlayerList().getPlayers().forEach(player -> {
                    QuestManager manager = QuestManager.of(player);
                    if (manager != null) {
                        manager.refreshDefinitions(player);
                    }
                }));
            }
        }
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "quests");
        }

        @Override
        protected void apply(
                Map<ResourceLocation, JsonElement> objects,
                ResourceManager resourceManager,
                ProfilerFiller profiler
        ) {
            Candidate candidate = parseCandidate(objects);
            finishApply(STORE.applyLocal(
                    candidate.definitions(), candidate.sources(), candidate.diagnostics()), "datapack reload");
        }
    }

    private record Candidate(
            Map<ResourceLocation, StardewQuestDefinition> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics
    ) {
    }
}
