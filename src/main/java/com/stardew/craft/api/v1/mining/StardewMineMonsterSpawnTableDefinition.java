package com.stardew.craft.api.v1.mining;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Atomic datapack table that selects registered mine-monster profiles.
 *
 * <p>Empty theme and mechanic lists match every active mine theme. An absent
 * {@code dark} value matches both normal and dark floors.
 */
public record StardewMineMonsterSpawnTableDefinition(
        int minFloor,
        int maxFloor,
        int priority,
        List<ResourceLocation> themes,
        List<String> mechanics,
        Optional<Boolean> dark,
        double minDistance,
        double maxDistance,
        List<StardewMineMonsterSpawnEntry> entries
) {
    public static final Codec<StardewMineMonsterSpawnTableDefinition> CODEC =
            RecordCodecBuilder.<StardewMineMonsterSpawnTableDefinition>create(
                    instance -> instance.group(
                            Codec.intRange(1, Integer.MAX_VALUE)
                                    .fieldOf("min_floor")
                                    .forGetter(StardewMineMonsterSpawnTableDefinition::minFloor),
                            Codec.intRange(1, Integer.MAX_VALUE)
                                    .fieldOf("max_floor")
                                    .forGetter(StardewMineMonsterSpawnTableDefinition::maxFloor),
                            Codec.INT.optionalFieldOf("priority", 0)
                                    .forGetter(StardewMineMonsterSpawnTableDefinition::priority),
                            ResourceLocation.CODEC.listOf()
                                    .optionalFieldOf("themes", List.of())
                                    .forGetter(StardewMineMonsterSpawnTableDefinition::themes),
                            Codec.STRING.listOf()
                                    .optionalFieldOf("mechanics", List.of())
                                    .forGetter(StardewMineMonsterSpawnTableDefinition::mechanics),
                            Codec.BOOL.optionalFieldOf("dark")
                                    .forGetter(StardewMineMonsterSpawnTableDefinition::dark),
                            Codec.doubleRange(0.0, Double.MAX_VALUE)
                                    .optionalFieldOf("min_distance", 0.0)
                                    .forGetter(StardewMineMonsterSpawnTableDefinition::minDistance),
                            Codec.doubleRange(0.0, Double.MAX_VALUE)
                                    .optionalFieldOf("max_distance", Double.MAX_VALUE)
                                    .forGetter(StardewMineMonsterSpawnTableDefinition::maxDistance),
                            StardewMineMonsterSpawnEntry.CODEC.listOf()
                                    .fieldOf("entries")
                                    .forGetter(StardewMineMonsterSpawnTableDefinition::entries)
                    ).apply(instance,
                            StardewMineMonsterSpawnTableDefinition::new))
                    .validate(StardewMineMonsterSpawnTableDefinition::validate);

    public StardewMineMonsterSpawnTableDefinition {
        themes = List.copyOf(themes == null ? List.of() : themes);
        mechanics = (mechanics == null ? List.<String>of() : mechanics)
                .stream()
                .map(value -> value == null
                        ? ""
                        : value.trim().toLowerCase(Locale.ROOT))
                .toList();
        dark = dark == null ? Optional.empty() : dark;
        entries = List.copyOf(entries == null ? List.of() : entries);
    }

    private static DataResult<StardewMineMonsterSpawnTableDefinition>
    validate(StardewMineMonsterSpawnTableDefinition definition) {
        if (definition.maxFloor() < definition.minFloor()) {
            return DataResult.error(
                    () -> "mine monster max_floor must be >= min_floor");
        }
        if (definition.maxDistance() < definition.minDistance()) {
            return DataResult.error(
                    () -> "mine monster max_distance must be >= min_distance");
        }
        if (definition.mechanics().stream().anyMatch(String::isBlank)) {
            return DataResult.error(
                    () -> "mine monster mechanics must not contain blank IDs");
        }
        if (definition.entries().isEmpty()) {
            return DataResult.error(
                    () -> "mine monster spawn table needs at least one entry");
        }
        long totalWeight = definition.entries().stream()
                .mapToLong(StardewMineMonsterSpawnEntry::weight)
                .sum();
        if (totalWeight > Integer.MAX_VALUE) {
            return DataResult.error(
                    () -> "mine monster spawn weights exceed integer range");
        }
        return DataResult.success(definition);
    }
}
