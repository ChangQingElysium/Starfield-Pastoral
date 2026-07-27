package com.stardew.craft.api.v1.action;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.api.v1.content.StardewContentKey;
import com.stardew.craft.api.v1.content.StardewContentReference;
import com.stardew.craft.api.v1.content.StardewTypedContentReferenceProvider;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.List;

/** Public registry and codec for server-authoritative content actions. */
public final class StardewActions {
    private static final Map<ResourceLocation, StardewActionType<?>> TYPES = new LinkedHashMap<>();
    private static final Map<ResourceLocation,
            StardewTypedContentReferenceProvider<?>>
            REFERENCE_PROVIDERS = new LinkedHashMap<>();

    public static final Codec<StardewAction> CODEC = Codec.PASSTHROUGH.flatXmap(
            StardewActions::decodeDynamic,
            StardewActions::encodeDynamic
    );

    private StardewActions() {
    }

    public static synchronized <T> void register(ResourceLocation id, StardewActionType<T> type) {
        requireNamespaced(id);
        Objects.requireNonNull(type, "type");
        if (TYPES.putIfAbsent(id, type) != null) {
            throw new IllegalStateException("Stardew action type already registered: " + id);
        }
    }

    public static <T> void register(
            ResourceLocation id,
            Codec<T> codec,
            StardewActionExecutor<T> executor
    ) {
        register(id, new StardewActionType<>(codec, executor));
    }

    public static synchronized <T> void register(
            ResourceLocation id,
            Codec<T> codec,
            StardewActionExecutor<T> executor,
            StardewTypedContentReferenceProvider<T> references
    ) {
        Objects.requireNonNull(references, "references");
        register(id, new StardewActionType<>(codec, executor));
        REFERENCE_PROVIDERS.put(id, references);
    }

    public static synchronized Set<ResourceLocation> registeredIds() {
        return Set.copyOf(TYPES.keySet());
    }

    public static DataResult<StardewAction> decode(ResourceLocation type, JsonElement data) {
        StardewActionType<?> registered = TYPES.get(type);
        if (registered == null) {
            return DataResult.error(() -> "Unknown Stardew action type: " + type);
        }
        return decodeTyped(type, registered, data);
    }

    public static DataResult<StardewActionResult> execute(
            StardewAction action,
            StardewActionContext context
    ) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(context, "context");
        StardewActionType<?> registered = TYPES.get(action.type());
        if (registered == null) {
            return DataResult.error(() -> "Action type is no longer registered: " + action.type());
        }
        try {
            StardewActionResult result = executeTyped(registered, context, action.data());
            if (result == null) {
                return DataResult.error(() -> "Action " + action.type() + " returned null");
            }
            return DataResult.success(result);
        } catch (RuntimeException exception) {
            return DataResult.error(() -> "Action " + action.type() + " failed: " + exception.getMessage());
        }
    }

    public static DataResult<List<StardewContentReference>>
    contentReferences(
            StardewContentKey owner,
            StardewAction action
    ) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(action, "action");
        StardewTypedContentReferenceProvider<?> provider =
                REFERENCE_PROVIDERS.get(action.type());
        if (provider == null) {
            return DataResult.success(List.of());
        }
        try {
            return DataResult.success(
                    extractReferences(
                            provider, owner, action.data()));
        } catch (RuntimeException exception) {
            return DataResult.error(() ->
                    "Action reference provider "
                            + action.type() + " failed: "
                            + exception.getMessage());
        }
    }

    private static DataResult<StardewAction> decodeDynamic(Dynamic<?> dynamic) {
        Object converted = dynamic.convert(JsonOps.INSTANCE).getValue();
        if (!(converted instanceof JsonElement element) || !element.isJsonObject()) {
            return DataResult.error(() -> "Stardew action must be a JSON object");
        }
        JsonObject root = element.getAsJsonObject();
        if (!root.has("type") || !root.get("type").isJsonPrimitive()) {
            return DataResult.error(() -> "Stardew action is missing string field 'type'");
        }
        String rawType = root.get("type").getAsString();
        if (rawType.indexOf(':') < 1) {
            return DataResult.error(() -> "Stardew action type must be namespaced: " + rawType);
        }
        ResourceLocation type = ResourceLocation.tryParse(rawType);
        if (type == null) {
            return DataResult.error(() -> "Invalid Stardew action type ID: " + rawType);
        }
        JsonElement data = root.has("data") ? root.get("data") : new JsonObject();
        return decode(type, data);
    }

    private static DataResult<Dynamic<?>> encodeDynamic(StardewAction action) {
        StardewActionType<?> registered = TYPES.get(action.type());
        if (registered == null) {
            return DataResult.error(() -> "Action type is no longer registered: " + action.type());
        }
        return encodeTyped(registered, action.data()).map(data -> {
            JsonObject root = new JsonObject();
            root.addProperty("type", action.type().toString());
            root.add("data", data);
            return new Dynamic<>(JsonOps.INSTANCE, root);
        });
    }

    private static <T> DataResult<StardewAction> decodeTyped(
            ResourceLocation id,
            StardewActionType<T> type,
            JsonElement data
    ) {
        return type.codec().parse(JsonOps.INSTANCE, data).map(value -> new StardewAction(id, value));
    }

    @SuppressWarnings("unchecked")
    private static <T> DataResult<JsonElement> encodeTyped(StardewActionType<T> type, Object data) {
        return type.codec().encodeStart(JsonOps.INSTANCE, (T) data);
    }

    @SuppressWarnings("unchecked")
    private static <T> StardewActionResult executeTyped(
            StardewActionType<T> type,
            StardewActionContext context,
            Object data
    ) {
        return type.executor().execute(context, (T) data);
    }

    @SuppressWarnings("unchecked")
    private static <T> List<StardewContentReference>
    extractReferences(
            StardewTypedContentReferenceProvider<T> provider,
            StardewContentKey owner,
            Object data
    ) {
        var references = provider.references(owner, (T) data);
        return List.copyOf(Objects.requireNonNull(
                references,
                "action reference provider result"));
    }

    private static void requireNamespaced(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        if (id.getNamespace().isBlank()) {
            throw new IllegalArgumentException("Action type ID must be namespaced");
        }
    }
}
