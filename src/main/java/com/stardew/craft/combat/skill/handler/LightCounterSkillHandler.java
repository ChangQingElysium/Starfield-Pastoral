package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.LightCounterParryState;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Server-authoritative lifecycle for Steel Smallsword's original Light Counter.
 *
 * <p>The incoming-damage event remains responsible for consuming the parry and
 * performing the counterattack. This handler owns activation, cooldown, window
 * expiry, animation lock and cancellation cleanup.</p>
 */
public final class LightCounterSkillHandler implements RuntimeWeaponSkillHandler {
    public static final int WINDOW_TICKS = LightCounterParryState.DEFAULT_WINDOW_TICKS;
    public static final int INITIAL_RESISTANCE_TICKS = 7;
    public static final int INITIAL_RESISTANCE_AMPLIFIER = 0;
    public static final int ANIMATION_TICKS = WINDOW_TICKS;

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

        WeaponSkillCooldowns.setCooldown(
                context.player(),
                weaponId,
                skillId,
                context.nowTick(),
                context.skillData().getCooldown() * 20
        );
        context.player().addEffect(new MobEffectInstance(
                MobEffects.DAMAGE_RESISTANCE,
                INITIAL_RESISTANCE_TICKS,
                INITIAL_RESISTANCE_AMPLIFIER,
                false,
                false,
                true
        ));
        LightCounterParryState.start(
                context.player(),
                context.nowTick(),
                WINDOW_TICKS,
                weaponId,
                context.weaponSnapshot()
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
                ANIMATION_TICKS
        );
    }

    @Override
    public boolean completesImmediately() {
        return false;
    }

    @Override
    public SkillTickResult tick(SkillExecutionContext context, SkillInstance instance) {
        return LightCounterParryState.isActive(context.player(), context.nowTick())
                ? SkillTickResult.CONTINUE
                : SkillTickResult.COMPLETE;
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        String activeWeaponId = LightCounterParryState.getWeaponId(context.player());
        if (context.weaponId().getPath().equals(activeWeaponId)) {
            LightCounterParryState.clear(context.player());
        }
    }
}
