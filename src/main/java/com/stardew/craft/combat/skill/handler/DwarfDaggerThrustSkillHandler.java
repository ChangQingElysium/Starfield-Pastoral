package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.PostServerRuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.combat.skill.runtime.WeaponSkillMovementControl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative lifecycle for Dwarf Dagger's original Rune Thrust.
 */
public final class DwarfDaggerThrustSkillHandler
        implements PostServerRuntimeWeaponSkillHandler {
    public static final double THRUST_DISTANCE = 8.0;
    public static final int THRUST_DURATION_TICKS = 5;
    public static final int ANIMATION_TICKS = 8;
    public static final double HIT_RADIUS = 1.2;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int WEAK_POINT_DURATION_TICKS = 100;
    public static final int WEAK_POINT_AMPLIFIER = 3;
    public static final int RESISTANCE_DURATION_TICKS = 50;
    public static final int RESISTANCE_AMPLIFIER = 2;
    public static final float ENERGY_RESTORE = 2.0F;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillMovementControl.isLocked(
                context.player(),
                context.nowTick()
        )) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.INVALID_STATE
            );
        }
        if (WeaponSkillRuntime.hasActive(
                context.player().getUUID(),
                context.skillId()
        )) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.INVALID_STATE
            );
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
        requireMovementUnlocked(context, "Rune Thrust");
        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();

        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );

        DwarfDaggerThrustExecutionState executionState =
                new DwarfDaggerThrustExecutionState(
                context.player(),
                context.nowTick(),
                computeDashEnd(context.player(), THRUST_DISTANCE),
                THRUST_DURATION_TICKS
        );
        instance.initializeExecutionState(executionState);
        instance.registerCommittedEffect(() ->
                executionState.start(
                        context.player(),
                        THRUST_DURATION_TICKS
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
                ANIMATION_TICKS
        );
    }

    private static void requireMovementUnlocked(
            SkillExecutionContext context,
            String skillName
    ) {
        if (WeaponSkillMovementControl.isLocked(
                context.player(),
                context.nowTick()
        )) {
            throw new IllegalStateException(
                    "Validated " + skillName + " movement is now locked"
            );
        }
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
        return instance.requireExecutionState(
                DwarfDaggerThrustExecutionState.class
        ).result(context.nowTick());
    }

    @Override
    public SkillTickResult postServerTick(
            SkillExecutionContext context,
            SkillInstance instance
    ) {
        return instance.requireExecutionState(
                DwarfDaggerThrustExecutionState.class
        ).advance(context);
    }

    /** Settles weak point and the cast bonus from one exact applied hit. */
    public static boolean onAppliedHit(
            ServerPlayer player,
            LivingEntity target,
            long nowTick,
            String weaponId,
            String skillId
    ) {
        if (player == null || target == null) {
            return false;
        }
        return WeaponSkillRuntime.activeExecutionState(
                player.getUUID(),
                BuiltinWeaponSkillHandlers.DWARF_DAGGER_THRUST,
                DwarfDaggerThrustExecutionState.class
        ).map(state -> state.recordAppliedHit(
                player,
                target,
                nowTick,
                weaponId,
                skillId
        )).orElse(false);
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        instance.executionState(DwarfDaggerThrustExecutionState.class)
                .ifPresent(state -> state.finish(context.player()));
    }

    private static Vec3 computeDashEnd(Player player, double distance) {
        Vec3 start = player.position();
        Vec3 look = horizontalLook(player);
        Vec3 end = start.add(look.scale(distance));
        HitResult hit = player.level().clip(new ClipContext(
                start.add(0.0, player.getBbHeight() * 0.5, 0.0),
                end.add(0.0, player.getBbHeight() * 0.5, 0.0),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        if (hit.getType() != HitResult.Type.MISS) {
            end = hit.getLocation().subtract(look.scale(0.4));
        }
        Vec3 safe = findSafePosition(player, end);
        return safe != null ? safe : end;
    }

    private static Vec3 horizontalLook(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        if (horizontal.lengthSqr() < 1.0E-6) {
            double yaw = Math.toRadians(player.getYRot());
            horizontal = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        }
        return horizontal.normalize();
    }

    private static Vec3 findSafePosition(Player player, Vec3 desired) {
        AABB box = player.getBoundingBox().move(
                desired.x - player.getX(),
                desired.y - player.getY(),
                desired.z - player.getZ()
        );
        if (player.level().noCollision(player, box)) {
            return desired;
        }
        Vec3 raised = desired.add(0.0, 0.25, 0.0);
        AABB raisedBox = player.getBoundingBox().move(
                raised.x - player.getX(),
                raised.y - player.getY(),
                raised.z - player.getZ()
        );
        return player.level().noCollision(player, raisedBox) ? raised : null;
    }
}
