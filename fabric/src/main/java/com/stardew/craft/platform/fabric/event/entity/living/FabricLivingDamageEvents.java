package com.stardew.craft.platform.fabric.event.entity.living;

import com.stardew.craft.platform.fabric.event.IEventBus;
import com.stardew.craft.platform.fabric.event.entity.living.damage.DamageContainer;
import com.stardew.craft.platform.fabric.event.entity.living.damage.DamageFrames;
import java.util.Objects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

public final class FabricLivingDamageEvents {
    private static IEventBus installedBus;
    private FabricLivingDamageEvents() {}
    public static synchronized void install(IEventBus bus) {
        Objects.requireNonNull(bus, "bus");
        if (installedBus == bus) return;
        if (installedBus != null) throw new IllegalStateException("Living damage events already have an installed bus");
        installedBus = bus;
    }

    public static DamageFrames frames(LivingEntity entity) {
        if (!(entity instanceof DamageFrames.Access access)) throw new IllegalStateException("Living damage mixin is not installed");
        return access.stardewcraft$damageFrames();
    }

    public static boolean incoming(LivingEntity entity, DamageSource source, float amount) {
        var frame = frames(entity).current();
        frame.container = new DamageContainer(source, amount);
        return bus().post(new LivingIncomingDamageEvent(entity, frame.container)).isCanceled();
    }

    public static float pre(LivingEntity entity) {
        var frame = frames(entity).current();
        bus().post(new LivingDamageEvent.Pre(entity, frame.container));
        frame.prePosted = true;
        return frame.container.getNewDamage();
    }

    public static void post(LivingEntity entity) {
        var frame = frames(entity).currentOrNull();
        if (frame != null && frame.prePosted) bus().post(new LivingDamageEvent.Post(entity, frame.container));
    }

    public static float absorption(LivingEntity entity, float currentAbsorption) {
        var frame = frames(entity).current();
        var result = absorb(frame.container, currentAbsorption);
        frame.absorbed = result.absorbed();
        return result.damage();
    }

    public record Absorption(float damage, float absorbed, float remaining) {}
    public static Absorption absorb(DamageContainer container, float currentAbsorption) {
        float damage = container.getNewDamage();
        container.setReduction(DamageContainer.Reduction.ABSORPTION, Math.min(currentAbsorption, damage));
        float absorbed = Math.min(damage, container.getReduction(DamageContainer.Reduction.ABSORPTION));
        return new Absorption(container.getNewDamage(), absorbed, Math.max(0, currentAbsorption - absorbed));
    }

    public static LivingKnockBackEvent knockback(LivingEntity entity, double strength, double x, double z) {
        return bus().post(new LivingKnockBackEvent(entity, (float) strength, x, z));
    }

    private static IEventBus bus() {
        if (installedBus == null) throw new IllegalStateException("Living damage event bus is not installed");
        return installedBus;
    }
}
