package com.stardew.craft.api.v1.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Reloadable effects attached to a food through the
 * {@code stardewcraft:stardew_food_effects} item Data Map.
 *
 * <p>Each entry has its own stable ID so higher-priority data packs can replace
 * one effect without replacing unrelated effects contributed by other packs.
 */
public record StardewFoodEffectData(Map<ResourceLocation, StardewFoodEffect> effects) {
    public static final Codec<StardewFoodEffectData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(ResourceLocation.CODEC, StardewFoodEffect.CODEC)
                    .optionalFieldOf("effects", Map.of())
                    .forGetter(StardewFoodEffectData::effects)
    ).apply(instance, StardewFoodEffectData::new));

    public StardewFoodEffectData {
        Objects.requireNonNull(effects, "effects");
        LinkedHashMap<ResourceLocation, StardewFoodEffect> ordered = new LinkedHashMap<>();
        effects.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> ordered.put(
                        Objects.requireNonNull(entry.getKey(), "effect entry ID"),
                        Objects.requireNonNull(entry.getValue(), "effect entry")));
        effects = Collections.unmodifiableMap(ordered);
    }

    /** Combines data-pack contributions; entries from the newer value win by ID. */
    public StardewFoodEffectData merge(StardewFoodEffectData newer) {
        Objects.requireNonNull(newer, "newer");
        LinkedHashMap<ResourceLocation, StardewFoodEffect> merged = new LinkedHashMap<>(effects);
        merged.putAll(newer.effects);
        return new StardewFoodEffectData(merged);
    }
}
