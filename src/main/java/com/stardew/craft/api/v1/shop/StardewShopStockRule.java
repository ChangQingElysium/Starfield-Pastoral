package com.stardew.craft.api.v1.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.api.v1.condition.StardewCondition;
import com.stardew.craft.api.v1.condition.StardewConditions;

import java.util.List;
import java.util.Locale;

/** Datapack rule controlling ownership and reset cadence for one limited shop row. */
public record StardewShopStockRule(
        String shop,
        String item,
        int priority,
        Scope scope,
        Reset reset,
        List<StardewCondition> availableWhen
) {
    public static final Codec<StardewShopStockRule> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("shop")
                            .forGetter(StardewShopStockRule::shop),
                    Codec.STRING.fieldOf("item")
                            .forGetter(StardewShopStockRule::item),
                    Codec.INT.optionalFieldOf("priority", 0)
                            .forGetter(StardewShopStockRule::priority),
                    Scope.CODEC.optionalFieldOf(
                                    "scope", Scope.PLAYER)
                            .forGetter(StardewShopStockRule::scope),
                    Reset.CODEC.optionalFieldOf(
                                    "reset", Reset.DAY)
                            .forGetter(StardewShopStockRule::reset),
                    StardewConditions.CODEC.listOf()
                            .optionalFieldOf(
                                    "available_when", List.of())
                            .forGetter(
                                    StardewShopStockRule::availableWhen)
            ).apply(instance, StardewShopStockRule::new));

    public StardewShopStockRule {
        if (shop.isBlank()) {
            throw new IllegalArgumentException(
                    "shop must not be blank");
        }
        if (item.isBlank()) {
            throw new IllegalArgumentException(
                    "item must not be blank");
        }
        availableWhen = List.copyOf(availableWhen);
    }

    public enum Scope {
        PLAYER,
        WORLD;

        public static final Codec<Scope> CODEC =
                enumCodec(Scope.class, "stock scope");
    }

    public enum Reset {
        DAY,
        WEEK,
        SEASON,
        YEAR,
        NEVER;

        public static final Codec<Reset> CODEC =
                enumCodec(Reset.class, "stock reset");
    }

    private static <E extends Enum<E>> Codec<E> enumCodec(
            Class<E> type,
            String label
    ) {
        return Codec.STRING.comapFlatMap(value -> {
            try {
                return DataResult.success(Enum.valueOf(
                        type, value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                return DataResult.error(() ->
                        "Unknown " + label + ": " + value);
            }
        }, value -> value.name().toLowerCase(Locale.ROOT));
    }
}
