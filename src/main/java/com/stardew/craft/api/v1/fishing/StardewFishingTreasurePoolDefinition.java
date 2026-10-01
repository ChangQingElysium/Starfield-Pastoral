package com.stardew.craft.api.v1.fishing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.api.v1.condition.StardewCondition;
import com.stardew.craft.api.v1.condition.StardewConditions;

import java.util.List;

/** Server-authoritative additions or replacements for the data-defined base treasure query. */
public record StardewFishingTreasurePoolDefinition(
        String chest,
        float chance,
        int rolls,
        List<StardewCondition> availableWhen,
        List<StardewFishingTreasureEntry> entries,
        boolean replaceBase
) {
    private static final Codec<String> CHEST_CODEC = com.stardew.craft.port.PortCodecs.validate(Codec.STRING, value -> switch (value) {
        case "any", "normal", "golden" -> DataResult.success(value);
        default -> DataResult.error(() -> "chest must be any, normal, or golden");
    });

    public static final Codec<StardewFishingTreasurePoolDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            com.stardew.craft.port.PortCodecs.optionalFieldOf(CHEST_CODEC, "chest", "any")
                    .forGetter(StardewFishingTreasurePoolDefinition::chest),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.floatRange(0.0F, 1.0F), "chance", 1.0F)
                    .forGetter(StardewFishingTreasurePoolDefinition::chance),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.intRange(1, 64), "rolls", 1)
                    .forGetter(StardewFishingTreasurePoolDefinition::rolls),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(StardewConditions.CODEC.listOf(), "available_when", List.of())
                    .forGetter(StardewFishingTreasurePoolDefinition::availableWhen),
            StardewFishingTreasureEntry.CODEC.listOf().fieldOf("entries")
                    .forGetter(StardewFishingTreasurePoolDefinition::entries),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.BOOL, "replace_base", false).forGetter(StardewFishingTreasurePoolDefinition::replaceBase)
    ).apply(instance, StardewFishingTreasurePoolDefinition::new));

    public StardewFishingTreasurePoolDefinition {
        availableWhen = List.copyOf(availableWhen == null ? List.of() : availableWhen);
        entries = List.copyOf(entries == null ? List.of() : entries);
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("fishing treasure pool needs at least one entry");
        }
    }

    public StardewFishingTreasurePoolDefinition(String chest, float chance, int rolls,
            List<StardewCondition> availableWhen, List<StardewFishingTreasureEntry> entries) {
        this(chest, chance, rolls, availableWhen, entries, false);
    }

    public boolean accepts(boolean golden) {
        return "any".equals(chest) || (golden ? "golden".equals(chest) : "normal".equals(chest));
    }
}
