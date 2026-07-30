package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.IridiumNeedleFrenzyTracker;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.player.PlayerStardewDataAPI;
import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Server-authoritative lifecycle for Iridium Needle's original Iridium Frenzy.
 */
public final class IridiumNeedleFrenzySkillHandler implements RuntimeWeaponSkillHandler {
    public static final float ENERGY_COST = 10.0F;
    public static final int DURATION_TICKS = 120;
    public static final int SPEED_AMPLIFIER = 0;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillCooldowns.isOnCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick()
        )) {
            return SkillValidation.reject(SkillValidation.RejectionReason.COOLDOWN);
        }
        if (WeaponSkillRuntime.hasActive(
                context.player().getUUID(),
                context.skillId()
        ) || IridiumNeedleFrenzyTracker.isActive(
                context.player(),
                context.nowTick()
        )) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.INVALID_STATE
            );
        }
        return canPayEnergy(context)
                ? SkillValidation.accept()
                : SkillValidation.reject(
                        SkillValidation.RejectionReason.INVALID_STATE
                );
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        if (!canPayEnergy(context)) {
            throw new IllegalStateException(
                    "Validated Iridium Frenzy energy is no longer available"
            );
        }
        MobEffect speedEffect = Objects.requireNonNull(
                ModMobEffects.SPEED.get(),
                "Iridium Frenzy speed effect"
        );
        Holder<MobEffect> speed = Holder.direct(speedEffect);

        if (!context.player().getAbilities().instabuild
                && !PlayerStardewDataAPI.consumeEnergy(
                        context.player(),
                        ENERGY_COST
                )) {
            throw new IllegalStateException(
                    "Validated Iridium Frenzy energy payment failed"
            );
        }

        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        WeaponSkillCooldowns.setCooldown(
                context.player(),
                weaponId,
                skillId,
                context.nowTick(),
                context.skillData().getCooldown() * 20
        );
        IridiumNeedleFrenzyTracker.start(
                context.player(),
                context.nowTick(),
                DURATION_TICKS
        );

        context.player().addEffect(new MobEffectInstance(
                speed,
                DURATION_TICKS,
                SPEED_AMPLIFIER,
                false,
                true,
                true
        ));
    }

    @Override
    public boolean completesImmediately() {
        return false;
    }

    @Override
    public SkillTickResult tick(
            SkillExecutionContext context,
            SkillInstance instance
    ) {
        return IridiumNeedleFrenzyTracker.isActive(
                context.player(),
                context.nowTick()
        )
                ? SkillTickResult.CONTINUE
                : SkillTickResult.COMPLETE;
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        if (!shouldNotifyOnFinish(reason)) {
            IridiumNeedleFrenzyTracker.removePlayer(
                    context.player().getUUID()
            );
            return;
        }
        IridiumNeedleFrenzyTracker.clear(context.player());
    }

    static boolean shouldNotifyOnFinish(SkillInstance.EndReason reason) {
        return reason != SkillInstance.EndReason.CASTER_UNAVAILABLE;
    }

    static boolean canPayEnergy(
            float currentEnergy,
            boolean creativeMode,
            boolean freeEnergyBlessing
    ) {
        return creativeMode
                || freeEnergyBlessing
                || currentEnergy >= ENERGY_COST;
    }

    private static boolean canPayEnergy(SkillExecutionContext context) {
        return canPayEnergy(
                PlayerStardewDataAPI.getEnergy(context.player()),
                context.player().getAbilities().instabuild,
                context.player().hasEffect(
                        ModMobEffects.STATUE_OF_BLESSINGS_2
                )
        );
    }
}
