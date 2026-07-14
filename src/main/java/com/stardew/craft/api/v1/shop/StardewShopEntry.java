package com.stardew.craft.api.v1.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.api.v1.condition.StardewCondition;
import com.stardew.craft.api.v1.condition.StardewConditions;

import java.util.List;
import java.util.Optional;

/** One purchasable shop row. Item IDs may also use built-in pseudo IDs such as recipe:. */
public record StardewShopEntry(
        String item,
        String displayName,
        String description,
        int price,
        int stock,
        Optional<String> tradeItem,
        int tradeItemCount,
        List<Integer> seasons,
        int minYear,
        int minMineLevel,
        Optional<String> mailFlag,
        int dayOfWeek,
        int dayOfMonthParity,
        int purchaseStack,
        List<StardewCondition> availableWhen
) {
    public static final Codec<StardewShopEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("item").forGetter(StardewShopEntry::item),
            Codec.STRING.optionalFieldOf("display_name", "").forGetter(StardewShopEntry::displayName),
            Codec.STRING.optionalFieldOf("description", "").forGetter(StardewShopEntry::description),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("price", 0).forGetter(StardewShopEntry::price),
            Codec.INT.optionalFieldOf("stock", Integer.MAX_VALUE).forGetter(StardewShopEntry::stock),
            Codec.STRING.optionalFieldOf("trade_item").forGetter(StardewShopEntry::tradeItem),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("trade_item_count", 0)
                    .forGetter(StardewShopEntry::tradeItemCount),
            Codec.intRange(0, 3).listOf().optionalFieldOf("seasons", List.of()).forGetter(StardewShopEntry::seasons),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("min_year", 1).forGetter(StardewShopEntry::minYear),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("min_mine_level", 0)
                    .forGetter(StardewShopEntry::minMineLevel),
            Codec.STRING.optionalFieldOf("mail_flag").forGetter(StardewShopEntry::mailFlag),
            Codec.intRange(-1, 6).optionalFieldOf("day_of_week", -1).forGetter(StardewShopEntry::dayOfWeek),
            Codec.intRange(0, 2).optionalFieldOf("day_of_month_parity", 0)
                    .forGetter(StardewShopEntry::dayOfMonthParity),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("purchase_stack", 1)
                    .forGetter(StardewShopEntry::purchaseStack),
            StardewConditions.CODEC.listOf().optionalFieldOf("available_when", List.of())
                    .forGetter(StardewShopEntry::availableWhen)
    ).apply(instance, StardewShopEntry::new));

    public StardewShopEntry {
        seasons = List.copyOf(seasons);
        availableWhen = List.copyOf(availableWhen);
        if (stock < 0 && stock != Integer.MAX_VALUE) {
            throw new IllegalArgumentException("stock must be non-negative");
        }
    }
}
