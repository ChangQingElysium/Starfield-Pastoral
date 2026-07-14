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

/** Public registry for add-on special-order reward payloads. */
public final class StardewSpecialOrderRewards {
    private static final Map<ResourceLocation, Type<?>> TYPES = new LinkedHashMap<>();

    private StardewSpecialOrderRewards() {
    }

    public static synchronized <T> void register(
            ResourceLocation id, Codec<T> codec, SpecialOrderRewardExecutor<T> executor) {
        Objects.requireNonNull(id, "id");
        if (TYPES.putIfAbsent(id, new Type<>(codec, executor)) != null) {
            throw new IllegalStateException("Special-order reward already registered: " + id);
        }
    }

    public static synchronized Set<ResourceLocation> registeredIds() { return Set.copyOf(TYPES.keySet()); }

    public static DataResult<StardewSpecialOrderReward> decode(ResourceLocation id, JsonElement data) {
        Type<?> type = TYPES.get(id);
        if (type == null) return DataResult.error(() -> "Unknown special-order reward type: " + id);
        return decodeTyped(id, type, data);
    }

    public static DataResult<Boolean> grant(StardewSpecialOrderReward reward, SpecialOrderRewardContext context) {
        Type<?> type = TYPES.get(reward.type());
        if (type == null) return DataResult.error(() -> "Reward type is no longer registered: " + reward.type());
        try {
            grantTyped(type, context, reward.data());
            return DataResult.success(true);
        } catch (RuntimeException exception) {
            return DataResult.error(() -> "Reward " + reward.type() + " failed: " + exception.getMessage());
        }
    }

    private static <T> DataResult<StardewSpecialOrderReward> decodeTyped(
            ResourceLocation id, Type<T> type, JsonElement data) {
        return type.codec.parse(JsonOps.INSTANCE, data).map(value -> new StardewSpecialOrderReward(id, value));
    }

    @SuppressWarnings("unchecked")
    private static <T> void grantTyped(Type<T> type, SpecialOrderRewardContext context, Object data) {
        type.executor.grant(context, (T) data);
    }

    private record Type<T>(Codec<T> codec, SpecialOrderRewardExecutor<T> executor) {
        private Type {
            Objects.requireNonNull(codec, "codec");
            Objects.requireNonNull(executor, "executor");
        }
    }
}
