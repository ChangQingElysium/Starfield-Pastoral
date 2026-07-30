package com.stardew.craft.api.v1.mapinteraction;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;

/** Public typed action registry for data-pack map interactions. */
public final class StardewMapInteractionActions {
    private static volatile Map<ResourceLocation,
            StardewMapInteractionActionType<?>> types = Map.of();

    private StardewMapInteractionActions() {
    }

    public static synchronized <T> void register(
            ResourceLocation id,
            Codec<T> codec,
            StardewMapInteractionActionExecutor<T> executor
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(executor, "executor");
        if (types.containsKey(id)) {
            throw new IllegalStateException(
                    "Map interaction action type already registered: " + id);
        }
        LinkedHashMap<ResourceLocation,
                StardewMapInteractionActionType<?>> next =
                new LinkedHashMap<>(types);
        next.put(id,
                new StardewMapInteractionActionType<>(codec, executor));
        types = Map.copyOf(next);
    }

    public static Set<ResourceLocation> registeredIds() {
        return Set.copyOf(types.keySet());
    }

    public static DataResult<StardewMapInteractionAction> decode(
            ResourceLocation type,
            JsonElement data
    ) {
        StardewMapInteractionActionType<?> registered = types.get(type);
        if (registered == null) {
            return DataResult.error(() ->
                    "Unknown map interaction action type: " + type);
        }
        return decodeTyped(type, registered, data);
    }

    public static DataResult<InteractionResult> execute(
            StardewMapInteractionAction action,
            StardewMapInteractionContext context
    ) {
        StardewMapInteractionActionType<?> registered =
                types.get(action.type());
        if (registered == null) {
            return DataResult.error(() ->
                    "Map interaction action type is no longer registered: "
                            + action.type());
        }
        try {
            InteractionResult result =
                    executeTyped(registered, context, action.data());
            return result == null
                    ? DataResult.error(() ->
                            "Map interaction action returned null: "
                                    + action.type())
                    : DataResult.success(result);
        } catch (RuntimeException exception) {
            return DataResult.error(() ->
                    "Map interaction action " + action.type()
                            + " failed: " + exception.getMessage());
        }
    }

    private static <T> DataResult<StardewMapInteractionAction>
    decodeTyped(
            ResourceLocation id,
            StardewMapInteractionActionType<T> type,
            JsonElement data
    ) {
        return type.codec().parse(JsonOps.INSTANCE, data)
                .map(value -> new StardewMapInteractionAction(id, value));
    }

    @SuppressWarnings("unchecked")
    private static <T> InteractionResult executeTyped(
            StardewMapInteractionActionType<T> type,
            StardewMapInteractionContext context,
            Object data
    ) {
        return type.executor().execute(context, (T) data);
    }
}
