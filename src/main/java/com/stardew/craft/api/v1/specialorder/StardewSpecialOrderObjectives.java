package com.stardew.craft.api.v1.specialorder;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Public registry for add-on special-order objective payloads. */
public final class StardewSpecialOrderObjectives {
    private static final Map<ResourceLocation, Type<?>> TYPES = new LinkedHashMap<>();

    private StardewSpecialOrderObjectives() {
    }

    public static synchronized <T> void register(
            ResourceLocation id,
            Codec<T> codec,
            SpecialOrderObjectiveEvaluator<T> evaluator
    ) {
        Objects.requireNonNull(id, "id");
        if (TYPES.putIfAbsent(id, new Type<>(codec, evaluator)) != null) {
            throw new IllegalStateException("Special-order objective already registered: " + id);
        }
    }

    public static synchronized Set<ResourceLocation> registeredIds() { return Set.copyOf(TYPES.keySet()); }

    public static DataResult<StardewSpecialOrderObjective> decode(ResourceLocation id, JsonElement data) {
        Type<?> type = TYPES.get(id);
        if (type == null) return DataResult.error(() -> "Unknown special-order objective type: " + id);
        return decodeTyped(id, type, data);
    }

    public static DataResult<Integer> progress(
            StardewSpecialOrderObjective objective,
            SpecialOrderObjectiveContext context,
            SpecialOrderProgressEvent event
    ) {
        Type<?> type = TYPES.get(objective.type());
        if (type == null) return DataResult.error(() -> "Objective type is no longer registered: " + objective.type());
        try {
            return DataResult.success(Math.max(0, progressTyped(type, context, objective.data(), event)));
        } catch (RuntimeException exception) {
            return DataResult.error(() -> "Objective " + objective.type() + " failed: " + exception.getMessage());
        }
    }

    private static <T> DataResult<StardewSpecialOrderObjective> decodeTyped(
            ResourceLocation id, Type<T> type, JsonElement data) {
        return type.codec.parse(JsonOps.INSTANCE, data).map(value -> new StardewSpecialOrderObjective(id, value));
    }

    @SuppressWarnings("unchecked")
    private static <T> int progressTyped(
            Type<T> type, SpecialOrderObjectiveContext context, Object data, SpecialOrderProgressEvent event) {
        return type.evaluator.progress(context, (T) data, event);
    }

    private record Type<T>(Codec<T> codec, SpecialOrderObjectiveEvaluator<T> evaluator) {
        private Type {
            Objects.requireNonNull(codec, "codec");
            Objects.requireNonNull(evaluator, "evaluator");
        }
    }
}
