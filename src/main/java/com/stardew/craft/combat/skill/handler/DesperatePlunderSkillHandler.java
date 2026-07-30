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
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.item.weapon.WeaponSkillData;
import java.util.List;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-authoritative extraction of Pirate's Sword's original Desperate Plunder.
 */
public final class DesperatePlunderSkillHandler implements RuntimeWeaponSkillHandler {
    public static final double TARGET_RANGE = 4.0;
    public static final float HEALTH_COST = 2.0F;
    public static final float MINIMUM_REMAINING_HEALTH = 0.5F;
    public static final float KILL_HEALING = 4.0F;
    public static final int FURY_DURATION_TICKS = 60;
    public static final int FURY_AMPLIFIER = 0;
    public static final int ANIMATION_TICKS = 8;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
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

        LivingEntity target = SkillTargeting.findTargetEntity(
                context.player(),
                TARGET_RANGE
        );
        if (target != null) {
            instance.setTargetEntityIds(List.of(target.getId()));
        }

        context.player().setHealth(healthAfterCost(context.player().getHealth()));

        if (target == null) {
            grantFury(context);
        } else {
            WeaponSkillDamage.apply(
                    context.player(),
                    target,
                    createHitContext(context.skillData()),
                    context.weaponSnapshot(),
                    context.nowTick() + HIT_CONTEXT_LIFETIME_TICKS
            );
            if (target.isDeadOrDying() || target.getHealth() <= 0.0F) {
                healAfterKill(context);
            } else {
                grantFury(context);
            }
        }

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

    static SkillContext createHitContext(WeaponSkillData skillData) {
        return SkillContext.builder()
                .skillId(skillData.getId())
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(skillData.getDamagePercent() / 100.0F)
                .build();
    }

    static float healthAfterCost(float currentHealth) {
        return currentHealth > HEALTH_COST + MINIMUM_REMAINING_HEALTH
                ? currentHealth - HEALTH_COST
                : MINIMUM_REMAINING_HEALTH;
    }

    static float healthAfterKillHealing(float currentHealth, float maximumHealth) {
        return Math.min(currentHealth + KILL_HEALING, maximumHealth);
    }

    private static void healAfterKill(SkillExecutionContext context) {
        context.player().setHealth(healthAfterKillHealing(
                context.player().getHealth(),
                context.player().getMaxHealth()
        ));
        context.player().level().playSound(
                null,
                context.player().getX(),
                context.player().getY(),
                context.player().getZ(),
                SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS,
                0.5F,
                1.2F
        );
    }

    private static void grantFury(SkillExecutionContext context) {
        context.player().addEffect(new MobEffectInstance(
                ModMobEffects.FURY,
                FURY_DURATION_TICKS,
                FURY_AMPLIFIER,
                false,
                true,
                true
        ));
    }
}
