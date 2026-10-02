package com.stardew.craft.port.net.neoforged.neoforge.event.entity.living;

import com.stardew.craft.port.net.neoforged.neoforge.common.damagesource.DamageContainer;
import com.stardew.craft.port.net.neoforged.neoforge.common.damagesource.IReductionFunction;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/**
 * PORT(1.20.1): NeoForge 21.1 {@code LivingIncomingDamageEvent}. Fired by
 * {@code com.stardew.craft.mixin.PortLivingEntityDamageMixin} at the same point as NeoForge: inside
 * {@code LivingEntity#hurt}, after the invulnerable / client / dead / fire-resistance checks and before any damage
 * processing. Cancelling makes {@code hurt} return false; the amount set here is what vanilla processes.
 */
@Cancelable
public class LivingIncomingDamageEvent extends LivingEvent {
    private final DamageContainer container;

    public LivingIncomingDamageEvent() {
        this(null, null);
    }

    public LivingIncomingDamageEvent(LivingEntity entity, DamageContainer container) {
        super(entity);
        this.container = container;
    }

    public DamageContainer getContainer() {
        return this.container;
    }

    public DamageSource getSource() {
        return this.container.getSource();
    }

    public float getAmount() {
        return this.container.getNewDamage();
    }

    public float getOriginalAmount() {
        return this.container.getOriginalDamage();
    }

    public void setAmount(float newDamage) {
        this.container.setNewDamage(newDamage);
    }

    public void addReductionModifier(DamageContainer.Reduction type, IReductionFunction reductionFunc) {
        this.container.addModifier(type, reductionFunc);
    }

    public void setInvulnerabilityTicks(int ticks) {
        this.container.setPostAttackInvulnerabilityTicks(ticks);
    }
}
