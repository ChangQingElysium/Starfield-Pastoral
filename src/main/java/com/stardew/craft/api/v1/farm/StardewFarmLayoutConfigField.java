package com.stardew.craft.api.v1.farm;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * One server-defined option shown while a player creates a farm.
 *
 * <p>Values use a small string wire format so new field presentation can be
 * added without exposing arbitrary addon classes to the client.
 */
public record StardewFarmLayoutConfigField(
        ResourceLocation id,
        Component label,
        Component description,
        Type type,
        String defaultValue,
        int minimum,
        int maximum,
        List<String> choices
) {
    private static final int MAX_CHOICES = 64;
    private static final int MAX_VALUE_LENGTH = 128;

    public StardewFarmLayoutConfigField {
        id = Objects.requireNonNull(id, "id");
        label = Objects.requireNonNull(label, "label");
        description = Objects.requireNonNull(description, "description");
        type = Objects.requireNonNull(type, "type");
        choices = List.copyOf(Objects.requireNonNull(choices, "choices"));
        if (choices.size() > MAX_CHOICES) {
            throw new IllegalArgumentException("Farm layout option has too many choices: " + id);
        }
        if (type == Type.INTEGER && minimum > maximum) {
            throw new IllegalArgumentException("Farm layout integer minimum exceeds maximum: " + id);
        }
        if (type != Type.CHOICE && !choices.isEmpty()) {
            throw new IllegalArgumentException("Only choice fields may declare choices: " + id);
        }
        if (type == Type.CHOICE && choices.isEmpty()) {
            throw new IllegalArgumentException("Choice field must declare at least one choice: " + id);
        }
        for (String choice : choices) {
            validateWireValue(choice, "choice");
        }
        Optional<String> normalizedDefault = normalize(
                type, defaultValue, minimum, maximum, choices);
        if (normalizedDefault.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid default value for farm layout option: " + id);
        }
        defaultValue = normalizedDefault.get();
    }

    public static StardewFarmLayoutConfigField bool(
            ResourceLocation id,
            Component label,
            Component description,
            boolean defaultValue
    ) {
        return new StardewFarmLayoutConfigField(
                id, label, description, Type.BOOLEAN,
                Boolean.toString(defaultValue), 0, 0, List.of());
    }

    public static StardewFarmLayoutConfigField integer(
            ResourceLocation id,
            Component label,
            Component description,
            int defaultValue,
            int minimum,
            int maximum
    ) {
        return new StardewFarmLayoutConfigField(
                id, label, description, Type.INTEGER,
                Integer.toString(defaultValue), minimum, maximum, List.of());
    }

    public static StardewFarmLayoutConfigField choice(
            ResourceLocation id,
            Component label,
            Component description,
            String defaultValue,
            List<String> choices
    ) {
        return new StardewFarmLayoutConfigField(
                id, label, description, Type.CHOICE,
                defaultValue, 0, 0, choices);
    }

    /** Returns the canonical wire value, or empty when the value is invalid. */
    public Optional<String> normalize(String rawValue) {
        return normalize(type, rawValue, minimum, maximum, choices);
    }

    private static Optional<String> normalize(
            Type type,
            String rawValue,
            int minimum,
            int maximum,
            List<String> choices
    ) {
        if (rawValue == null || rawValue.length() > MAX_VALUE_LENGTH) {
            return Optional.empty();
        }
        return switch (type) {
            case BOOLEAN -> {
                String value = rawValue.trim().toLowerCase(Locale.ROOT);
                yield value.equals("true") || value.equals("false")
                        ? Optional.of(value) : Optional.empty();
            }
            case INTEGER -> {
                try {
                    int value = Integer.parseInt(rawValue.trim());
                    yield value >= minimum && value <= maximum
                            ? Optional.of(Integer.toString(value))
                            : Optional.empty();
                } catch (NumberFormatException ignored) {
                    yield Optional.empty();
                }
            }
            case CHOICE -> choices.contains(rawValue)
                    ? Optional.of(rawValue) : Optional.empty();
        };
    }

    private static void validateWireValue(String value, String kind) {
        if (value == null || value.isBlank() || value.length() > MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException("Invalid farm layout " + kind);
        }
    }

    public enum Type {
        BOOLEAN,
        INTEGER,
        CHOICE
    }
}
