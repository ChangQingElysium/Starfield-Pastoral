package com.stardew.craft.combat;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server-authoritative 3D projection of Stardew's facing-based melee area. */
final class StardewWeaponAreaAttack {
    private StardewWeaponAreaAttack() {
    }

    static void applySecondaryTargets(ResolvedWeaponHit root) {
        if (!root.dealtPositiveDamage()
                || !root.primaryTarget()
                || root.sweepTarget()
                || !"normal".equals(root.authoredSkillContext().getSkillId())) {
            return;
        }
        WeaponDamageSnapshot snapshot = root.weaponSnapshot().orElse(null);
        if (snapshot == null) {
            return;
        }

        Player player = root.attacker();
        WeaponType type = WeaponStats.fromItemStack(snapshot.weapon())
                .getWeaponType();
        Shape shape = shape(type);
        Vec3 forward = horizontalForward(player);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        AABB search = player.getBoundingBox().inflate(
                shape.forwardReach(),
                shape.verticalReach(),
                shape.forwardReach()
        );
        List<LivingEntity> targets = player.level().getEntitiesOfClass(
                LivingEntity.class,
                search,
                candidate -> isSecondaryCandidate(
                        player,
                        root.target(),
                        candidate,
                        forward,
                        right,
                        shape
                )
        );
        for (LivingEntity target : targets) {
            WeaponSkillDamage.apply(
                    player,
                    target,
                    SkillContext.normalAttack(),
                    snapshot,
                    root.gameTick() + 2L,
                    WeaponSkillDamage.AttackGatePolicy.SKILL_DAMAGE,
                    WeaponSkillDamage.HitCooldownPolicy.RESPECT_VANILLA
            );
        }
    }

    static boolean contains(
            WeaponType type,
            double forwardDistance,
            double sideDistance,
            double verticalDistance
    ) {
        if (type == WeaponType.SLINGSHOT) {
            return false;
        }
        Shape shape = shape(type);
        return forwardDistance >= shape.rearReach()
                && forwardDistance <= shape.forwardReach()
                && Math.abs(sideDistance) <= shape.halfWidth()
                && Math.abs(verticalDistance) <= shape.verticalReach();
    }

    private static boolean isSecondaryCandidate(
            Player player,
            LivingEntity primary,
            LivingEntity candidate,
            Vec3 forward,
            Vec3 right,
            Shape shape
    ) {
        if (candidate == player
                || candidate == primary
                || !candidate.isAlive()
                || !candidate.isAttackable()
                || player.isAlliedTo(candidate)) {
            return false;
        }
        Vec3 offset = candidate.getBoundingBox().getCenter()
                .subtract(player.getBoundingBox().getCenter());
        return contains(
                shape.type(),
                offset.dot(forward),
                offset.dot(right),
                offset.y
        );
    }

    private static Vec3 horizontalForward(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        return horizontal.lengthSqr() > 1.0E-8D
                ? horizontal.normalize()
                : new Vec3(0.0D, 0.0D, 1.0D);
    }

    private static Shape shape(WeaponType type) {
        return switch (type) {
            case DAGGER -> new Shape(type, -0.15D, 1.55D, 0.55D, 1.25D);
            case SWORD -> new Shape(type, -0.45D, 2.10D, 1.30D, 1.50D);
            case CLUB -> new Shape(type, -0.70D, 2.50D, 1.75D, 1.75D);
            case SLINGSHOT -> new Shape(type, 0.0D, 0.0D, 0.0D, 0.0D);
        };
    }

    private record Shape(
            WeaponType type,
            double rearReach,
            double forwardReach,
            double halfWidth,
            double verticalReach
    ) {
    }
}
