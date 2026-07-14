package com.stardew.craft.museum;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.action.StardewAction;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.api.v1.museum.StardewMuseumRewardDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Atomic museum milestones. Built-in IDs remain unchanged for save compatibility. */
@SuppressWarnings("null")
public final class MuseumRewardRegistry {
    public static final String RUSTY_KEY_REWARD_ID = "museum60";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ResourceLocation LEGACY_TABLE =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "rewards");
    private static volatile Map<String, MuseumReward> rewards = Map.of();

    public record MuseumReward(
            String id,
            String condition,
            int threshold,
            List<String> requiredIds,
            List<StardewAction> actions
    ) {
        public MuseumReward {
            requiredIds = List.copyOf(requiredIds);
            actions = List.copyOf(actions);
        }
    }

    private MuseumRewardRegistry() {
    }

    public static List<MuseumReward> getAllRewards() {
        return List.copyOf(rewards.values());
    }

    public static Item resolveItem(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return null;
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == Items.AIR ? null : item;
    }

    public static List<MuseumReward> getClaimableRewards(
            MuseumDonationData data,
            java.util.UUID playerId,
            Set<String> claimedIds
    ) {
        Set<String> donated = data.getDonatedItems(playerId);
        int minerals = 0;
        int artifacts = 0;
        for (String itemId : donated) {
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;
            var metadata = StardewItemDataApi.resolve(new ItemStack(BuiltInRegistries.ITEM.get(id))).orElse(null);
            if (metadata == null) continue;
            String category = metadata.category().getPath();
            if ("mineral".equals(category)) minerals++;
            if ("artifact".equals(category) || "artifact_quality".equals(category)) artifacts++;
        }

        List<MuseumReward> result = new ArrayList<>();
        for (MuseumReward reward : rewards.values()) {
            if (claimedIds.contains(reward.id())) continue;
            boolean qualifies = switch (reward.condition()) {
                case "total_count" -> donated.size() >= reward.threshold();
                case "mineral_count" -> minerals >= reward.threshold();
                case "artifact_count" -> artifacts >= reward.threshold();
                case "specific_items" -> donated.containsAll(reward.requiredIds());
                default -> false;
            };
            if (qualifies) result.add(reward);
        }
        return result;
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "museum_rewards");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
            Map<String, MuseumReward> next = new LinkedHashMap<>();
            List<String> errors = new ArrayList<>();
            JsonElement legacy = objects.get(LEGACY_TABLE);
            if (legacy == null || !legacy.isJsonArray()) {
                errors.add("Missing " + LEGACY_TABLE);
            } else {
                for (JsonElement raw : legacy.getAsJsonArray()) {
                    JsonObject object = raw.getAsJsonObject().deepCopy();
                    String id = object.has("id") ? object.remove("id").getAsString() : "";
                    decode(LEGACY_TABLE, id, object, next, errors);
                }
            }
            objects.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(ResourceLocation::toString)))
                    .filter(entry -> entry.getKey().getPath().startsWith("rewards/"))
                    .forEach(entry -> {
                        String path = entry.getKey().getPath().substring("rewards/".length());
                        ResourceLocation id = ResourceLocation.tryBuild(entry.getKey().getNamespace(), path);
                        decode(entry.getKey(), id == null ? "" : id.toString(), entry.getValue(), next, errors);
                    });
            if (!errors.isEmpty()) {
                errors.forEach(error -> StardewCraft.LOGGER.error("[Museum reward] {}", error));
                StardewCraft.LOGGER.error("[Museum reward] Rejected reload; keeping {} rewards", rewards.size());
                return;
            }
            rewards = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(next));
            StardewCraft.LOGGER.info("[Museum reward] Applied {} rewards", rewards.size());
        }
    }

    private static void decode(ResourceLocation source, String id, JsonElement json,
                               Map<String, MuseumReward> next, List<String> errors) {
        if (id == null || id.isBlank()) {
            errors.add(source + ": missing or invalid reward ID");
            return;
        }
        StardewMuseumRewardDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(message -> errors.add(source + " [" + id + "]: " + message))
                .ifPresent(definition -> {
                    MuseumReward reward = new MuseumReward(id, definition.condition(), definition.threshold(),
                            definition.requiredItems().stream().map(ResourceLocation::toString).toList(),
                            definition.rewards());
                    if (next.putIfAbsent(id, reward) != null) errors.add(source + ": duplicate reward ID " + id);
                });
    }
}
