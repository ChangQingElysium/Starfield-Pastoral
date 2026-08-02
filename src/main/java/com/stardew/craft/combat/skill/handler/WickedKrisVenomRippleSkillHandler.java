package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.item.weapon.WeaponSkillData;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
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
        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );
        instance.initializeExecutionState(new State());

        instance.registerCommittedEffect(() -> {
            for (LivingEntity target : targets) {
                WeaponSkillDamage.apply(
                        context.player(),
                        target,
                        createHitContext(context.skillData()),
                        context.weaponSnapshot(),
                        context.nowTick() + HIT_CONTEXT_LIFETIME_TICKS,
                        WeaponSkillDamage.AttackGatePolicy.SKILL_DAMAGE,
                        WeaponSkillDamage.HitCooldownPolicy
                                .BYPASS_FOR_AUTHORED_SEQUENCE
                );
            }
        });

        // The legacy server branch intentionally sent no action/animation packet.
        // Its client-only visual call remains outside this server action contract.
    }

    /** Grants this cast's speed reward once after its first positive hit. */
    public static boolean recordAppliedHit(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        return WeaponSkillRuntime.activeExecutionState(
                player.getUUID(),
                BuiltinWeaponSkillHandlers.WICKED_KRIS_VENOM_RIPPLE,
                State.class
        ).map(state -> state.grantSpeedOnce(player))
                .orElse(false);
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

    private static final class State implements SkillInstance.ExecutionState {
        private boolean speedGranted;

        private boolean grantSpeedOnce(ServerPlayer player) {
            if (speedGranted) {
                return false;
            }
            speedGranted = true;
            player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED,
                    SPEED_DURATION_TICKS,
                    SPEED_AMPLIFIER,
                    false,
                    true,
                    true
            ));
            return true;
        }
    }
}
