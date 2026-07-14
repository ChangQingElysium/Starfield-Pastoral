package com.stardew.craft.api.v1.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.api.v1.condition.StardewCondition;
import com.stardew.craft.api.v1.condition.StardewConditions;
import com.stardew.craft.api.v1.query.StardewItemQueries;
import com.stardew.craft.api.v1.query.StardewItemQuery;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** A namespaced item or placeable-block pool consumed by a world-content algorithm. */
public record StardewWorldLootPoolDefinition(
        ResourceLocation source,
        String group,
        Mode mode,
        int priority,
        List<StardewCondition> availableWhen,
        List<Entry> entries
) {
    public static final Codec<StardewWorldLootPoolDefinition> CODEC = RecordCodecBuilder.<StardewWorldLootPoolDefinition>create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("source").forGetter(StardewWorldLootPoolDefinition::source),
            Codec.STRING.optionalFieldOf("group", "default").forGetter(StardewWorldLootPoolDefinition::group),
            Mode.CODEC.optionalFieldOf("mode", Mode.WEIGHTED).forGetter(StardewWorldLootPoolDefinition::mode),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(StardewWorldLootPoolDefinition::priority),
            StardewConditions.CODEC.listOf().optionalFieldOf("available_when", List.of())
                    .forGetter(StardewWorldLootPoolDefinition::availableWhen),
            Entry.CODEC.listOf().fieldOf("entries").forGetter(StardewWorldLootPoolDefinition::entries)
    ).apply(instance, StardewWorldLootPoolDefinition::new)).validate(StardewWorldLootPoolDefinition::validate);

    public StardewWorldLootPoolDefinition {
        group = group == null ? "default" : group.trim();
        availableWhen = List.copyOf(availableWhen == null ? List.of() : availableWhen);
        entries = List.copyOf(entries == null ? List.of() : entries);
    }

    private static DataResult<StardewWorldLootPoolDefinition> validate(StardewWorldLootPoolDefinition definition) {
        if (definition.group().isBlank()) {
            return DataResult.error(() -> "world-loot group must not be blank");
        }
        if (definition.entries().isEmpty()) {
            return DataResult.error(() -> "world-loot pool needs at least one entry");
        }
        return DataResult.success(definition);
    }

    public enum Mode {
        WEIGHTED,
        SEQUENTIAL;

        public static final Codec<Mode> CODEC = Codec.STRING.comapFlatMap(raw -> {
            try {
                return DataResult.<Mode>success(valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                return DataResult.<Mode>error(() -> "unknown world-loot mode: " + raw);
            }
        }, value -> value.name().toLowerCase(java.util.Locale.ROOT));
    }

    public record Entry(
            StardewItemQuery query,
            int weight,
            double chance,
            boolean continueOnSuccess,
            List<StardewCondition> availableWhen
    ) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                StardewItemQueries.CODEC.fieldOf("query").forGetter(Entry::query),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("weight", 1).forGetter(Entry::weight),
                Codec.doubleRange(0.0, 1.0).optionalFieldOf("chance", 1.0).forGetter(Entry::chance),
                Codec.BOOL.optionalFieldOf("continue_on_success", false).forGetter(Entry::continueOnSuccess),
                StardewConditions.CODEC.listOf().optionalFieldOf("available_when", List.of())
                        .forGetter(Entry::availableWhen)
        ).apply(instance, Entry::new));

        public Entry {
            availableWhen = List.copyOf(availableWhen == null ? List.of() : availableWhen);
        }
    }
}
