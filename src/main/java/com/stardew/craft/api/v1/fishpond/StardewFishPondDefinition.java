package com.stardew.craft.api.v1.fishpond;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/** A reloadable fish-specific pond rule. File path supplies the definition ID. */
public record StardewFishPondDefinition(
        ResourceLocation fish,
        int maxPopulation,
        int spawnTime,
        float baseMinProduceChance,
        float baseMaxProduceChance,
        List<ProducedItem> producedItems,
        Map<String, List<GateItem>> populationGates
) {
    public static final Codec<StardewFishPondDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("fish").forGetter(StardewFishPondDefinition::fish),
            Codec.intRange(-1, 100).optionalFieldOf("max_population", -1)
                    .forGetter(StardewFishPondDefinition::maxPopulation),
            Codec.intRange(-1, 1000).optionalFieldOf("spawn_time", -1)
                    .forGetter(StardewFishPondDefinition::spawnTime),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("base_min_produce_chance", 0.15F)
                    .forGetter(StardewFishPondDefinition::baseMinProduceChance),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("base_max_produce_chance", 0.95F)
                    .forGetter(StardewFishPondDefinition::baseMaxProduceChance),
            ProducedItem.CODEC.listOf().fieldOf("produced_items")
                    .forGetter(StardewFishPondDefinition::producedItems),
            Codec.unboundedMap(Codec.STRING, GateItem.CODEC.listOf())
                    .optionalFieldOf("population_gates", Map.of())
                    .forGetter(StardewFishPondDefinition::populationGates)
    ).apply(instance, StardewFishPondDefinition::new));

    public StardewFishPondDefinition {
        producedItems = List.copyOf(producedItems == null ? List.of() : producedItems);
        populationGates = Map.copyOf(populationGates == null ? Map.of() : populationGates);
        if (producedItems.isEmpty()) throw new IllegalArgumentException("fish pond needs produced_items");
        if (baseMaxProduceChance < baseMinProduceChance) {
            throw new IllegalArgumentException("base_max_produce_chance cannot be below base_min_produce_chance");
        }
    }

    public record ProducedItem(
            ResourceLocation item,
            int requiredPopulation,
            float chance,
            int precedence,
            int minCount,
            int maxCount
    ) {
        public static final Codec<ProducedItem> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(ProducedItem::item),
                Codec.intRange(0, 100).optionalFieldOf("required_population", 0)
                        .forGetter(ProducedItem::requiredPopulation),
                Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(ProducedItem::chance),
                Codec.INT.optionalFieldOf("precedence", 0).forGetter(ProducedItem::precedence),
                Codec.intRange(1, 999).optionalFieldOf("min_count", 1).forGetter(ProducedItem::minCount),
                Codec.intRange(1, 999).optionalFieldOf("max_count", 1).forGetter(ProducedItem::maxCount)
        ).apply(instance, ProducedItem::new));

        public ProducedItem {
            if (maxCount < minCount) {
                throw new IllegalArgumentException("produced item max_count cannot be below min_count");
            }
        }
    }

    public record GateItem(ResourceLocation item, int minCount, int maxCount, int weight) {
        public static final Codec<GateItem> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(GateItem::item),
                Codec.intRange(1, 999).optionalFieldOf("min_count", 1).forGetter(GateItem::minCount),
                Codec.intRange(1, 999).optionalFieldOf("max_count", 1).forGetter(GateItem::maxCount),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("weight", 1).forGetter(GateItem::weight)
        ).apply(instance, GateItem::new));

        public GateItem {
            if (maxCount < minCount) {
                throw new IllegalArgumentException("population gate max_count cannot be below min_count");
            }
        }
    }
}
