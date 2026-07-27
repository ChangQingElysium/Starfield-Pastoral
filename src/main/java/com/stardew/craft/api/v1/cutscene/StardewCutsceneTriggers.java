package com.stardew.craft.api.v1.cutscene;

import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.api.v1.content.StardewContentKey;
import com.stardew.craft.api.v1.content.StardewContentReference;
import com.stardew.craft.api.v1.content.StardewTypedContentReferenceProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Public registry for server-authoritative custom cutscene trigger types. */
public final class StardewCutsceneTriggers {
    private static final Map<ResourceLocation, StardewCutsceneTriggerType<?>> TYPES = new LinkedHashMap<>();
    private static final Map<ResourceLocation,
            StardewTypedContentReferenceProvider<?>>
            REFERENCE_PROVIDERS = new LinkedHashMap<>();

    private StardewCutsceneTriggers() {
    }

    public static synchronized <T> void register(
            ResourceLocation id,
            StardewCutsceneTriggerType<T> type
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        if (TYPES.putIfAbsent(id, type) != null) {
            throw new IllegalStateException("Cutscene trigger type already registered: " + id);
        }
    }

    public static <T> void register(
            ResourceLocation id,
            com.mojang.serialization.Codec<T> codec,
            StardewCutsceneTriggerEvaluator<T> evaluator
    ) {
        register(id, new StardewCutsceneTriggerType<>(codec, evaluator));
    }

    public static synchronized <T> void register(
            ResourceLocation id,
            com.mojang.serialization.Codec<T> codec,
            StardewCutsceneTriggerEvaluator<T> evaluator,
            StardewTypedContentReferenceProvider<T> references
    ) {
        Objects.requireNonNull(references, "references");
        register(id, new StardewCutsceneTriggerType<>(
                codec, evaluator));
        REFERENCE_PROVIDERS.put(id, references);
    }

    public static synchronized Set<ResourceLocation> registeredIds() {
        return Set.copyOf(TYPES.keySet());
    }

    public static DataResult<Boolean> validate(ResourceLocation id, JsonObject data) {
        StardewCutsceneTriggerType<?> type = TYPES.get(id);
        if (type == null) return DataResult.error(() -> "Unknown cutscene trigger type: " + id);
        return validateTyped(type, data);
    }

    public static DataResult<Boolean> test(ResourceLocation id, JsonObject data, ServerPlayer player) {
        StardewCutsceneTriggerType<?> type = TYPES.get(id);
        if (type == null) return DataResult.error(() -> "Unknown cutscene trigger type: " + id);
        return testTyped(type, data, player);
    }

    public static DataResult<List<StardewContentReference>>
    contentReferences(
            StardewContentKey owner,
            ResourceLocation id,
            JsonObject data
    ) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(data, "data");
        StardewCutsceneTriggerType<?> type = TYPES.get(id);
        if (type == null) {
            return DataResult.error(() ->
                    "Unknown cutscene trigger type: " + id);
        }
        StardewTypedContentReferenceProvider<?> provider =
                REFERENCE_PROVIDERS.get(id);
        if (provider == null) {
            return DataResult.success(List.of());
        }
        return referencesTyped(
                owner, id, type, provider, data);
    }

    private static <T> DataResult<Boolean> validateTyped(StardewCutsceneTriggerType<T> type, JsonObject data) {
        return type.codec().parse(JsonOps.INSTANCE, payload(data)).map(ignored -> true);
    }

    private static <T> DataResult<Boolean> testTyped(
            StardewCutsceneTriggerType<T> type,
            JsonObject data,
            ServerPlayer player
    ) {
        return type.codec().parse(JsonOps.INSTANCE, payload(data)).flatMap(decoded -> {
            try {
                return DataResult.success(type.evaluator().test(player, decoded));
            } catch (RuntimeException exception) {
                return DataResult.error(() -> "Cutscene trigger failed: " + exception.getMessage());
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> DataResult<List<StardewContentReference>>
    referencesTyped(
            StardewContentKey owner,
            ResourceLocation id,
            StardewCutsceneTriggerType<?> rawType,
            StardewTypedContentReferenceProvider<?> rawProvider,
            JsonObject data
    ) {
        StardewCutsceneTriggerType<T> type =
                (StardewCutsceneTriggerType<T>) rawType;
        StardewTypedContentReferenceProvider<T> provider =
                (StardewTypedContentReferenceProvider<T>) rawProvider;
        return type.codec().parse(JsonOps.INSTANCE, payload(data))
                .flatMap(decoded -> {
                    try {
                        return DataResult.success(List.copyOf(
                                Objects.requireNonNull(
                                        provider.references(
                                                owner, decoded),
                                        "cutscene trigger reference "
                                                + "provider result")));
                    } catch (RuntimeException exception) {
                        return DataResult.error(() ->
                                "Cutscene trigger reference provider "
                                        + id + " failed: "
                                        + exception.getMessage());
                    }
                });
    }

    private static com.google.gson.JsonElement payload(JsonObject data) {
        return data.has("data") ? data.get("data") : data;
    }
}
