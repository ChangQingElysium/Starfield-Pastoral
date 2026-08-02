package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTargeting;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.item.weapon.WeaponSkillData;
import java.util.List;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-authoritative extraction of Wooden Blade's original Tree Blessing.
 */
public final class TreeBlessingSkillHandler implements RuntimeWeaponSkillHandler {
    public static final double TARGET_RANGE = 4.0;
    public static final int SHELTER_DURATION_TICKS = 40;
    public static final int SHELTER_AMPLIFIER = 1;
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

        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );

        LivingEntity target = SkillTargeting.findTargetEntity(
                context.player(),
                TARGET_RANGE
        );
        if (target != null) {
            instance.setTargetEntityIds(List.of(target.getId()));
        }

        instance.registerCommittedEffect(() -> {
            context.player().addEffect(new MobEffectInstance(
                    ModMobEffects.SHELTER,
                    SHELTER_DURATION_TICKS,
                    SHELTER_AMPLIFIER,
                    false,
                    false,
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
        });

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

}
