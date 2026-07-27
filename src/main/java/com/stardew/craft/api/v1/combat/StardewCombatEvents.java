package com.stardew.craft.api.v1.combat;

import com.stardew.craft.api.v1.internal.combat.StardewCombatRegistry;
import net.minecraft.resources.ResourceLocation;

/** Registration facade for ordered server-side combat extensions. */
public final class StardewCombatEvents {
    private StardewCombatEvents() {
    }

    public static void registerDamageModifier(
            ResourceLocation id,
            int priority,
            StardewCombatDamageModifier modifier
    ) {
        StardewCombatRegistry.registerDamageModifier(
                id, priority, modifier);
    }

    public static void registerKillListener(
            ResourceLocation id,
            int priority,
            StardewCombatKillListener listener
    ) {
        StardewCombatRegistry.registerKillListener(
                id, priority, listener);
    }
}
