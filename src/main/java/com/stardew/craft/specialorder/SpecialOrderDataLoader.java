package com.stardew.craft.specialorder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.content.AtomicDefinitionStore;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import com.stardew.craft.api.v1.content.DefinitionSnapshot;
import com.stardew.craft.api.v1.specialorder.StardewSpecialOrderObjectives;
import com.stardew.craft.api.v1.specialorder.StardewSpecialOrderRewards;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Atomic loader for namespaced special-order definitions. */
@SuppressWarnings("null")
public final class SpecialOrderDataLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicDefinitionStore<SpecialOrderDefinition> STORE = new AtomicDefinitionStore<>();

    private SpecialOrderDataLoader() {
    }

    @Nullable
    public static SpecialOrderDefinition get(String id) {
        return STORE.snapshot().definitions().values().stream()
                .filter(definition -> definition.id().equals(id))
                .findFirst().orElse(null);
    }

    public static List<SpecialOrderDefinition> all() {
        return List.copyOf(STORE.snapshot().definitions().values());
    }

    public static DefinitionSnapshot<SpecialOrderDefinition> snapshot() {
        return STORE.snapshot();
    }

    private static Candidate parse(Map<ResourceLocation, JsonElement> objects) {
        Map<ResourceLocation, SpecialOrderDefinition> definitions = new LinkedHashMap<>();
        Map<ResourceLocation, String> sources = new LinkedHashMap<>();
        List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
            ResourceLocation id = entry.getKey();
            if (entry.getValue() == null || !entry.getValue().isJsonObject()) {
                diagnostics.add(DefinitionDiagnostic.error(id, id, "Special-order definition must be an object"));
                continue;
            }
            try {
                SpecialOrderDefinition definition = parseDefinition(id, entry.getValue().getAsJsonObject(), diagnostics);
                if (definition != null) {
                    definitions.put(id, definition);
                    sources.put(id, GSON.toJson(entry.getValue()));
                }
            } catch (RuntimeException exception) {
                diagnostics.add(DefinitionDiagnostic.error(
                        id, id, "Failed to parse special order: " + exception.getMessage()));
            }
        }
        return new Candidate(definitions, sources, diagnostics);
    }

    @Nullable
    private static SpecialOrderDefinition parseDefinition(
            ResourceLocation id, JsonObject root, List<DefinitionDiagnostic> diagnostics) {
        String requester = requiredString(root, "requester");
        SpecialOrderDefinition.Duration duration = SpecialOrderDefinition.Duration.valueOf(
                requiredString(root, "duration").toUpperCase(Locale.ROOT));
        boolean repeatable = getBoolean(root, "repeatable", false);
        String title = requiredString(root, "title");
        String text = requiredString(root, "text");

        List<SpecialOrderDefinition.ObjectiveDefinition> objectives = new ArrayList<>();
        JsonArray objectiveArray = root.getAsJsonArray("objectives");
        if (objectiveArray == null || objectiveArray.isEmpty()) {
            throw new IllegalArgumentException("objectives must contain at least one entry");
        }
        for (JsonElement element : objectiveArray) {
            SpecialOrderDefinition.ObjectiveDefinition objective = parseObjective(id, element.getAsJsonObject(), diagnostics);
            if (objective != null) objectives.add(objective);
        }

        List<SpecialOrderDefinition.RewardDefinition> rewards = new ArrayList<>();
        if (root.has("rewards")) {
            for (JsonElement element : root.getAsJsonArray("rewards")) {
                SpecialOrderDefinition.RewardDefinition reward = parseReward(id, element.getAsJsonObject(), diagnostics);
                if (reward != null) rewards.add(reward);
            }
        }
        if (objectives.size() != objectiveArray.size()) return null;

        return new SpecialOrderDefinition(
                getString(root, "legacy_id", id.toString()), requester, duration, repeatable,
                stringList(root, "required_tags"),
                title, text, objectives, rewards, parseRandomElements(root),
                getString(root, "item_to_remove_on_end", null),
                getString(root, "mail_to_remove_on_end", null));
    }

    @Nullable
    private static SpecialOrderDefinition.ObjectiveDefinition parseObjective(
            ResourceLocation source, JsonObject root, List<DefinitionDiagnostic> diagnostics) {
        String rawType = requiredString(root, "type");
        String text = requiredString(root, "text");
        int count = Math.max(1, getInt(root, "required_count", 1));
        try {
            var builtin = SpecialOrderDefinition.ObjectiveType.valueOf(rawType.toUpperCase(Locale.ROOT));
            return new SpecialOrderDefinition.ObjectiveDefinition(
                    builtin, text, count, getString(root, "accepted_tags", ""),
                    getString(root, "drop_box", ""), getString(root, "target", ""),
                    getInt(root, "minimum_capacity", 0), getString(root, "message", ""));
        } catch (IllegalArgumentException ignored) {
        }
        ResourceLocation type = ResourceLocation.tryParse(rawType);
        if (type == null || rawType.indexOf(':') < 1) {
            diagnostics.add(DefinitionDiagnostic.error(source, source, "Invalid objective type: " + rawType));
            return null;
        }
        JsonElement data = root.has("data") ? root.get("data") : new JsonObject();
        var decoded = StardewSpecialOrderObjectives.decode(type, data);
        var objective = decoded.resultOrPartial(message -> diagnostics.add(
                DefinitionDiagnostic.error(source, source, message))).orElse(null);
        return objective == null ? null : new SpecialOrderDefinition.ObjectiveDefinition(
                null, text, count, "", "", "", 0, "", objective, type.toString());
    }

    @Nullable
    private static SpecialOrderDefinition.RewardDefinition parseReward(
            ResourceLocation source, JsonObject root, List<DefinitionDiagnostic> diagnostics) {
        String rawType = requiredString(root, "type");
        try {
            var builtin = SpecialOrderDefinition.RewardType.valueOf(rawType.toUpperCase(Locale.ROOT));
            return new SpecialOrderDefinition.RewardDefinition(
                    builtin, getInt(root, "amount", 0), getString(root, "mail", ""),
                    getBoolean(root, "no_letter", false), getBoolean(root, "host", false));
        } catch (IllegalArgumentException ignored) {
        }
        ResourceLocation type = ResourceLocation.tryParse(rawType);
        if (type == null || rawType.indexOf(':') < 1) {
            diagnostics.add(DefinitionDiagnostic.error(source, source, "Invalid reward type: " + rawType));
            return null;
        }
        JsonElement data = root.has("data") ? root.get("data") : new JsonObject();
        var decoded = StardewSpecialOrderRewards.decode(type, data);
        var reward = decoded.resultOrPartial(message -> diagnostics.add(
                DefinitionDiagnostic.error(source, source, message))).orElse(null);
        return reward == null ? null : new SpecialOrderDefinition.RewardDefinition(
                null, 0, "", false, false, reward, type.toString());
    }

    private static List<SpecialOrderDefinition.RandomElement> parseRandomElements(JsonObject root) {
        if (!root.has("random_elements")) return List.of();
        List<SpecialOrderDefinition.RandomElement> elements = new ArrayList<>();
        for (JsonElement rawElement : root.getAsJsonArray("random_elements")) {
            JsonObject element = rawElement.getAsJsonObject();
            List<SpecialOrderDefinition.RandomOption> options = new ArrayList<>();
            for (JsonElement rawOption : element.getAsJsonArray("options")) {
                JsonObject option = rawOption.getAsJsonObject();
                Map<String, String> values = new LinkedHashMap<>();
                option.getAsJsonObject("values").entrySet().forEach(
                        entry -> values.put(entry.getKey(), entry.getValue().getAsString()));
                options.add(new SpecialOrderDefinition.RandomOption(stringList(option, "required_tags"), values));
            }
            elements.add(new SpecialOrderDefinition.RandomElement(requiredString(element, "name"), options));
        }
        return elements;
    }

    private static String requiredString(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("missing string field '" + key + "'");
        }
        return root.get(key).getAsString();
    }

    private static String getString(JsonObject root, String key, String fallback) {
        return root.has(key) && !root.get(key).isJsonNull() ? root.get(key).getAsString() : fallback;
    }

    private static int getInt(JsonObject root, String key, int fallback) {
        return root.has(key) ? root.get(key).getAsInt() : fallback;
    }

    private static boolean getBoolean(JsonObject root, String key, boolean fallback) {
        return root.has(key) ? root.get(key).getAsBoolean() : fallback;
    }

    private static List<String> stringList(JsonObject root, String key) {
        if (!root.has(key)) return List.of();
        List<String> values = new ArrayList<>();
        root.getAsJsonArray(key).forEach(value -> values.add(value.getAsString()));
        return values;
    }

    private static void finish(AtomicDefinitionStore.ApplyResult<SpecialOrderDefinition> result) {
        for (DefinitionDiagnostic diagnostic : result.diagnostics()) {
            String source = diagnostic.source() == null ? "<special-order reload>" : diagnostic.source().toString();
            if (diagnostic.severity() == DefinitionDiagnostic.Severity.ERROR) {
                StardewCraft.LOGGER.error("[Special order] Definition error [{}]: {}", source, diagnostic.message());
            } else {
                StardewCraft.LOGGER.warn("[Special order] Definition warning [{}]: {}", source, diagnostic.message());
            }
        }
        if (!result.accepted()) {
            StardewCraft.LOGGER.error("[Special order] Rejected snapshot; keeping v{} with {} definitions",
                    result.snapshot().version(), result.snapshot().definitions().size());
        } else {
            StardewCraft.LOGGER.info("[Special order] Applied snapshot v{} ({} definitions)",
                    result.snapshot().version(), result.snapshot().definitions().size());
        }
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() { super(GSON, "special_orders"); }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
            Candidate candidate = parse(objects);
            finish(STORE.applyLocal(candidate.definitions(), candidate.sources(), candidate.diagnostics()));
        }
    }

    private record Candidate(
            Map<ResourceLocation, SpecialOrderDefinition> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics
    ) {
    }
}
