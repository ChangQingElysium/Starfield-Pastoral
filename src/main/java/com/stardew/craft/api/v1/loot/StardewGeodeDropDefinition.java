package com.stardew.craft.api.v1.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.api.v1.condition.StardewCondition;
import com.stardew.craft.api.v1.condition.StardewConditions;
import com.stardew.craft.api.v1.query.StardewItemQueries;
import com.stardew.craft.api.v1.query.StardewItemQuery;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** A custom geode identity and its weighted server-side Item Query results. */
public record StardewGeodeDropDefinition(
        List<ResourceLocation> inputs,
        List<StardewCondition> availableWhen,
        List<Entry> entries
) {
    public static final Codec<StardewGeodeDropDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.listOf().fieldOf("inputs").forGetter(StardewGeodeDropDefinition::inputs),
            StardewConditions.CODEC.listOf().optionalFieldOf("available_when", List.of())
                    .forGetter(StardewGeodeDropDefinition::availableWhen),
            Entry.CODEC.listOf().fieldOf("entries").forGetter(StardewGeodeDropDefinition::entries)
    ).apply(instance, StardewGeodeDropDefinition::new));

    public StardewGeodeDropDefinition {
        inputs = List.copyOf(inputs == null ? List.of() : inputs);
        availableWhen = List.copyOf(availableWhen == null ? List.of() : availableWhen);
        entries = List.copyOf(entries == null ? List.of() : entries);
        if (inputs.isEmpty()) throw new IllegalArgumentException("geode definition needs inputs");
        if (entries.isEmpty()) throw new IllegalArgumentException("geode definition needs entries");
    }

    public record Entry(StardewItemQuery query, int weight) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                StardewItemQueries.CODEC.fieldOf("query").forGetter(Entry::query),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("weight", 1).forGetter(Entry::weight)
        ).apply(instance, Entry::new));
    }
}
