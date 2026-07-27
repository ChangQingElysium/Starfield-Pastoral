package com.stardew.craft.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.action.StardewAction;
import com.stardew.craft.api.v1.guild.StardewMonsterSlayerGoalDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Atomic, namespaced monster-slayer goals with legacy storage IDs for built-in goals. */
@SuppressWarnings("null")
public final class MonsterSlayerGoalRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ResourceLocation LEGACY_TABLE =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "monster_slayer_goals");
    private static volatile Catalog catalog = Catalog.empty();

    public record SlayerGoal(
            String goalKey,
            String translationKey,
            int requiredKills,
            List<String> monsterTags,
            List<StardewAction> rewards
    ) {
        public SlayerGoal {
            monsterTags = List.copyOf(monsterTags);
            rewards = List.copyOf(rewards);
        }

        public boolean hasReward() {
            return !rewards.isEmpty();
        }
    }

    private MonsterSlayerGoalRegistry() {
    }

    @Nullable
    public static String getGoalKeyForTag(String monsterTag) {
        List<String> matches = catalog.tagToGoals()
                .getOrDefault(monsterTag, List.of());
        return matches.isEmpty() ? null : matches.getFirst();
    }

    public static List<String> getGoalKeysForTag(String monsterTag) {
        return catalog.tagToGoals().getOrDefault(monsterTag, List.of());
    }

    public static List<SlayerGoal> getAllGoals() {
        return List.copyOf(catalog.goals().values());
    }

    @Nullable
    public static SlayerGoal getGoal(String goalKey) {
        return catalog.goals().get(goalKey);
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "adventurers_guild");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
            Map<String, SlayerGoal> next = new LinkedHashMap<>();
            List<String> errors = new ArrayList<>();
            JsonElement legacy = objects.get(LEGACY_TABLE);
            if (legacy == null || !legacy.isJsonArray()) {
                errors.add("Missing " + LEGACY_TABLE);
            } else {
                for (JsonElement raw : legacy.getAsJsonArray()) {
                    if (!raw.isJsonObject()) {
                        errors.add(LEGACY_TABLE + ": entry must be an object");
                        continue;
                    }
                    JsonObject object = raw.getAsJsonObject().deepCopy();
                    String id = object.has("id") ? object.remove("id").getAsString() : "";
                    decode(LEGACY_TABLE, id, object, next, errors);
                }
            }

            objects.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(ResourceLocation::toString)))
                    .filter(entry -> entry.getKey().getPath().startsWith("monster_slayer_goals/"))
                    .forEach(entry -> {
                        String path = entry.getKey().getPath().substring("monster_slayer_goals/".length());
                        ResourceLocation id = ResourceLocation.tryBuild(entry.getKey().getNamespace(), path);
                        decode(entry.getKey(), id == null ? "" : id.toString(), entry.getValue(), next, errors);
                    });

            applyCandidate(next, errors);
        }
    }

    static synchronized void applyCandidate(
            Map<String, SlayerGoal> goals,
            List<String> errors
    ) {
        if (!errors.isEmpty()) {
            errors.forEach(error ->
                    StardewCraft.LOGGER.error(
                            "[Monster slayer] {}", error));
            StardewCraft.LOGGER.error(
                    "[Monster slayer] Rejected reload; keeping {} goals",
                    catalog.goals().size());
            return;
        }
        Map<String, List<String>> routing = new LinkedHashMap<>();
        goals.values().forEach(goal ->
                goal.monsterTags().forEach(tag ->
                        routing.computeIfAbsent(
                                tag, ignored -> new ArrayList<>())
                                .add(goal.goalKey())));
        routing.replaceAll((tag, ids) -> List.copyOf(ids));
        catalog = new Catalog(goals, routing);
        StardewCraft.LOGGER.info(
                "[Monster slayer] Applied {} goals across {} monster tags",
                catalog.goals().size(),
                catalog.tagToGoals().size());
    }

    private static void decode(ResourceLocation source, String id, JsonElement json,
                               Map<String, SlayerGoal> next, List<String> errors) {
        if (id == null || id.isBlank()) {
            errors.add(source + ": missing or invalid goal ID");
            return;
        }
        StardewMonsterSlayerGoalDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(message -> errors.add(source + " [" + id + "]: " + message))
                .ifPresent(definition -> {
                    if (next.putIfAbsent(id, new SlayerGoal(id, definition.translationKey(),
                            definition.requiredKills(), definition.monsterTags(), definition.rewards())) != null) {
                        errors.add(source + ": duplicate goal ID " + id);
                    }
                });
    }

    static Catalog catalog() {
        return catalog;
    }

    record Catalog(
            Map<String, SlayerGoal> goals,
            Map<String, List<String>> tagToGoals
    ) {
        Catalog {
            goals = java.util.Collections.unmodifiableMap(
                    new LinkedHashMap<>(goals));
            Map<String, List<String>> copiedRouting =
                    new LinkedHashMap<>();
            tagToGoals.forEach((tag, ids) ->
                    copiedRouting.put(tag, List.copyOf(ids)));
            tagToGoals = java.util.Collections.unmodifiableMap(
                    copiedRouting);
        }

        private static Catalog empty() {
            return new Catalog(Map.of(), Map.of());
        }
    }
}
