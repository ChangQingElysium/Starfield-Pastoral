package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTargeting;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.item.weapon.WeaponSkillData;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-authoritative lifecycle for Dwarf Sword's original Rune Guard Slash.
 */
public final class DwarfRuneGuardSkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final double TARGET_RANGE = 4.5D;
    public static final int SHELTER_DURATION_TICKS = 50;
    public static final int SHELTER_AMPLIFIER = 1;
    public static final int SLOW_DURATION_TICKS = 40;
    public static final int SLOW_AMPLIFIER = 0;
    public static final float HIT_ENERGY_RESTORE = 6.0F;
    public static final float MISS_ENERGY_RESTORE = 3.0F;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int ANIMATION_TICKS = 8;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillRuntime.hasActive(
                context.player().getUUID(),
                context.skillId()
        )) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.INVALID_STATE
            );
        }
        return WeaponSkillCooldowns.isOnCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick()
        )
                ? SkillValidation.reject(
                        SkillValidation.RejectionReason.COOLDOWN
                )
                : SkillValidation.accept();
    }

    @Override
    public void begin(
            SkillExecutionContext context,
            SkillInstance instance
    ) {
        LivingEntity target = SkillTargeting.findTargetEntity(
                context.player(),
                TARGET_RANGE
        );
        if (target != null) {
            instance.setTargetEntityIds(List.of(target.getId()));
        }

        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );
        instance.initializeExecutionState(
                new DwarfRuneGuardExecutionState(
                        target == null ? null : target.getUUID()
                )
        );

        instance.registerCommittedEffect(() -> {
            DwarfRuneGuardExecutionState state =
                    instance.requireExecutionState(
                            DwarfRuneGuardExecutionState.class
                    );
            context.player().addEffect(new MobEffectInstance(
                    ModMobEffects.SHELTER,
                    SHELTER_DURATION_TICKS,
                    SHELTER_AMPLIFIER,
                    false,
                    true,
                    true
            ));
            if (target != null) {
                WeaponSkillDamage.apply(
                        context.player(),
                        target,
                        createHitContext(context.skillData()),
                        context.weaponSnapshot(),
                        context.nowTick() + HIT_CONTEXT_LIFETIME_TICKS,
                        WeaponSkillDamage.AttackGatePolicy
                                .RESPECT_AT_IMPACT,
                        WeaponSkillDamage.HitCooldownPolicy.RESPECT_VANILLA
                );
            }
            state.settleMiss(context.player());
        });

        // Preserve the authored notification and action-lock order.
        WeaponSkillAnimationDispatcher.sendSkillAnim(
                context.player(),
                weaponId,
                skillId,
                ANIMATION_TICKS
        );
        WeaponSkillAnimationLock.setLock(
                context.player(),
                context.nowTick(),
                ANIMATION_TICKS
        );
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
        return isGuardWindowComplete(
                instance.startGameTick(),
                context.nowTick()
        )
                ? SkillTickResult.COMPLETE
                : SkillTickResult.CONTINUE;
    }

    /** Records this execution's exact positive strike result once. */
    public static boolean onAppliedHit(
            ServerPlayer player,
            LivingEntity target
    ) {
        if (player == null || target == null) {
            return false;
        }
        return WeaponSkillRuntime.activeExecutionState(
                player.getUUID(),
                BuiltinWeaponSkillHandlers.DWARF_RUNE_GUARD,
                DwarfRuneGuardExecutionState.class
        ).map(state -> state.onAppliedHit(player, target))
                .orElse(false);
    }

    static boolean isGuardWindowComplete(long startTick, long nowTick) {
        return nowTick >= startTick + SHELTER_DURATION_TICKS;
    }

    static SkillContext createHitContext(WeaponSkillData skillData) {
        return SkillContext.builder()
                .skillId(skillData.getId())
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(
                        skillData.getDamagePercent() / 100.0F
                )
                .build();
    }
}
