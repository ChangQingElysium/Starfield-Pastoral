package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.DwarfDaggerThrustTracker;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative lifecycle for Dwarf Dagger's original Rune Thrust.
 */
public final class DwarfDaggerThrustSkillHandler implements RuntimeWeaponSkillHandler {
    public static final double THRUST_DISTANCE = 8.0;
    public static final int THRUST_DURATION_TICKS = 5;
    public static final int ANIMATION_TICKS = 8;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillRuntime.hasActive(
                context.player().getUUID(),
                context.skillId()
        ) || DwarfDaggerThrustTracker.isThrusting(
                context.player(),
                context.nowTick()
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
        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();

        WeaponSkillCooldowns.setCooldown(
                context.player(),
                weaponId,
                skillId,
                context.nowTick(),
                context.skillData().getCooldown() * 20
        );

        DwarfDaggerThrustTracker.start(
                context.player(),
                context.nowTick(),
                computeDashEnd(context.player(), THRUST_DISTANCE),
                THRUST_DURATION_TICKS,
                weaponId,
                skillId,
                context.skillData().getDamagePercent() / 100.0F
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
    public SkillTickResult tick(
            SkillExecutionContext context,
            SkillInstance instance
    ) {
        return DwarfDaggerThrustTracker.isThrusting(
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
        DwarfDaggerThrustTracker.stop(context.player());
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
