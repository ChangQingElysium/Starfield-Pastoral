package com.stardew.craft.api.v1.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Reloadable location metadata; it never places or moves a pre-generated structure. */
public record StardewLocationDefinition(
        ResourceLocation dimension,
        String ledgerId,
        Vec3i min,
        Vec3i max,
        List<String> aliases,
        int priority,
        boolean indoor,
        @Nullable ResourceLocation parentId,
        Component displayName,
        Component description,
        @Nullable ResourceLocation iconTexture,
        List<ResourceLocation> tags,
        Map<ResourceLocation, String> properties
) {
    public static final Codec<StardewLocationDefinition> CODEC = RecordCodecBuilder.<StardewLocationDefinition>create(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("dimension",
                    ResourceLocation.fromNamespaceAndPath("stardewcraft", "stardew_valley"))
                    .forGetter(StardewLocationDefinition::dimension),
            Codec.STRING.optionalFieldOf("ledger_id", "").forGetter(StardewLocationDefinition::ledgerId),
            Vec3i.CODEC.fieldOf("min").forGetter(StardewLocationDefinition::min),
            Vec3i.CODEC.fieldOf("max").forGetter(StardewLocationDefinition::max),
            Codec.STRING.listOf().optionalFieldOf("aliases", List.of()).forGetter(StardewLocationDefinition::aliases),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(StardewLocationDefinition::priority),
            Codec.BOOL.optionalFieldOf("indoor", false).forGetter(StardewLocationDefinition::indoor),
            ResourceLocation.CODEC.optionalFieldOf("parent")
                    .forGetter(definition -> Optional.ofNullable(
                            definition.parentId())),
            ComponentSerialization.CODEC.optionalFieldOf(
                            "display_name", Component.empty())
                    .forGetter(StardewLocationDefinition::displayName),
            ComponentSerialization.CODEC.optionalFieldOf(
                            "description", Component.empty())
                    .forGetter(StardewLocationDefinition::description),
            ResourceLocation.CODEC.optionalFieldOf("icon")
                    .forGetter(definition -> Optional.ofNullable(
                            definition.iconTexture())),
            ResourceLocation.CODEC.listOf()
                    .optionalFieldOf("tags", List.of())
                    .forGetter(StardewLocationDefinition::tags),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.STRING)
                    .optionalFieldOf("properties", Map.of())
                    .forGetter(StardewLocationDefinition::properties)
    ).apply(instance, (dimension, ledgerId, min, max, aliases,
                        priority, indoor, parent, displayName,
                        description, icon, tags, properties) ->
            new StardewLocationDefinition(
                    dimension, ledgerId, min, max, aliases,
                    priority, indoor, parent.orElse(null),
                    displayName, description, icon.orElse(null),
                    tags, properties)))
            .validate(StardewLocationDefinition::validate);

    /** Backward-compatible constructor for definitions without hierarchy metadata. */
    public StardewLocationDefinition(
            ResourceLocation dimension,
            String ledgerId,
            Vec3i min,
            Vec3i max,
            List<String> aliases,
            int priority,
            boolean indoor
    ) {
        this(dimension, ledgerId, min, max, aliases, priority,
                indoor, null, Component.empty(), Component.empty(),
                null, List.of(), Map.of());
    }

    /** Backward-compatible constructor for definitions created before location environments. */
    public StardewLocationDefinition(
            ResourceLocation dimension,
            String ledgerId,
            Vec3i min,
            Vec3i max,
            List<String> aliases,
            int priority
    ) {
        this(dimension, ledgerId, min, max, aliases, priority,
                false, null, Component.empty(), Component.empty(),
                null, List.of(), Map.of());
    }

    public StardewLocationDefinition {
        dimension = Objects.requireNonNull(dimension, "dimension");
        min = Objects.requireNonNull(min, "min");
        max = Objects.requireNonNull(max, "max");
        ledgerId = ledgerId == null ? "" : ledgerId.trim();
        LinkedHashSet<String> normalizedAliases = new LinkedHashSet<>();
        for (String alias : aliases == null ? List.<String>of() : aliases) {
            if (alias != null && !alias.isBlank()) {
                normalizedAliases.add(alias.trim());
            }
        }
        aliases = List.copyOf(normalizedAliases);
        displayName = Objects.requireNonNull(
                displayName, "displayName");
        description = Objects.requireNonNull(
                description, "description");
        tags = List.copyOf(new LinkedHashSet<>(
                tags == null ? List.of() : tags));
        LinkedHashMap<ResourceLocation, String> normalizedProperties =
                new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, String> entry
                : (properties == null
                        ? Map.<ResourceLocation, String>of()
                        : properties).entrySet()) {
            ResourceLocation key = Objects.requireNonNull(
                    entry.getKey(), "property key");
            String value = Objects.requireNonNull(
                    entry.getValue(), "property value").trim();
            normalizedProperties.put(key, value);
        }
        properties = java.util.Collections.unmodifiableMap(
                normalizedProperties);
    }

    private static DataResult<StardewLocationDefinition> validate(StardewLocationDefinition definition) {
        if (definition.max().x() < definition.min().x()
                || definition.max().y() < definition.min().y()
                || definition.max().z() < definition.min().z()) {
            return DataResult.error(() -> "location max values must be >= min values");
        }
        if (definition.aliases().size() > 64) {
            return DataResult.error(
                    () -> "location has more than 64 aliases");
        }
        if (definition.tags().size() > 64) {
            return DataResult.error(
                    () -> "location has more than 64 tags");
        }
        if (definition.properties().size() > 128) {
            return DataResult.error(
                    () -> "location has more than 128 properties");
        }
        for (Map.Entry<ResourceLocation, String> entry
                : definition.properties().entrySet()) {
            if (entry.getValue().isBlank()
                    || entry.getValue().length() > 256) {
                return DataResult.error(() ->
                        "location property " + entry.getKey()
                                + " must contain 1-256 characters");
            }
        }
        return DataResult.success(definition);
    }

    public record Vec3i(int x, int y, int z) {
        public static final Codec<Vec3i> CODEC = Codec.INT.listOf().comapFlatMap(values ->
                values.size() == 3
                        ? DataResult.success(new Vec3i(values.get(0), values.get(1), values.get(2)))
                        : DataResult.error(() -> "location vector must contain exactly three integers"),
                value -> List.of(value.x(), value.y(), value.z()));
    }
}
