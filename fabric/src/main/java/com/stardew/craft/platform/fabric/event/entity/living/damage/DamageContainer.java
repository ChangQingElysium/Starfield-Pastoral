package com.stardew.craft.platform.fabric.event.entity.living.damage;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.world.damagesource.DamageSource;

/** One identity-bearing native damage sequence, shared by Incoming and Pre and snapshotted by Post. */
public final class DamageContainer {
    public enum Reduction { INVULNERABILITY, ARMOR, ENCHANTMENTS, MOB_EFFECTS, ABSORPTION }
    private final DamageSource source;
    private final float originalDamage;
    private float damage;
    private float blockedDamage;
    private float shieldDamage;
    private int invulnerabilityTicks = 20;
    private final EnumMap<Reduction, Float> reductions = new EnumMap<>(Reduction.class);
    private final EnumMap<Reduction, List<IReductionFunction>> modifiers = new EnumMap<>(Reduction.class);

    public DamageContainer(DamageSource source, float originalDamage) {
        this.source = source;
        this.originalDamage = originalDamage;
        this.damage = originalDamage;
    }

    public DamageSource getSource() { return source; }
    public float getOriginalDamage() { return originalDamage; }
    public float getNewDamage() { return damage; }
    public void setNewDamage(float damage) { this.damage = damage; }
    public float getBlockedDamage() { return blockedDamage; }
    public float getShieldDamage() { return shieldDamage; }
    public int getPostAttackInvulnerabilityTicks() { return invulnerabilityTicks; }
    public void setPostAttackInvulnerabilityTicks(int ticks) { invulnerabilityTicks = ticks; }
    public float getReduction(Reduction reduction) { return reductions.getOrDefault(reduction, 0F); }
    public void addModifier(Reduction reduction, IReductionFunction modifier) {
        modifiers.computeIfAbsent(reduction, ignored -> new ArrayList<>()).add(modifier);
    }

    public void setReduction(Reduction reduction, float amount) {
        for (var modifier : modifiers.getOrDefault(reduction, List.of())) amount = modifier.modify(this, amount);
        reductions.put(reduction, amount);
        damage -= amount;
    }

    /** Native full shield block; extension events can supply partial values through this same ledger. */
    public void recordBlock(float blocked, float shield) {
        blockedDamage = blocked;
        shieldDamage = shield;
        damage -= blocked;
    }
}
