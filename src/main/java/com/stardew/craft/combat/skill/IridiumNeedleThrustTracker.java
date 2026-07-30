package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.network.IridiumNeedleThrustStrikePayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class IridiumNeedleThrustTracker {
    public static final int STRIKE_COUNT = 3;
    public static final int STRIKE_INTERVAL_TICKS = 3;
    public static final double RETARGET_RANGE = 2.5;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private record State(long nextTick, int remainingStrikes, UUID targetId,
                         String weaponId, String skillId, float baseDamageMultiplier) {}

    private IridiumNeedleThrustTracker() {}

    public static void start(ServerPlayer player, long nowTick, LivingEntity target,
                             String weaponId, String skillId, float baseDamageMultiplier) {
        if (player == null || weaponId == null || skillId == null) {
            return;
        }
        UUID targetId = target != null ? target.getUUID() : null;
        ACTIVE.put(
            player.getUUID(),
            new State(
                nowTick,
                STRIKE_COUNT,
                targetId,
                weaponId,
                skillId,
                baseDamageMultiplier
            )
        );
    }

    @SuppressWarnings("null")
    public static void tick(ServerPlayer player, long nowTick) {
        State state = ACTIVE.get(player.getUUID());
        if (state == null || nowTick < state.nextTick) {
            return;
        }

        if (!(player.level() instanceof ServerLevel serverLevel)) {
            ACTIVE.remove(player.getUUID());
            return;
        }

        LivingEntity target = resolveTarget(serverLevel, player, state.targetId);
        if (target == null) {
            ACTIVE.remove(player.getUUID());
            return;
        }

        if (state.remainingStrikes <= 0) {
            ACTIVE.remove(player.getUUID());
            return;
        }

        boolean guaranteedCrit = isGuaranteedCritStrike(state.remainingStrikes);
        strike(player, target, nowTick, state.skillId, state.baseDamageMultiplier, guaranteedCrit);

        int remaining = state.remainingStrikes - 1;
        long nextTick = nextStrikeTick(nowTick);
        ACTIVE.put(player.getUUID(), new State(nextTick, remaining, target.getUUID(),
            state.weaponId, state.skillId, state.baseDamageMultiplier));
    }

    public static boolean isActive(UUID playerId) {
        return playerId != null && ACTIVE.containsKey(playerId);
    }

    static boolean isGuaranteedCritStrike(int remainingStrikes) {
        return remainingStrikes <= 1;
    }

    static long nextStrikeTick(long nowTick) {
        return nowTick + STRIKE_INTERVAL_TICKS;
    }

    @SuppressWarnings("null")
    private static void strike(ServerPlayer player, LivingEntity target, long nowTick,
                               String skillId, float baseDamageMultiplier, boolean guaranteedCrit) {
        target.invulnerableTime = 0;
        target.hurtTime = 0;

        SkillContext context = SkillContext.builder()
            .skillId(skillId)
            .tier(SkillContext.SkillTier.MINOR)
            .damageMultiplier(baseDamageMultiplier)
            .guaranteedCrit(guaranteedCrit)
            .build();
        boolean hit = WeaponSkillDamage.apply(
                player,
                target,
                context,
                nowTick + HIT_CONTEXT_LIFETIME_TICKS
        );

        if (hit) {
            PacketDistributor.sendToPlayer(player, new IridiumNeedleThrustStrikePayload());
        }
    }

    private static LivingEntity resolveTarget(ServerLevel level, ServerPlayer player, UUID targetId) {
        if (targetId != null) {
            Entity entity = level.getEntity(targetId);
            if (entity instanceof LivingEntity living && living.isAlive()) {
                return living;
            }
        }
        return findTargetInFront(player, RETARGET_RANGE);
    }

    @SuppressWarnings("null")
    private static LivingEntity findTargetInFront(ServerPlayer player, double range) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 end = eyePos.add(lookVec.scale(range));
        AABB box = player.getBoundingBox().expandTowards(lookVec.scale(range)).inflate(1.0D);

        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
            player.level(),
            player,
            eyePos,
            end,
            box,
            entity -> entity instanceof LivingEntity && entity.isPickable() && entity != player
        );

        return hit != null ? (LivingEntity) hit.getEntity() : null;
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        ACTIVE.remove(playerId);
    }
}
