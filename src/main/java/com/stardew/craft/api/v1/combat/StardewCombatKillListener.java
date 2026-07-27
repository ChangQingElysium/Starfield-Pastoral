package com.stardew.craft.api.v1.combat;

/** Observes a confirmed player kill on the logical server. */
@FunctionalInterface
public interface StardewCombatKillListener {
    void onKill(StardewCombatKillContext context);
}
