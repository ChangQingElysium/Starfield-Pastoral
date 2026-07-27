package com.stardew.craft.api.v1.farm;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable, server-validated configuration stored with one farm instance. */
public record StardewFarmLayoutConfiguration(Map<ResourceLocation, String> values) {
    private static final int MAX_FIELDS = 64;

    public StardewFarmLayoutConfiguration {
        Objects.requireNonNull(values, "values");
        if (values.size() > MAX_FIELDS) {
            throw new IllegalArgumentException("Farm layout configuration has too many fields");
        }
        LinkedHashMap<ResourceLocation, String> copy = new LinkedHashMap<>();
        values.forEach((id, value) -> copy.put(
                Objects.requireNonNull(id, "configuration id"),
                Objects.requireNonNull(value, "configuration value")));
        values = Map.copyOf(copy);
    }

    public static StardewFarmLayoutConfiguration empty() {
        return new StardewFarmLayoutConfiguration(Map.of());
    }

    /**
     * Applies defaults and rejects unknown or invalid client values.
     *
     * <p>This method is intentionally usable by addons for their own tests,
     * while creation always repeats validation on the logical server.
     */
    public static StardewFarmLayoutConfiguration validate(
            List<StardewFarmLayoutConfigField> fields,
            Map<ResourceLocation, String> requestedValues
    ) {
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(requestedValues, "requestedValues");
        if (fields.size() > MAX_FIELDS || requestedValues.size() > MAX_FIELDS) {
            throw new IllegalArgumentException("Farm layout configuration has too many fields");
        }

        LinkedHashMap<ResourceLocation, StardewFarmLayoutConfigField> schema =
                new LinkedHashMap<>();
        for (StardewFarmLayoutConfigField field : fields) {
            if (schema.putIfAbsent(field.id(), field) != null) {
                throw new IllegalArgumentException(
                        "Duplicate farm layout configuration field: " + field.id());
            }
        }
        for (ResourceLocation requestedId : requestedValues.keySet()) {
            if (!schema.containsKey(requestedId)) {
                throw new IllegalArgumentException(
                        "Unknown farm layout configuration field: " + requestedId);
            }
        }

        LinkedHashMap<ResourceLocation, String> normalized = new LinkedHashMap<>();
        schema.forEach((id, field) -> {
            String requested = requestedValues.getOrDefault(id, field.defaultValue());
            String value = field.normalize(requested).orElseThrow(() ->
                    new IllegalArgumentException(
                            "Invalid value for farm layout configuration field: " + id));
            normalized.put(id, value);
        });
        return new StardewFarmLayoutConfiguration(normalized);
    }

    public Optional<String> find(ResourceLocation id) {
        return Optional.ofNullable(values.get(id));
    }

    public boolean booleanValue(ResourceLocation id, boolean fallback) {
        return find(id).map(Boolean::parseBoolean).orElse(fallback);
    }

    public int integerValue(ResourceLocation id, int fallback) {
        return find(id).flatMap(value -> {
            try {
                return Optional.of(Integer.parseInt(value));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }).orElse(fallback);
    }
}
