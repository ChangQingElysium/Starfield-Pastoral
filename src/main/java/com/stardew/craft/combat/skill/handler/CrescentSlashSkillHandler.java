package com.stardew.craft.combat.skill.handler;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;

/**
 * Behavior-preserving extraction of the original Crescent Slash implementation.
 */
public final class CrescentSlashSkillHandler implements RuntimeWeaponSkillHandler {
    public static final double TARGET_RANGE = 4.5;
    public static final double MINIMUM_DIRECTION_DOT = 0.2;
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
        int cooldownTicks = context.skillData().getCooldown() * 20;

        WeaponSkillCooldowns.setCooldown(
                context.player(),
                weaponId,
                skillId,
                context.nowTick(),
                cooldownTicks
        );

        states.put(
                instance.instanceId(),
                new State(
                        context.nowTick() + ACTIVE_TICK_OFFSET,
                        context.nowTick() + ANIMATION_TICKS
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
                ANIMATION_TICKS,
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
        if (!state.struck && context.nowTick() >= state.hitTick) {
            strike(context, instance);
            state.struck = true;
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
        states.remove(instance.instanceId());
    }

    private static void strike(SkillExecutionContext context, SkillInstance instance) {
        List<LivingEntity> targets = SkillTargeting.findTargetsInArc(
                context.player(),
                TARGET_RANGE,
                MINIMUM_DIRECTION_DOT
        );
        List<Integer> targetIds = targets.stream().map(LivingEntity::getId).toList();
        instance.setTargetEntityIds(targetIds);
        SkillContext hitContext = SkillContext.builder()
                .skillId(context.skillData().getId())
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(
                        context.skillData().getDamagePercent() / 100.0f
                )
                .build();
        for (LivingEntity target : targets) {
            WeaponSkillDamage.apply(
                    context.player(),
                    target,
                    hitContext,
                    context.weaponSnapshot(),
                    context.nowTick() + HIT_CONTEXT_LIFETIME_TICKS,
                    WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT
            );
        }
        WeaponSkillAnimationDispatcher.sendImpact(
                context.player(),
                context.skillData().getId(),
                targetIds,
                instance.seed()
        );
    }

    private static final class State {
        private final long hitTick;
        private final long endTick;
        private boolean struck;

        private State(long hitTick, long endTick) {
            this.hitTick = hitTick;
            this.endTick = endTick;
        }
    }
}
