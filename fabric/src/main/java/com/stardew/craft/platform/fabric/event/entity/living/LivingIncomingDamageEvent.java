package com.stardew.craft.platform.fabric.event.entity.living;

import com.stardew.craft.platform.fabric.event.entity.living.damage.DamageContainer;
import com.stardew.craft.platform.fabric.event.entity.living.damage.IReductionFunction;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** After native immunity checks, before shield handling, armor, effects, and absorption. */
public final class LivingIncomingDamageEvent extends LivingEvent {
    private final DamageContainer container;
    public LivingIncomingDamageEvent(LivingEntity entity, DamageContainer container) {
        super(entity, true);
        this.container = container;
    }
    public DamageContainer getContainer() { return container; }
    public DamageSource getSource() { return container.getSource(); }
    public float getAmount() { return container.getNewDamage(); }
    public float getOriginalAmount() { return container.getOriginalDamage(); }
    public void setAmount(float amount) { container.setNewDamage(amount); }
    public void addReductionModifier(DamageContainer.Reduction type, IReductionFunction modifier) { container.addModifier(type, modifier); }
    public void setInvulnerabilityTicks(int ticks) { container.setPostAttackInvulnerabilityTicks(ticks); }
}
