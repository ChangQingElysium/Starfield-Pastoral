package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.network.ForestBlessingPayload;
import com.stardew.craft.combat.CombatHealing;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTargeting;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Behavior-preserving extraction of the original Forest Blessing implementation.
 */
public final class ForestBlessingSkillHandler implements RuntimeWeaponSkillHandler {
    public static final double TARGET_RANGE = 4.0;
    public static final int DURATION_TICKS = 80;
    public static final int HEAL_INTERVAL_TICKS = 10;
    public static final int HEAL_WITH_TARGET = 2;
    public static final int HEAL_WITHOUT_TARGET = 1;
    public static final int ANIMATION_TICKS = 8;
    public static final int ACTIVE_TICK_OFFSET = 3;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;

    private final Map<UUID, State> states = new HashMap<>();

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillRuntime.hasActive(context.player().getUUID(), context.skillId())) {
            return SkillValidation.reject(SkillValidation.RejectionReason.INVALID_STATE);
        }
        boolean coolingDown = WeaponSkillCooldowns.isOnCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick()
        );
        return coolingDown
                ? SkillValidation.reject(SkillValidation.RejectionReason.COOLDOWN)
                : SkillValidation.accept();
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();

        states.put(
                instance.instanceId(),
                new State(
                        context.nowTick() + ACTIVE_TICK_OFFSET,
                        context.skillData().getCooldown() * 20
                )
        );
        WeaponSkillAnimationLock.setLock(
                context.player(),
                context.nowTick(),
                ANIMATION_TICKS
        );
        WeaponSkillAnimationDispatcher.sendSkillAnim(
                context.player(),
                weaponId,
                skillId,
                ANIMATION_TICKS,
                ACTIVE_TICK_OFFSET + DURATION_TICKS,
                ACTIVE_TICK_OFFSET
        );
    }

    @Override
    public boolean completesImmediately() {
        return false;
    }

    @Override
    public SkillTickResult tick(SkillExecutionContext context, SkillInstance instance) {
        State state = states.get(instance.instanceId());
        if (state == null) {
            return SkillTickResult.CANCEL;
        }
        if (!state.activated && context.nowTick() >= state.activationTick) {
            activateBlessing(context, instance, state);
        }
        if (!state.activated) {
            return SkillTickResult.CONTINUE;
        }
        while (context.nowTick() >= state.nextHealTick
                && state.nextHealTick <= state.endTick) {
            heal(context, state.healAmount);
            state.nextHealTick += HEAL_INTERVAL_TICKS;
        }
        return context.nowTick() >= state.endTick
                ? SkillTickResult.COMPLETE
                : SkillTickResult.CONTINUE;
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        State state = states.remove(instance.instanceId());
        if (state != null
                && shouldCommitCooldown(reason, state.activated)
                && state.cooldownTicks > 0) {
            WeaponSkillCooldowns.setCooldown(
                    context.player(),
                    context.weaponId().getPath(),
                    context.skillData().getId(),
                    context.player().level().getGameTime(),
                    state.cooldownTicks
            );
        }
        if (state != null && state.activated
                && reason != SkillInstance.EndReason.CASTER_UNAVAILABLE) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                    context.player(),
                    new ForestBlessingPayload(
                            context.player().getId(),
                            false,
                            0,
                            reason == SkillInstance.EndReason.COMPLETED
                    )
            );
        }
    }

    private static void activateBlessing(
            SkillExecutionContext context,
            SkillInstance instance,
            State state
    ) {
        state.activated = true;
        state.endTick = context.nowTick() + DURATION_TICKS;
        state.nextHealTick = context.nowTick() + HEAL_INTERVAL_TICKS;

        LivingEntity target = SkillTargeting.findTargetEntity(context.player(), TARGET_RANGE);
        state.healAmount = target == null ? HEAL_WITHOUT_TARGET : HEAL_WITH_TARGET;
        if (target != null) {
            instance.setTargetEntityIds(java.util.List.of(target.getId()));
            SkillContext hitContext = SkillContext.builder()
                    .skillId(context.skillData().getId())
                    .tier(SkillContext.SkillTier.MINOR)
                    .damageMultiplier(context.skillData().getDamagePercent() / 100.0f)
                    .build();
            WeaponSkillDamage.apply(
                    context.player(),
                    target,
                    hitContext,
                    context.weaponSnapshot(),
                    context.nowTick() + HIT_CONTEXT_LIFETIME_TICKS,
                    WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT
            );
            WeaponSkillAnimationDispatcher.sendImpact(
                    context.player(),
                    context.skillData().getId(),
                    java.util.List.of(target.getId()),
                    instance.seed()
            );
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                context.player(),
                new ForestBlessingPayload(
                        context.player().getId(),
                        true,
                        DURATION_TICKS,
                        false
                )
        );
    }

    static boolean shouldCommitCooldown(
            SkillInstance.EndReason reason,
            boolean activated
    ) {
        return activated || reason == SkillInstance.EndReason.COMPLETED;
    }

    private static void heal(SkillExecutionContext context, int amount) {
        CombatHealing.heal(context.player(), amount);
    }

    private static final class State {
        private final long activationTick;
        private long endTick;
        private long nextHealTick;
        private int healAmount;
        private final int cooldownTicks;
        private boolean activated;

        private State(long activationTick, int cooldownTicks) {
            this.activationTick = activationTick;
            this.cooldownTicks = cooldownTicks;
        }
    }
}
