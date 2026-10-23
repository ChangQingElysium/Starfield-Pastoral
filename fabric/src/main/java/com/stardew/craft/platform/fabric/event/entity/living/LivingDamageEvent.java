package com.stardew.craft.platform.fabric.event.entity.living;

import com.stardew.craft.platform.fabric.event.entity.living.damage.DamageContainer;
import java.util.EnumMap;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

public abstract class LivingDamageEvent extends LivingEvent {
    protected LivingDamageEvent(LivingEntity entity) { super(entity, false); }

    /** After armor/effects/enchantments, before absorption and actual health changes. */
    public static final class Pre extends LivingDamageEvent {
        private final DamageContainer container;
        public Pre(LivingEntity entity, DamageContainer container) { super(entity); this.container = container; }
        public DamageContainer getContainer() { return container; }
        public DamageSource getSource() { return container.getSource(); }
        public float getNewDamage() { return container.getNewDamage(); }
        public float getOriginalDamage() { return container.getOriginalDamage(); }
        public void setNewDamage(float damage) { container.setNewDamage(damage); }
    }

    /** Immutable snapshot after actuallyHurt, including zero-damage sequences, before outer hurt handles death. */
    public static final class Post extends LivingDamageEvent {
        private final DamageSource source;
        private final float originalDamage;
        private final float newDamage;
        private final float blockedDamage;
        private final float shieldDamage;
        private final int invulnerabilityTicks;
        private final EnumMap<DamageContainer.Reduction, Float> reductions = new EnumMap<>(DamageContainer.Reduction.class);
        public Post(LivingEntity entity, DamageContainer container) {
            super(entity);
            source = container.getSource(); originalDamage = container.getOriginalDamage(); newDamage = container.getNewDamage();
            blockedDamage = container.getBlockedDamage(); shieldDamage = container.getShieldDamage();
            invulnerabilityTicks = container.getPostAttackInvulnerabilityTicks();
            for (var reduction : DamageContainer.Reduction.values()) reductions.put(reduction, container.getReduction(reduction));
        }
        public DamageSource getSource() { return source; }
        public float getOriginalDamage() { return originalDamage; }
        public float getNewDamage() { return newDamage; }
        public float getBlockedDamage() { return blockedDamage; }
        public float getShieldDamage() { return shieldDamage; }
        public int getPostAttackInvulnerabilityTicks() { return invulnerabilityTicks; }
        public float getReduction(DamageContainer.Reduction type) { return reductions.getOrDefault(type, 0F); }
    }
}
