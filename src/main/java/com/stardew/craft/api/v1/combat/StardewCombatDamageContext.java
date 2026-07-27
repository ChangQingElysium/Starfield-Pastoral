package com.stardew.craft.api.v1.combat;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Synchronous server-side context for one incoming damage decision.
 *
 * <p>The amount is the result of higher-priority decisions and may differ
 * from the amount originally supplied by NeoForge.
 */
public record StardewCombatDamageContext(
        LivingEntity target,
        @Nullable Entity attacker,
        DamageSource source,
        ResourceKey<Level> dimension,
        float amount
) {
    public StardewCombatDamageContext {
        target = Objects.requireNonNull(target, "target");
        source = Objects.requireNonNull(source, "source");
        dimension = Objects.requireNonNull(dimension, "dimension");
        if (!Float.isFinite(amount) || amount < 0.0F) {
            throw new IllegalArgumentException(
                    "amount must be finite and non-negative");
        }
    }
}
