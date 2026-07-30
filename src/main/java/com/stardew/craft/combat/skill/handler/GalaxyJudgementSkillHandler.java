package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.StarfallTracker;
import com.stardew.craft.combat.skill.StartrailTracker;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.item.weapon.WeaponSkillData;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative lifecycle for Galaxy Sword's original Galaxy Judgement.
 */
public final class GalaxyJudgementSkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final double MAIN_SLASH_RADIUS = 4.0D;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int ANIMATION_TICKS = 12;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillRuntime.hasActive(
                context.player().getUUID(),
                context.skillId()
        ) || StarfallTracker.hasState(context.player().getUUID())) {
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
        List<LivingEntity> targets = findMainSlashTargets(context);
        instance.setTargetEntityIds(
                targets.stream().map(LivingEntity::getId).toList()
        );

        int consumedStacks =
                StartrailTracker.consumeAll(context.player());
        boolean guaranteedCritical =
                consumedStacks >= StartrailTracker.MAX_STACKS;
        int extraHits = extraHitsForStacks(consumedStacks);

        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        WeaponSkillCooldowns.setCooldown(
                context.player(),
                weaponId,
                skillId,
                context.nowTick(),
                context.skillData().getCooldown() * 20
        );

        SkillContext mainSlashContext = createMainSlashContext(
                context.skillData(),
                guaranteedCritical
        );
        for (LivingEntity target : targets) {
            WeaponSkillDamage.apply(
                    context.player(),
                    target,
                    mainSlashContext,
                    context.weaponSnapshot(),
                    context.nowTick() + HIT_CONTEXT_LIFETIME_TICKS
            );
        }

        StarfallTracker.start(
                context.player(),
                context.nowTick(),
                StarfallTracker.DEFAULT_STRIKES,
                extraHits,
                StarfallTracker.DEFAULT_RADIUS,
                StarfallTracker.DEFAULT_DAMAGE_MULTIPLIER,
                skillId,
                context.weaponSnapshot()
        );

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
        return switch (StarfallTracker.tick(
                context.player(),
                context.nowTick()
        )) {
            case ACTIVE -> SkillTickResult.CONTINUE;
            case COMPLETED -> SkillTickResult.COMPLETE;
            case INVALIDATED -> SkillTickResult.CANCEL;
        };
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        StarfallTracker.cancel(context.player());
    }

    static int extraHitsForStacks(int stacks) {
        return Math.min(
                StarfallTracker.MAX_EXTRA_HITS,
                Math.max(0, stacks) / 4
        );
    }

    static SkillContext createMainSlashContext(
            WeaponSkillData skillData,
            boolean guaranteedCritical
    ) {
        return SkillContext.builder()
                .skillId(skillData.getId())
                .tier(SkillContext.SkillTier.MAJOR)
                .damageMultiplier(
                        skillData.getDamagePercent() / 100.0F
                )
                .guaranteedCrit(guaranteedCritical)
                .build();
    }

    private static List<LivingEntity> findMainSlashTargets(
            SkillExecutionContext context
    ) {
        Vec3 origin = context.player().position();
        AABB box = context.player().getBoundingBox().inflate(
                MAIN_SLASH_RADIUS,
                MAIN_SLASH_RADIUS * 0.75D,
                MAIN_SLASH_RADIUS
        );
        return List.copyOf(
                context.player().level().getEntitiesOfClass(
                        LivingEntity.class,
                        box,
                        entity -> entity.isPickable()
                                && entity != context.player()
                                && entity.distanceToSqr(
                                        origin.x,
                                        origin.y,
                                        origin.z
                                ) <= MAIN_SLASH_RADIUS
                                        * MAIN_SLASH_RADIUS
                )
        );
    }

}
