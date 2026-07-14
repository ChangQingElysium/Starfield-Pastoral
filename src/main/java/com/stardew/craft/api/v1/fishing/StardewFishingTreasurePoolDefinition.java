package com.stardew.craft.api.v1.fishing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.api.v1.condition.StardewCondition;
import com.stardew.craft.api.v1.condition.StardewConditions;

import java.util.List;

/** Server-authoritative additive loot applied after Stardew's built-in treasure algorithm. */
public record StardewFishingTreasurePoolDefinition(
        String chest,
        float chance,
        int rolls,
        List<StardewCondition> availableWhen,
        List<StardewFishingTreasureEntry> entries
) {
    private static final Codec<String> CHEST_CODEC = Codec.STRING.validate(value -> switch (value) {
        case "any", "normal", "golden" -> DataResult.success(value);
        default -> DataResult.error(() -> "chest must be any, normal, or golden");
    });

    public static final Codec<StardewFishingTreasurePoolDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CHEST_CODEC.optionalFieldOf("chest", "any")
                    .forGetter(StardewFishingTreasurePoolDefinition::chest),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("chance", 1.0F)
                    .forGetter(StardewFishingTreasurePoolDefinition::chance),
            Codec.intRange(1, 64).optionalFieldOf("rolls", 1)
                    .forGetter(StardewFishingTreasurePoolDefinition::rolls),
            StardewConditions.CODEC.listOf().optionalFieldOf("available_when", List.of())
                    .forGetter(StardewFishingTreasurePoolDefinition::availableWhen),
            StardewFishingTreasureEntry.CODEC.listOf().fieldOf("entries")
                    .forGetter(StardewFishingTreasurePoolDefinition::entries)
    ).apply(instance, StardewFishingTreasurePoolDefinition::new));

    public StardewFishingTreasurePoolDefinition {
        availableWhen = List.copyOf(availableWhen == null ? List.of() : availableWhen);
        entries = List.copyOf(entries == null ? List.of() : entries);
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("fishing treasure pool needs at least one entry");
        }
    }

    public boolean accepts(boolean golden) {
        return "any".equals(chest) || (golden ? "golden".equals(chest) : "normal".equals(chest));
    }
}
