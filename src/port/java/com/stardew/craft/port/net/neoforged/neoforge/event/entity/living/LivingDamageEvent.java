package com.stardew.craft.port.net.neoforged.neoforge.event.entity.living;

import com.stardew.craft.port.net.neoforged.neoforge.common.damagesource.DamageContainer;
import java.util.EnumMap;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;

/**
 * PORT(1.20.1): NeoForge 21.1 {@code LivingDamageEvent} (Pre/Post). Forge 1.20.1's class of the same simple name is a
 * single cancelable post-absorption event, so the NeoForge shape lives here. Both events are fired from
 * {@code LivingEntity#actuallyHurt} / {@code Player#actuallyHurt} by the port damage mixins:
 * {@link Pre} after armor, enchantment and mob-effect reductions and before absorption (its new damage feeds the
 * vanilla absorption step), {@link Post} after health was modified, also when the final damage is zero.
 */
public abstract class LivingDamageEvent extends LivingEvent {
    private LivingDamageEvent(LivingEntity entity) {
        super(entity);
    }

    public static class Pre extends LivingDamageEvent {
        private final DamageContainer container;

        public Pre() {
            this(null, null);
        }

        public Pre(LivingEntity entity, DamageContainer container) {
            super(entity);
            this.container = container;
        }

        public DamageContainer getContainer() {
            return container;
        }

        public DamageSource getSource() {
            return container.getSource();
        }

        public float getNewDamage() {
            return container.getNewDamage();
        }

        public float getOriginalDamage() {
            return container.getOriginalDamage();
        }

        public void setNewDamage(float newDamage) {
            container.setNewDamage(newDamage);
        }
    }

    public static class Post extends LivingDamageEvent {
        private final float originalDamage;
        private final DamageSource source;
        private final float newDamage;
        private final float blockedDamage;
        private final float shieldDamage;
        private final int postAttackInvulnerabilityTicks;
        private final EnumMap<DamageContainer.Reduction, Float> reductions = new EnumMap<>(DamageContainer.Reduction.class);

        public Post() {
            super(null);
            this.originalDamage = 0;
            this.source = null;
            this.newDamage = 0;
            this.blockedDamage = 0;
            this.shieldDamage = 0;
            this.postAttackInvulnerabilityTicks = 0;
        }

        public Post(LivingEntity entity, DamageContainer container) {
            super(entity);
            this.originalDamage = container.getOriginalDamage();
            this.source = container.getSource();
            this.newDamage = container.getNewDamage();
            this.blockedDamage = container.getBlockedDamage();
            this.shieldDamage = container.getShieldDamage();
            this.postAttackInvulnerabilityTicks = container.getPostAttackInvulnerabilityTicks();
            for (DamageContainer.Reduction type : DamageContainer.Reduction.values()) {
                this.reductions.put(type, container.getReduction(type));
            }
        }

        public float getOriginalDamage() {
            return originalDamage;
        }

        public DamageSource getSource() {
            return source;
        }

        public float getNewDamage() {
            return newDamage;
        }

        public float getBlockedDamage() {
            return blockedDamage;
        }

        public float getShieldDamage() {
            return shieldDamage;
        }

        public int getPostAttackInvulnerabilityTicks() {
            return postAttackInvulnerabilityTicks;
        }

        public float getReduction(DamageContainer.Reduction reduction) {
            return reductions.getOrDefault(reduction, 0f);
        }
    }
}
