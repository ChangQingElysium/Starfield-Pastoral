package com.stardew.craft.api.v1.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Public registry and codec for data-driven quest objective types. */
public final class StardewQuestObjectives {
    private static final Map<ResourceLocation, StardewQuestObjectiveType<?>> TYPES = new LinkedHashMap<>();

    public static final Codec<StardewQuestObjective> CODEC = Codec.PASSTHROUGH.flatXmap(
            StardewQuestObjectives::decodeDynamic,
            StardewQuestObjectives::encodeDynamic
    );

    private StardewQuestObjectives() {
    }

    public static synchronized <T> void register(ResourceLocation id, StardewQuestObjectiveType<T> type) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        if (TYPES.putIfAbsent(id, type) != null) {
            throw new IllegalStateException("Stardew quest objective type already registered: " + id);
        }
    }

    public static <T> void register(
            ResourceLocation id,
            Codec<T> codec,
            StardewQuestObjectiveFactory<T> factory
    ) {
        register(id, new StardewQuestObjectiveType<>(codec, factory));
    }

    public static synchronized Set<ResourceLocation> registeredIds() {
        return Set.copyOf(TYPES.keySet());
    }

    public static DataResult<StardewQuestObjective> decode(ResourceLocation type, JsonElement data) {
        StardewQuestObjectiveType<?> registered = TYPES.get(type);
        if (registered == null) {
            return DataResult.error(() -> "Unknown Stardew quest objective type: " + type);
        }
        return decodeTyped(type, registered, data);
    }

    public static DataResult<QuestObjectiveRuntime> createRuntime(StardewQuestObjective objective) {
        StardewQuestObjectiveType<?> registered = TYPES.get(objective.type());
        if (registered == null) {
            return DataResult.error(() -> "Quest objective type is no longer registered: " + objective.type());
        }
        try {
            QuestObjectiveRuntime runtime = createTyped(registered, objective.data());
            return runtime == null
                    ? DataResult.error(() -> "Quest objective factory returned null: " + objective.type())
                    : DataResult.success(runtime);
        } catch (RuntimeException exception) {
            return DataResult.error(() -> "Quest objective factory failed for " + objective.type()
                    + ": " + exception.getMessage());
        }
    }

    private static DataResult<StardewQuestObjective> decodeDynamic(Dynamic<?> dynamic) {
        Object converted = dynamic.convert(JsonOps.INSTANCE).getValue();
        if (!(converted instanceof JsonElement element) || !element.isJsonObject()) {
            return DataResult.error(() -> "Stardew quest objective must be a JSON object");
        }
        JsonObject root = element.getAsJsonObject();
        if (!root.has("type") || !root.get("type").isJsonPrimitive()) {
            return DataResult.error(() -> "Stardew quest objective is missing string field 'type'");
        }
        String rawType = root.get("type").getAsString();
        if (rawType.indexOf(':') < 1) {
            return DataResult.error(() -> "Stardew quest objective type must be namespaced: " + rawType);
        }
        ResourceLocation type = ResourceLocation.tryParse(rawType);
        if (type == null) {
            return DataResult.error(() -> "Invalid Stardew quest objective type ID: " + rawType);
        }
        return decode(type, root.has("data") ? root.get("data") : new JsonObject());
    }

    private static DataResult<Dynamic<?>> encodeDynamic(StardewQuestObjective objective) {
        StardewQuestObjectiveType<?> registered = TYPES.get(objective.type());
        if (registered == null) {
            return DataResult.error(() -> "Quest objective type is no longer registered: " + objective.type());
        }
        return encodeTyped(registered, objective.data()).map(data -> {
            JsonObject root = new JsonObject();
            root.addProperty("type", objective.type().toString());
            root.add("data", data);
            return new Dynamic<>(JsonOps.INSTANCE, root);
        });
    }

    private static <T> DataResult<StardewQuestObjective> decodeTyped(
            ResourceLocation id,
            StardewQuestObjectiveType<T> type,
            JsonElement data
    ) {
        return type.codec().parse(JsonOps.INSTANCE, data).map(value -> new StardewQuestObjective(id, value));
    }

    @SuppressWarnings("unchecked")
    private static <T> DataResult<JsonElement> encodeTyped(StardewQuestObjectiveType<T> type, Object data) {
        return type.codec().encodeStart(JsonOps.INSTANCE, (T) data);
    }

    @SuppressWarnings("unchecked")
    private static <T> QuestObjectiveRuntime createTyped(StardewQuestObjectiveType<T> type, Object data) {
        return type.factory().create((T) data);
    }
}
