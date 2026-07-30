package com.stardew.craft.combat;

import java.util.Objects;

/**
 * A named, ordered damage adjustment.
 *
 * <p>The pipeline owns arithmetic. Callers only describe which adjustments
 * apply to the current hit.</p>
 */
public record DamageAdjustment(String id, Operation operation, float value, Rounding rounding) {
    public DamageAdjustment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(rounding, "rounding");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Damage adjustment id cannot be blank");
        }
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("Damage adjustment value must be finite");
        }
    }

    public static DamageAdjustment add(String id, float value) {
        return new DamageAdjustment(id, Operation.ADD, value, Rounding.NONE);
    }

    public static DamageAdjustment addFloor(String id, float value) {
        return new DamageAdjustment(id, Operation.ADD, value, Rounding.FLOOR);
    }

    public static DamageAdjustment multiply(String id, float value) {
        return new DamageAdjustment(id, Operation.MULTIPLY, value, Rounding.NONE);
    }

    public static DamageAdjustment multiplyCeil(String id, float value) {
        return new DamageAdjustment(id, Operation.MULTIPLY, value, Rounding.CEILING);
    }

    public static DamageAdjustment multiplyFloor(String id, float value) {
        return new DamageAdjustment(id, Operation.MULTIPLY, value, Rounding.FLOOR);
    }

    public static DamageAdjustment replaceWithBaseMultiplier(String id, float value) {
        return new DamageAdjustment(id, Operation.REPLACE_WITH_BASE_MULTIPLIER, value, Rounding.NONE);
    }

    public enum Operation {
        ADD,
        MULTIPLY,
        REPLACE_WITH_BASE_MULTIPLIER
    }

    public enum Rounding {
        NONE,
        FLOOR,
        CEILING
    }
}
