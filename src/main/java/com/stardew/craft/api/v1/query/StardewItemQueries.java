package com.stardew.craft.api.v1.query;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Public registry and codec for item selection used by content definitions. */
public final class StardewItemQueries {
    private static final Map<ResourceLocation, StardewItemQueryType<?>> TYPES = new LinkedHashMap<>();

    public static final Codec<StardewItemQuery> CODEC = Codec.PASSTHROUGH.flatXmap(
            StardewItemQueries::decodeDynamic,
            StardewItemQueries::encodeDynamic
    );

    private StardewItemQueries() {
    }

    public static synchronized <T> void register(ResourceLocation id, StardewItemQueryType<T> type) {
        requireNamespaced(id);
        Objects.requireNonNull(type, "type");
        if (TYPES.putIfAbsent(id, type) != null) {
            throw new IllegalStateException("Stardew item-query type already registered: " + id);
        }
    }

    public static <T> void register(
            ResourceLocation id,
            Codec<T> codec,
            StardewItemQueryResolver<T> resolver
    ) {
        register(id, new StardewItemQueryType<>(codec, resolver));
    }

    public static synchronized Set<ResourceLocation> registeredIds() {
        return Set.copyOf(TYPES.keySet());
    }

    public static DataResult<StardewItemQuery> decode(ResourceLocation type, JsonElement data) {
        StardewItemQueryType<?> registered = TYPES.get(type);
        if (registered == null) {
            return DataResult.error(() -> "Unknown Stardew item-query type: " + type);
        }
        return decodeTyped(type, registered, data);
    }

    public static DataResult<List<ItemStack>> resolve(
            StardewItemQuery query,
            StardewItemQueryContext context
    ) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(context, "context");
        StardewItemQueryType<?> registered = TYPES.get(query.type());
        if (registered == null) {
            return DataResult.error(() -> "Item-query type is no longer registered: " + query.type());
        }
        try {
            List<ItemStack> result = resolveTyped(registered, context, query.data());
            if (result == null) {
                return DataResult.error(() -> "Item query " + query.type() + " returned null");
            }
            return DataResult.success(result.stream()
                    .filter(Objects::nonNull)
                    .filter(stack -> !stack.isEmpty())
                    .map(ItemStack::copy)
                    .toList());
        } catch (RuntimeException exception) {
            return DataResult.error(() -> "Item query " + query.type() + " failed: " + exception.getMessage());
        }
    }

    private static DataResult<StardewItemQuery> decodeDynamic(Dynamic<?> dynamic) {
        Object converted = dynamic.convert(JsonOps.INSTANCE).getValue();
        if (!(converted instanceof JsonElement element) || !element.isJsonObject()) {
            return DataResult.error(() -> "Stardew item query must be a JSON object");
        }
        JsonObject root = element.getAsJsonObject();
        if (!root.has("type") || !root.get("type").isJsonPrimitive()) {
            return DataResult.error(() -> "Stardew item query is missing string field 'type'");
        }
        String rawType = root.get("type").getAsString();
        if (rawType.indexOf(':') < 1) {
            return DataResult.error(() -> "Stardew item-query type must be namespaced: " + rawType);
        }
        ResourceLocation type = ResourceLocation.tryParse(rawType);
        if (type == null) {
            return DataResult.error(() -> "Invalid Stardew item-query type ID: " + rawType);
        }
        JsonElement data = root.has("data") ? root.get("data") : new JsonObject();
        return decode(type, data);
    }

    private static DataResult<Dynamic<?>> encodeDynamic(StardewItemQuery query) {
        StardewItemQueryType<?> registered = TYPES.get(query.type());
        if (registered == null) {
            return DataResult.error(() -> "Item-query type is no longer registered: " + query.type());
        }
        return encodeTyped(registered, query.data()).map(data -> {
            JsonObject root = new JsonObject();
            root.addProperty("type", query.type().toString());
            root.add("data", data);
            return new Dynamic<>(JsonOps.INSTANCE, root);
        });
    }

    private static <T> DataResult<StardewItemQuery> decodeTyped(
            ResourceLocation id,
            StardewItemQueryType<T> type,
            JsonElement data
    ) {
        return type.codec().parse(JsonOps.INSTANCE, data).map(value -> new StardewItemQuery(id, value));
    }

    @SuppressWarnings("unchecked")
    private static <T> DataResult<JsonElement> encodeTyped(StardewItemQueryType<T> type, Object data) {
        return type.codec().encodeStart(JsonOps.INSTANCE, (T) data);
    }

    @SuppressWarnings("unchecked")
    private static <T> List<ItemStack> resolveTyped(
            StardewItemQueryType<T> type,
            StardewItemQueryContext context,
            Object data
    ) {
        return type.resolver().resolve(context, (T) data);
    }

    private static void requireNamespaced(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        if (id.getNamespace().isBlank()) {
            throw new IllegalArgumentException("Item-query type ID must be namespaced");
        }
    }
}
