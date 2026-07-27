package com.stardew.craft.api.v1.npc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

/**
 * A deterministic, domain-specific patch for one NPC's gift taste lists.
 *
 * <p>Patches are applied from low to high priority, then by full resource ID.
 * Each patch removes items before adding items.
 */
public record StardewNpcGiftTastePatchDefinition(
        ResourceLocation npc,
        int priority,
        boolean required,
        Map<String, List<ResourceLocation>> add,
        Map<String, List<ResourceLocation>> remove
) {
    public static final List<String> CATEGORIES = List.of(
            "loved", "liked", "neutral", "disliked", "hated");
    private static final Set<String> CATEGORY_SET =
            Set.copyOf(CATEGORIES);

    private static final Codec<Map<String, List<ResourceLocation>>>
            CATEGORY_ITEMS_CODEC = Codec.unboundedMap(
                    Codec.STRING,
                    ResourceLocation.CODEC.listOf());

    public static final Codec<StardewNpcGiftTastePatchDefinition> CODEC =
            RecordCodecBuilder
                    .<StardewNpcGiftTastePatchDefinition>create(instance ->
                            instance.group(
                                    ResourceLocation.CODEC.fieldOf("npc")
                                            .forGetter(
                                                    StardewNpcGiftTastePatchDefinition
                                                            ::npc),
                                    Codec.INT.optionalFieldOf("priority", 0)
                                            .forGetter(
                                                    StardewNpcGiftTastePatchDefinition
                                                            ::priority),
                                    Codec.BOOL.optionalFieldOf(
                                                    "required", true)
                                            .forGetter(
                                                    StardewNpcGiftTastePatchDefinition
                                                            ::required),
                                    CATEGORY_ITEMS_CODEC.optionalFieldOf(
                                                    "add", Map.of())
                                            .forGetter(
                                                    StardewNpcGiftTastePatchDefinition
                                                            ::add),
                                    CATEGORY_ITEMS_CODEC.optionalFieldOf(
                                                    "remove", Map.of())
                                            .forGetter(
                                                    StardewNpcGiftTastePatchDefinition
                                                            ::remove)
                            ).apply(instance,
                                    StardewNpcGiftTastePatchDefinition::new))
                    .validate(
                            StardewNpcGiftTastePatchDefinition::validate);

    public StardewNpcGiftTastePatchDefinition {
        add = freeze(add);
        remove = freeze(remove);
    }

    private static Map<String, List<ResourceLocation>> freeze(
            Map<String, List<ResourceLocation>> source
    ) {
        LinkedHashMap<String, List<ResourceLocation>> result =
                new LinkedHashMap<>();
        if (source != null) {
            source.forEach((category, items) -> result.put(
                    category == null
                            ? ""
                            : category.trim().toLowerCase(Locale.ROOT),
                    List.copyOf(items == null ? List.of() : items)));
        }
        return Collections.unmodifiableMap(result);
    }

    private static DataResult<StardewNpcGiftTastePatchDefinition>
    validate(StardewNpcGiftTastePatchDefinition definition) {
        LinkedHashSet<String> unknown = new LinkedHashSet<>();
        definition.add().keySet().stream()
                .filter(category -> !CATEGORY_SET.contains(category))
                .forEach(unknown::add);
        definition.remove().keySet().stream()
                .filter(category -> !CATEGORY_SET.contains(category))
                .forEach(unknown::add);
        if (!unknown.isEmpty()) {
            return DataResult.error(
                    () -> "Unknown NPC gift taste categories " + unknown);
        }
        if (definition.add().isEmpty()
                && definition.remove().isEmpty()) {
            return DataResult.error(
                    () -> "NPC gift taste patch must add or remove items");
        }
        for (String category : CATEGORIES) {
            List<ResourceLocation> additions =
                    definition.add().getOrDefault(category, List.of());
            List<ResourceLocation> removals =
                    definition.remove().getOrDefault(category, List.of());
            if (new LinkedHashSet<>(additions).size()
                    != additions.size()) {
                return DataResult.error(
                        () -> "Duplicate added item in " + category);
            }
            if (new LinkedHashSet<>(removals).size()
                    != removals.size()) {
                return DataResult.error(
                        () -> "Duplicate removed item in " + category);
            }
            LinkedHashSet<ResourceLocation> overlap =
                    new LinkedHashSet<>(additions);
            overlap.retainAll(removals);
            if (!overlap.isEmpty()) {
                return DataResult.error(
                        () -> "NPC gift taste patch both adds and removes "
                                + overlap + " in " + category);
            }
        }
        return DataResult.success(definition);
    }
}
