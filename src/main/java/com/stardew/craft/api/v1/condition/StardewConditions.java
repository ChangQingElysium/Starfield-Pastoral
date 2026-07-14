package com.stardew.craft.api.v1.condition;

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

/** Public registry and codec for server-authoritative content conditions. */
public final class StardewConditions {
    private static final Map<ResourceLocation, StardewConditionType<?>> TYPES = new LinkedHashMap<>();

    public static final Codec<StardewCondition> CODEC = Codec.PASSTHROUGH.flatXmap(
            StardewConditions::decodeDynamic,
            StardewConditions::encodeDynamic
    );

    private StardewConditions() {
    }

    public static synchronized <T> void register(ResourceLocation id, StardewConditionType<T> type) {
        requireNamespaced(id);
        Objects.requireNonNull(type, "type");
        if (TYPES.putIfAbsent(id, type) != null) {
            throw new IllegalStateException("Stardew condition type already registered: " + id);
        }
    }

    public static <T> void register(
            ResourceLocation id,
            Codec<T> codec,
            StardewConditionEvaluator<T> evaluator
    ) {
        register(id, new StardewConditionType<>(codec, evaluator));
    }

    public static synchronized Set<ResourceLocation> registeredIds() {
        return Set.copyOf(TYPES.keySet());
    }

    public static DataResult<StardewCondition> decode(ResourceLocation type, JsonElement data) {
        StardewConditionType<?> registered = TYPES.get(type);
        if (registered == null) {
            return DataResult.error(() -> "Unknown Stardew condition type: " + type);
        }
        return decodeTyped(type, registered, data);
    }

    public static DataResult<Boolean> test(StardewCondition condition, StardewConditionContext context) {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(context, "context");
        StardewConditionType<?> registered = TYPES.get(condition.type());
        if (registered == null) {
            return DataResult.error(() -> "Condition type is no longer registered: " + condition.type());
        }
        try {
            return DataResult.success(testTyped(registered, context, condition.data()));
        } catch (RuntimeException exception) {
            return DataResult.error(() -> "Condition " + condition.type() + " failed: " + exception.getMessage());
        }
    }

    /** Decodes old flat event JSON after its legacy type has been mapped to a namespaced ID. */
    public static DataResult<StardewCondition> decodeLegacy(String legacyType, JsonObject raw) {
        Objects.requireNonNull(legacyType, "legacyType");
        Objects.requireNonNull(raw, "raw");
        ResourceLocation id = legacyType.indexOf(':') >= 0
                ? ResourceLocation.tryParse(legacyType)
                : ResourceLocation.fromNamespaceAndPath("stardewcraft", legacyType);
        if (id == null) {
            return DataResult.error(() -> "Invalid Stardew condition type ID: " + legacyType);
        }
        JsonObject data = raw.deepCopy();
        data.remove("type");
        return decode(id, data);
    }

    private static DataResult<StardewCondition> decodeDynamic(Dynamic<?> dynamic) {
        Object converted = dynamic.convert(JsonOps.INSTANCE).getValue();
        if (!(converted instanceof JsonElement element) || !element.isJsonObject()) {
            return DataResult.error(() -> "Stardew condition must be a JSON object");
        }
        JsonObject root = element.getAsJsonObject();
        if (!root.has("type") || !root.get("type").isJsonPrimitive()) {
            return DataResult.error(() -> "Stardew condition is missing string field 'type'");
        }
        String rawType = root.get("type").getAsString();
        if (rawType.indexOf(':') < 1) {
            return DataResult.error(() -> "Stardew condition type must be namespaced: " + rawType);
        }
        ResourceLocation type = ResourceLocation.tryParse(rawType);
        if (type == null) {
            return DataResult.error(() -> "Invalid Stardew condition type ID: " + rawType);
        }
        JsonElement data = root.has("data") ? root.get("data") : new JsonObject();
        return decode(type, data);
    }

    private static DataResult<Dynamic<?>> encodeDynamic(StardewCondition condition) {
        StardewConditionType<?> registered = TYPES.get(condition.type());
        if (registered == null) {
            return DataResult.error(() -> "Condition type is no longer registered: " + condition.type());
        }
        return encodeTyped(registered, condition.data()).map(data -> {
            JsonObject root = new JsonObject();
            root.addProperty("type", condition.type().toString());
            root.add("data", data);
            return new Dynamic<>(JsonOps.INSTANCE, root);
        });
    }

    private static <T> DataResult<StardewCondition> decodeTyped(
            ResourceLocation id,
            StardewConditionType<T> type,
            JsonElement data
    ) {
        return type.codec().parse(JsonOps.INSTANCE, data).map(value -> new StardewCondition(id, value));
    }

    @SuppressWarnings("unchecked")
    private static <T> DataResult<JsonElement> encodeTyped(StardewConditionType<T> type, Object data) {
        return type.codec().encodeStart(JsonOps.INSTANCE, (T) data);
    }

    @SuppressWarnings("unchecked")
    private static <T> boolean testTyped(
            StardewConditionType<T> type,
            StardewConditionContext context,
            Object data
    ) {
        return type.evaluator().test(context, (T) data);
    }

    private static void requireNamespaced(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        if (id.getNamespace().isBlank()) {
            throw new IllegalArgumentException("Condition type ID must be namespaced");
        }
    }
}
