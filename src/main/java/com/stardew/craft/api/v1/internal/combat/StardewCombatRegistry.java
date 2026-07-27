package com.stardew.craft.api.v1.internal.combat;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.combat.StardewCombatDamageContext;
import com.stardew.craft.api.v1.combat.StardewCombatDamageDecision;
import com.stardew.craft.api.v1.combat.StardewCombatDamageModifier;
import com.stardew.craft.api.v1.combat.StardewCombatKillContext;
import com.stardew.craft.api.v1.combat.StardewCombatKillListener;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import net.minecraft.resources.ResourceLocation;

/** Internal ordered dispatch for public combat extension points. */
public final class StardewCombatRegistry {
    private static final OrderedExtensionRegistry<
            StardewCombatDamageModifier> DAMAGE_MODIFIERS =
            new OrderedExtensionRegistry<>(
                    id("combat/damage_modifiers"));
    private static final OrderedExtensionRegistry<
            StardewCombatKillListener> KILL_LISTENERS =
            new OrderedExtensionRegistry<>(
                    id("combat/kill_listeners"));

    private StardewCombatRegistry() {
    }

    public static void registerDamageModifier(
            ResourceLocation id,
            int priority,
            StardewCombatDamageModifier modifier
    ) {
        DAMAGE_MODIFIERS.register(id, priority, modifier);
    }

    public static void registerKillListener(
            ResourceLocation id,
            int priority,
            StardewCombatKillListener listener
    ) {
        KILL_LISTENERS.register(id, priority, listener);
    }

    public static float applyDamageModifiers(
            StardewCombatDamageContext initial
    ) {
        float amount = initial.amount();
        for (var registration : DAMAGE_MODIFIERS.entries()) {
            StardewCombatDamageDecision decision;
            try {
                float currentAmount = amount;
                decision = DAMAGE_MODIFIERS.invoke(
                        registration,
                        modifier -> modifier.modify(
                                new StardewCombatDamageContext(
                                        initial.target(),
                                        initial.attacker(),
                                        initial.source(),
                                        initial.dimension(),
                                        currentAmount)));
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Combat damage modifier {} failed for {}",
                        registration.id(),
                        initial.target().getType(),
                        exception);
                continue;
            }
            if (decision == null
                    || decision.kind()
                    == StardewCombatDamageDecision.Kind.PASS) {
                continue;
            }
            if (decision.kind()
                    == StardewCombatDamageDecision.Kind.DENY) {
                return 0.0F;
            }
            amount = decision.amount();
        }
        return amount;
    }

    public static void announceKill(
            StardewCombatKillContext context
    ) {
        for (var registration : KILL_LISTENERS.entries()) {
            try {
                KILL_LISTENERS.invokeVoid(
                        registration,
                        listener -> listener.onKill(context));
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Combat kill listener {} failed for {}",
                        registration.id(),
                        context.targetType(),
                        exception);
            }
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                StardewCraft.MODID, path);
    }
}
