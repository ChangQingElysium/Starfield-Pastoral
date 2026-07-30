package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.WickedKrisPoisonTracker;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.item.weapon.WeaponSkillData;
import java.util.List;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative extraction of Wicked Kris's original Venom Ripple.
 */
public final class WickedKrisVenomRippleSkillHandler implements RuntimeWeaponSkillHandler {
    public static final double TARGET_RADIUS = 4.0;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int POISON_DURATION_TICKS = 100;
    public static final int POISON_STACKS = 5;
    public static final boolean SCHEDULE_DETONATION = false;
    public static final int SPEED_DURATION_TICKS = 40;
    public static final int SPEED_AMPLIFIER = 0;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        boolean coolingDown = WeaponSkillCooldowns.isOnCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick()
        );
        if (coolingDown) {
            return SkillValidation.reject(SkillValidation.RejectionReason.COOLDOWN);
        }
        return findTargets(context).isEmpty()
                ? SkillValidation.reject(SkillValidation.RejectionReason.INVALID_STATE)
                : SkillValidation.accept();
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        List<LivingEntity> targets = findTargets(context);
        if (targets.isEmpty()) {
            throw new IllegalStateException(
                    "Validated Venom Ripple targets are no longer available"
            );
        }
        instance.setTargetEntityIds(targets.stream().map(LivingEntity::getId).toList());

        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        WeaponSkillCooldowns.setCooldown(
                context.player(),
                weaponId,
                skillId,
                context.nowTick(),
                context.skillData().getCooldown() * 20
        );

        for (LivingEntity target : targets) {
            target.invulnerableTime = 0;
            target.hurtTime = 0;
            WeaponSkillDamage.apply(
                    context.player(),
                    target,
                    createHitContext(context.skillData()),
                    context.weaponSnapshot(),
                    context.nowTick() + HIT_CONTEXT_LIFETIME_TICKS
            );

            // Authored behavior applies/refreshes poison after the attack attempt,
            // regardless of whether the damage pipeline accepts that hit.
            WickedKrisPoisonTracker.applyPoison(
                    target,
                    context.player(),
                    context.nowTick(),
                    POISON_DURATION_TICKS,
                    POISON_STACKS,
                    SCHEDULE_DETONATION,
                    context.weaponSnapshot()
            );
        }

        context.player().addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SPEED,
                SPEED_DURATION_TICKS,
                SPEED_AMPLIFIER,
                false,
                true,
                true
        ));

        // The legacy server branch intentionally sent no action/animation packet.
        // Its client-only visual call remains outside this server action contract.
    }

    static SkillContext createHitContext(WeaponSkillData skillData) {
        return SkillContext.builder()
                .skillId(skillData.getId())
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(skillData.getDamagePercent() / 100.0F)
                .build();
    }

    private static List<LivingEntity> findTargets(SkillExecutionContext context) {
        Vec3 origin = context.player().position();
        AABB box = context.player().getBoundingBox().inflate(
                TARGET_RADIUS,
                TARGET_RADIUS * 0.75,
                TARGET_RADIUS
        );
        return context.player().level().getEntitiesOfClass(
                LivingEntity.class,
                box,
                entity -> entity.isPickable()
                        && entity != context.player()
                        && entity.distanceToSqr(origin.x, origin.y, origin.z)
                                <= TARGET_RADIUS * TARGET_RADIUS
        );
    }
}
