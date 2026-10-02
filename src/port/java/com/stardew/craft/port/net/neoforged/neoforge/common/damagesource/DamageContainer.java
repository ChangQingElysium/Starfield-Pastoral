package com.stardew.craft.port.net.neoforged.neoforge.common.damagesource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.world.damagesource.DamageSource;

/**
 * PORT(1.20.1): NeoForge 21.1 {@code DamageContainer}, carried through one {@code LivingEntity#hurt} call.
 * <p>
 * On Forge 1.20.1 the per-entity container stack and the reduction capture live in
 * {@code com.stardew.craft.mixin.PortLivingEntityDamageMixin} / {@code PortPlayerDamageMixin}; see
 * {@code com.stardew.craft.port.event.PortDamageHooks} for the sequence. Shield blocking is not captured
 * ({@link #getBlockedDamage()} / {@link #getShieldDamage()} stay 0) because Forge's {@code ShieldBlockEvent} has no
 * container; the mod never reads them.
 */
public class DamageContainer {
    public enum Reduction {
        /** Damage reduced from post attack invulnerability. */
        INVULNERABILITY,
        /** Damage reduced from the effects of armor. */
        ARMOR,
        /** Damage reduced from enchantments on armor. */
        ENCHANTMENTS,
        /** Damage reduced from active mob effects. */
        MOB_EFFECTS,
        /** Damage absorbed by absorption. */
        ABSORPTION
    }

    private final EnumMap<Reduction, List<IReductionFunction>> reductionFunctions = new EnumMap<>(Reduction.class);
    private final float originalDamage;
    private final DamageSource source;
    private float newDamage;
    private final EnumMap<Reduction, Float> reductions = new EnumMap<>(Reduction.class);
    private float blockedDamage = 0f;
    private float shieldDamage = 0;
    private int invulnerabilityTicksAfterAttack = 20;

    public DamageContainer(DamageSource source, float originalDamage) {
        this.source = source;
        this.originalDamage = originalDamage;
        this.newDamage = originalDamage;
    }

    public float getOriginalDamage() {
        return originalDamage;
    }

    public DamageSource getSource() {
        return source;
    }

    public void setNewDamage(float damage) {
        this.newDamage = damage;
    }

    public float getNewDamage() {
        return newDamage;
    }

    public void addModifier(Reduction type, IReductionFunction reductionFunction) {
        this.reductionFunctions.computeIfAbsent(type, a -> new ArrayList<>()).add(reductionFunction);
    }

    public float getBlockedDamage() {
        return blockedDamage;
    }

    public float getShieldDamage() {
        return shieldDamage;
    }

    public void setPostAttackInvulnerabilityTicks(int ticks) {
        this.invulnerabilityTicksAfterAttack = ticks;
    }

    public int getPostAttackInvulnerabilityTicks() {
        return invulnerabilityTicksAfterAttack;
    }

    public float getReduction(Reduction type) {
        return reductions.getOrDefault(type, 0f);
    }

    //=============INTERNAL METHODS - DO NOT USE===================

    /** Records a vanilla reduction (after running registered modifiers) and subtracts it from the new damage. */
    public void setReduction(Reduction reduction, float amount) {
        float modifiedReduction = modifyReduction(reduction, amount);
        this.reductions.put(reduction, modifiedReduction);
        this.newDamage -= modifiedReduction;
    }

    /** PORT(1.20.1): records a reduction value without touching the new damage (used for absorption). */
    public void recordReduction(Reduction reduction, float amount) {
        this.reductions.put(reduction, amount);
    }

    private float modifyReduction(Reduction type, float reduction) {
        for (var func : reductionFunctions.getOrDefault(type, List.of())) {
            reduction = func.modify(this, reduction);
        }
        return reduction;
    }
}
