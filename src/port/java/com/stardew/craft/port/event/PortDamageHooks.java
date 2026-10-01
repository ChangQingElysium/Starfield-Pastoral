package com.stardew.craft.port.event;

import com.stardew.craft.port.net.neoforged.neoforge.common.damagesource.DamageContainer;
import com.stardew.craft.port.net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * PORT(1.20.1): NeoForge 21.1 damage sequence on Forge 1.20.1 (called from {@code PortLivingEntityDamageMixin} and
 * {@code PortPlayerDamageMixin}).
 * <ol>
 * <li>{@code LivingEntity#hurt}: after NeoForge's early checks a {@link DamageContainer} is pushed and
 * {@link LivingIncomingDamageEvent} fires; cancel returns false, otherwise vanilla/Forge {@code hurt} runs with the
 * container's new damage. The post-attack invulnerability (vanilla 20) comes from the container.</li>
 * <li>{@code actuallyHurt}: armor, mob-effect and enchantment reductions are recorded in the container, then
 * {@link LivingDamageEvent.Pre} fires and its new damage replaces the post-reduction amount fed to vanilla's
 * absorption step. Forge's own {@code LivingDamageEvent} still fires afterwards; its result is the health damage.</li>
 * <li>{@link LivingDamageEvent.Post} fires when {@code actuallyHurt} returns, after health was modified, including
 * when the final damage is zero.</li>
 * </ol>
 * Forge's {@code LivingHurtEvent} returns from {@code actuallyHurt} before armor when the amount is not positive;
 * NeoForge has no such early exit, so in that case Pre is fired with zero damage here, a positive Pre result is
 * applied with the vanilla absorption/health logic, and Post is fired.
 */
public final class PortDamageHooks {
    /** Implemented on {@code LivingEntity} by {@code PortLivingEntityDamageMixin}. */
    public interface ContainerHolder {
        Deque<DamageContainer> stardewcraft$damageContainers();
    }

    /** Per-container progress through {@code actuallyHurt}. Only touched on the thread running the hurt call. */
    private static final class Stage {
        boolean preFired;
    }

    private static final Map<DamageContainer, Stage> STAGES = new IdentityHashMap<>();

    @FunctionalInterface
    public interface HurtCall {
        boolean call(float amount);
    }

    private PortDamageHooks() {}

    public static DamageContainer current(LivingEntity entity) {
        return ((ContainerHolder) entity).stardewcraft$damageContainers().peek();
    }

    public static boolean hurt(LivingEntity entity, DamageSource source, float amount, HurtCall original) {
        if (entity.isInvulnerableTo(source)
                || entity.level().isClientSide
                || entity.isDeadOrDying()
                || source.is(DamageTypeTags.IS_FIRE) && entity.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            return original.call(amount);
        }
        Deque<DamageContainer> stack = ((ContainerHolder) entity).stardewcraft$damageContainers();
        DamageContainer container = new DamageContainer(source, amount);
        stack.push(container);
        try {
            if (MinecraftForge.EVENT_BUS.post(new LivingIncomingDamageEvent(entity, container))) {
                return false;
            }
            return original.call(container.getNewDamage());
        } finally {
            stack.removeFirstOccurrence(container);
            synchronized (STAGES) {
                STAGES.remove(container);
            }
        }
    }

    public static int postAttackInvulnerabilityTicks(LivingEntity entity, int vanillaTicks) {
        DamageContainer container = current(entity);
        return container == null ? vanillaTicks : container.getPostAttackInvulnerabilityTicks();
    }

    public static void beginActuallyHurt(LivingEntity entity) {
        DamageContainer container = current(entity);
        if (container == null) return;
        synchronized (STAGES) {
            STAGES.put(container, new Stage());
        }
    }

    private static Stage stage(DamageContainer container) {
        synchronized (STAGES) {
            return STAGES.get(container);
        }
    }

    /** {@code in}/{@code out}: the amount before/after {@code getDamageAfterArmorAbsorb}. */
    public static float afterArmor(LivingEntity entity, float in, float out) {
        DamageContainer container = current(entity);
        if (container == null || stage(container) == null) return out;
        container.setNewDamage(in);
        container.setReduction(DamageContainer.Reduction.ARMOR, in - out);
        return container.getNewDamage();
    }

    /** Records mob-effect and enchantment reductions, then fires {@link LivingDamageEvent.Pre}. */
    public static float afterMagic(LivingEntity entity, DamageSource source, float in, float out) {
        DamageContainer container = current(entity);
        Stage stage = container == null ? null : stage(container);
        if (stage == null) return out;
        container.setNewDamage(in);
        float afterEffects = in;
        if (!source.is(DamageTypeTags.BYPASSES_EFFECTS)) {
            MobEffectInstance resistance = entity.getEffect(MobEffects.DAMAGE_RESISTANCE);
            if (resistance != null && !source.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
                int j = 25 - (resistance.getAmplifier() + 1) * 5;
                afterEffects = Math.max(in * (float) j / 25.0F, 0.0F);
                container.setReduction(DamageContainer.Reduction.MOB_EFFECTS, in - afterEffects);
            }
        }
        if (afterEffects - out != 0.0F) {
            container.setReduction(DamageContainer.Reduction.ENCHANTMENTS, afterEffects - out);
        }
        firePre(entity, container, stage);
        return container.getNewDamage();
    }

    /** Wraps Forge's {@code onLivingDamage}: {@code in} is the post-absorption damage, {@code out} the health damage. */
    public static float afterForgeLivingDamage(LivingEntity entity, float in, float out) {
        DamageContainer container = current(entity);
        if (container == null || stage(container) == null) return out;
        container.recordReduction(DamageContainer.Reduction.ABSORPTION, Math.max(0.0F, container.getNewDamage() - in));
        container.setNewDamage(out);
        return out;
    }

    public static void endActuallyHurt(LivingEntity entity, DamageSource source) {
        DamageContainer container = current(entity);
        if (container == null) return;
        Stage stage;
        synchronized (STAGES) {
            stage = STAGES.remove(container);
        }
        if (stage == null) return;
        if (!stage.preFired) {
            if (entity.isInvulnerableTo(source)) return;
            // Forge's LivingHurtEvent path returned before armor: NeoForge would still run Pre and Post.
            if (container.getNewDamage() > 0.0F) container.setNewDamage(0.0F);
            firePre(entity, container, stage);
            applyLateDamage(entity, source, container);
        }
        MinecraftForge.EVENT_BUS.post(new LivingDamageEvent.Post(entity, container));
    }

    private static void firePre(LivingEntity entity, DamageContainer container, Stage stage) {
        stage.preFired = true;
        MinecraftForge.EVENT_BUS.post(new LivingDamageEvent.Pre(entity, container));
    }

    /** Vanilla absorption + health application for a positive Pre result on Forge's early-return path. */
    private static void applyLateDamage(LivingEntity entity, DamageSource source, DamageContainer container) {
        float damage = container.getNewDamage();
        if (damage <= 0.0F) {
            container.setNewDamage(0.0F);
            return;
        }
        float absorbed = Math.min(entity.getAbsorptionAmount(), damage);
        entity.setAbsorptionAmount(Math.max(0.0F, entity.getAbsorptionAmount() - absorbed));
        container.recordReduction(DamageContainer.Reduction.ABSORPTION, absorbed);
        float health = damage - absorbed;
        container.setNewDamage(health);
        if (health == 0.0F) return;
        if (entity instanceof Player player) player.causeFoodExhaustion(source.getFoodExhaustion());
        entity.getCombatTracker().recordDamage(source, health);
        entity.setHealth(entity.getHealth() - health);
        if (entity instanceof Player player && health < 3.4028235E37F) {
            player.awardStat(Stats.DAMAGE_TAKEN, Math.round(health * 10.0F));
        }
        entity.gameEvent(GameEvent.ENTITY_DAMAGE);
    }
}
