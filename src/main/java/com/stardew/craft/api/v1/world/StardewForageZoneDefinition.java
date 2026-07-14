package com.stardew.craft.api.v1.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.api.v1.condition.StardewCondition;
import com.stardew.craft.api.v1.condition.StardewConditions;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

/** A reloadable outdoor zone and its seasonal forage block choices. */
public record StardewForageZoneDefinition(
        List<Rect> areas,
        List<Entry> entries,
        int minDailySpawn,
        int maxDailySpawn,
        int maxSpawnedAtOnce,
        Surface surface,
        int priority,
        List<StardewCondition> availableWhen
) {
    public static final Codec<StardewForageZoneDefinition> CODEC = RecordCodecBuilder.<StardewForageZoneDefinition>create(instance -> instance.group(
            Rect.CODEC.listOf().fieldOf("areas").forGetter(StardewForageZoneDefinition::areas),
            Entry.CODEC.listOf().fieldOf("entries").forGetter(StardewForageZoneDefinition::entries),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("min_daily_spawn")
                    .forGetter(StardewForageZoneDefinition::minDailySpawn),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("max_daily_spawn")
                    .forGetter(StardewForageZoneDefinition::maxDailySpawn),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("max_spawned_at_once")
                    .forGetter(StardewForageZoneDefinition::maxSpawnedAtOnce),
            Surface.CODEC.optionalFieldOf("surface", Surface.NATURAL)
                    .forGetter(StardewForageZoneDefinition::surface),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(StardewForageZoneDefinition::priority),
            StardewConditions.CODEC.listOf().optionalFieldOf("available_when", List.of())
                    .forGetter(StardewForageZoneDefinition::availableWhen)
    ).apply(instance, StardewForageZoneDefinition::new)).validate(StardewForageZoneDefinition::validate);

    public StardewForageZoneDefinition {
        areas = List.copyOf(areas == null ? List.of() : areas);
        entries = List.copyOf(entries == null ? List.of() : entries);
        availableWhen = List.copyOf(availableWhen == null ? List.of() : availableWhen);
    }

    private static DataResult<StardewForageZoneDefinition> validate(StardewForageZoneDefinition definition) {
        if (definition.areas().isEmpty()) return DataResult.error(() -> "forage zone needs an area");
        if (definition.entries().isEmpty()) return DataResult.error(() -> "forage zone needs entries");
        if (definition.maxDailySpawn() < definition.minDailySpawn()) {
            return DataResult.error(() -> "max_daily_spawn must be >= min_daily_spawn");
        }
        if (definition.maxSpawnedAtOnce() < definition.minDailySpawn()) {
            return DataResult.error(() -> "max_spawned_at_once must be >= min_daily_spawn");
        }
        return DataResult.success(definition);
    }

    public record Rect(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int weight) {
        public static final Codec<Rect> CODEC = RecordCodecBuilder.<Rect>create(instance -> instance.group(
                Codec.INT.fieldOf("min_x").forGetter(Rect::minX),
                Codec.INT.fieldOf("min_y").forGetter(Rect::minY),
                Codec.INT.fieldOf("min_z").forGetter(Rect::minZ),
                Codec.INT.fieldOf("max_x").forGetter(Rect::maxX),
                Codec.INT.fieldOf("max_y").forGetter(Rect::maxY),
                Codec.INT.fieldOf("max_z").forGetter(Rect::maxZ),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("weight", 1).forGetter(Rect::weight)
        ).apply(instance, Rect::new)).validate(rect ->
                rect.maxX() < rect.minX() || rect.maxY() < rect.minY() || rect.maxZ() < rect.minZ()
                        ? DataResult.error(() -> "forage area max values must be >= min values")
                        : DataResult.success(rect));
    }

    public record Entry(ResourceLocation block, List<String> seasons, double chance) {
        private static final List<String> VALID_SEASONS = List.of("spring", "summer", "fall", "winter");
        public static final Codec<Entry> CODEC = RecordCodecBuilder.<Entry>create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("block").forGetter(Entry::block),
                Codec.STRING.listOf().optionalFieldOf("seasons", VALID_SEASONS).forGetter(Entry::seasons),
                Codec.doubleRange(0.0, 1.0).optionalFieldOf("chance", 1.0).forGetter(Entry::chance)
        ).apply(instance, Entry::new)).validate(Entry::validate);

        public Entry {
            seasons = (seasons == null ? VALID_SEASONS : seasons).stream()
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .distinct()
                    .toList();
        }

        private static DataResult<Entry> validate(Entry entry) {
            if (entry.seasons().isEmpty()) return DataResult.error(() -> "forage entry needs seasons");
            for (String season : entry.seasons()) {
                if (!VALID_SEASONS.contains(season)) {
                    return DataResult.error(() -> "unknown forage season: " + season);
                }
            }
            return DataResult.success(entry);
        }
    }

    public enum Surface {
        NATURAL,
        SAND;

        public static final Codec<Surface> CODEC = Codec.STRING.comapFlatMap(raw -> {
            try {
                return DataResult.<Surface>success(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                return DataResult.<Surface>error(() -> "unknown forage surface: " + raw);
            }
        }, value -> value.name().toLowerCase(Locale.ROOT));
    }
}
