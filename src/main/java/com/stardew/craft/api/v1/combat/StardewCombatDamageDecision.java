package com.stardew.craft.api.v1.combat;

/**
 * Ordered result of one damage modifier.
 *
 * <p>{@link Kind#PASS} preserves the current amount, {@link Kind#SET}
 * replaces it for later modifiers, and {@link Kind#DENY} cancels the
 * damage immediately.
 */
public record StardewCombatDamageDecision(
        Kind kind,
        float amount
) {
    public StardewCombatDamageDecision {
        if (kind == null) {
            throw new NullPointerException("kind");
        }
        if (kind == Kind.SET
                && (!Float.isFinite(amount) || amount < 0.0F)) {
            throw new IllegalArgumentException(
                    "SET amount must be finite and non-negative");
        }
    }

    public static StardewCombatDamageDecision pass() {
        return new StardewCombatDamageDecision(Kind.PASS, 0.0F);
    }

    public static StardewCombatDamageDecision set(float amount) {
        return new StardewCombatDamageDecision(Kind.SET, amount);
    }

    public static StardewCombatDamageDecision deny() {
        return new StardewCombatDamageDecision(Kind.DENY, 0.0F);
    }

    public enum Kind {
        PASS,
        SET,
        DENY
    }
}
