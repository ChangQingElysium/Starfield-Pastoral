package com.stardew.craft.api.v1.building;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.api.v1.condition.StardewCondition;
import com.stardew.craft.api.v1.condition.StardewConditions;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Server-authoritative definition of one purchasable building product.
 *
 * <p>The definition ID is supplied by the Java registration or datapack file.
 * Builders are namespaced catalog IDs rather than fixed NPC names, so an addon
 * may expose the same catalog from any interaction it owns.
 */
public record StardewBuildingBlueprintDefinition(
        ResourceLocation builder,
        int order,
        String displayNameKey,
        String descriptionKey,
        int money,
        List<StardewBuildingMaterial> materials,
        ResourceLocation resultItem,
        int resultCount,
        boolean upgrade,
        int previewCanvasSize,
        boolean magicalConstruction,
        List<StardewCondition> availableWhen,
        List<ResourceLocation> tags,
        Map<ResourceLocation, String> properties
) {
    public static final Codec<StardewBuildingBlueprintDefinition> CODEC =
            RecordCodecBuilder.<StardewBuildingBlueprintDefinition>create(
                    instance -> instance.group(
                            ResourceLocation.CODEC.fieldOf("builder")
                                    .forGetter(StardewBuildingBlueprintDefinition::builder),
                            Codec.INT.optionalFieldOf("order", 0)
                                    .forGetter(StardewBuildingBlueprintDefinition::order),
                            Codec.STRING.fieldOf("display_name")
                                    .forGetter(StardewBuildingBlueprintDefinition::displayNameKey),
                            Codec.STRING.fieldOf("description")
                                    .forGetter(StardewBuildingBlueprintDefinition::descriptionKey),
                            Codec.intRange(0, Integer.MAX_VALUE)
                                    .optionalFieldOf("money", 0)
                                    .forGetter(StardewBuildingBlueprintDefinition::money),
                            StardewBuildingMaterial.CODEC.listOf()
                                    .optionalFieldOf("materials", List.of())
                                    .forGetter(StardewBuildingBlueprintDefinition::materials),
                            ResourceLocation.CODEC.fieldOf("result_item")
                                    .forGetter(StardewBuildingBlueprintDefinition::resultItem),
                            Codec.intRange(1, 64).optionalFieldOf("result_count", 1)
                                    .forGetter(StardewBuildingBlueprintDefinition::resultCount),
                            Codec.BOOL.optionalFieldOf("upgrade", false)
                                    .forGetter(StardewBuildingBlueprintDefinition::upgrade),
                            Codec.intRange(8, 512).optionalFieldOf(
                                            "preview_canvas_size", 16)
                                    .forGetter(StardewBuildingBlueprintDefinition::previewCanvasSize),
                            Codec.BOOL.optionalFieldOf(
                                            "magical_construction", false)
                                    .forGetter(StardewBuildingBlueprintDefinition::magicalConstruction),
                            StardewConditions.CODEC.listOf()
                                    .optionalFieldOf("available_when", List.of())
                                    .forGetter(StardewBuildingBlueprintDefinition::availableWhen),
                            ResourceLocation.CODEC.listOf()
                                    .optionalFieldOf("tags", List.of())
                                    .forGetter(StardewBuildingBlueprintDefinition::tags),
                            Codec.unboundedMap(ResourceLocation.CODEC, Codec.STRING)
                                    .optionalFieldOf("properties", Map.of())
                                    .forGetter(StardewBuildingBlueprintDefinition::properties)
                    ).apply(instance, StardewBuildingBlueprintDefinition::new))
                    .validate(StardewBuildingBlueprintDefinition::validate);

    public StardewBuildingBlueprintDefinition {
        builder = Objects.requireNonNull(builder, "builder");
        displayNameKey = requireText(displayNameKey, "displayNameKey");
        descriptionKey = requireText(descriptionKey, "descriptionKey");
        resultItem = Objects.requireNonNull(resultItem, "resultItem");
        materials = List.copyOf(
                Objects.requireNonNull(materials, "materials"));
        availableWhen = List.copyOf(
                Objects.requireNonNull(availableWhen, "availableWhen"));
        tags = List.copyOf(new LinkedHashSet<>(
                Objects.requireNonNull(tags, "tags")));
        properties = Map.copyOf(new LinkedHashMap<>(
                Objects.requireNonNull(properties, "properties")));
        if (money < 0) {
            throw new IllegalArgumentException(
                    "building blueprint money must not be negative");
        }
        if (resultCount < 1 || resultCount > 64) {
            throw new IllegalArgumentException(
                    "building blueprint resultCount must be 1-64");
        }
        if (previewCanvasSize < 8 || previewCanvasSize > 512) {
            throw new IllegalArgumentException(
                    "building blueprint previewCanvasSize must be 8-512");
        }
        validateCollections(
                materials, availableWhen, tags, properties);
    }

    private static DataResult<StardewBuildingBlueprintDefinition> validate(
            StardewBuildingBlueprintDefinition definition
    ) {
        try {
            validateCollections(
                    definition.materials(),
                    definition.availableWhen(),
                    definition.tags(),
                    definition.properties());
        } catch (IllegalArgumentException exception) {
            return DataResult.error(() ->
                    exception.getMessage());
        }
        return DataResult.success(definition);
    }

    private static void validateCollections(
            List<StardewBuildingMaterial> materials,
            List<StardewCondition> availableWhen,
            List<ResourceLocation> tags,
            Map<ResourceLocation, String> properties
    ) {
        if (materials.size() > 32) {
            throw new IllegalArgumentException(
                    "building blueprint has more than 32 material rows");
        }
        HashSet<ResourceLocation> seen = new HashSet<>();
        for (StardewBuildingMaterial material : materials) {
            if (!seen.add(material.item())) {
                throw new IllegalArgumentException(
                        "duplicate building material " + material.item());
            }
        }
        if (availableWhen.size() > 32) {
            throw new IllegalArgumentException(
                    "building blueprint has more than 32 conditions");
        }
        if (tags.size() > 32 || properties.size() > 32) {
            throw new IllegalArgumentException(
                    "building blueprint metadata exceeds 32 entries");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 256) {
            throw new IllegalArgumentException(
                    field + " must contain 1-256 characters");
        }
        return trimmed;
    }
}
