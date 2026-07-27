package com.stardew.craft.api.v1.combat;

/** Synchronous server-side modifier for incoming living-entity damage. */
@FunctionalInterface
public interface StardewCombatDamageModifier {
    StardewCombatDamageDecision modify(
            StardewCombatDamageContext context
    );
}
