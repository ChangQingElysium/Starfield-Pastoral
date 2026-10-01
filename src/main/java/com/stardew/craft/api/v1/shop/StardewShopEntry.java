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
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.STRING, "display_name", "").forGetter(StardewShopEntry::displayName),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.STRING, "description", "").forGetter(StardewShopEntry::description),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "price", 0).forGetter(StardewShopEntry::price),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.INT, "stock", Integer.MAX_VALUE).forGetter(StardewShopEntry::stock),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.STRING, "trade_item").forGetter(StardewShopEntry::tradeItem),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "trade_item_count", 0)
                    .forGetter(StardewShopEntry::tradeItemCount),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.intRange(0, 3).listOf(), "seasons", List.of()).forGetter(StardewShopEntry::seasons),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "min_year", 1).forGetter(StardewShopEntry::minYear),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "min_mine_level", 0)
                    .forGetter(StardewShopEntry::minMineLevel),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.STRING, "mail_flag").forGetter(StardewShopEntry::mailFlag),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.intRange(-1, 6), "day_of_week", -1).forGetter(StardewShopEntry::dayOfWeek),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.intRange(0, 2), "day_of_month_parity", 0)
                    .forGetter(StardewShopEntry::dayOfMonthParity),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "purchase_stack", 1)
                    .forGetter(StardewShopEntry::purchaseStack),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(StardewConditions.CODEC.listOf(), "available_when", List.of())
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
