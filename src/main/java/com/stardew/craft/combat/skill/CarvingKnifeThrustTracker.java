package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.DamageNumberContextStore;
import com.stardew.craft.combat.network.CarvingKnifeThrustStrikePayload;
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

public final class CarvingKnifeThrustTracker {

    public static final float BASE_DAMAGE_MULTIPLIER = 0.45f;
    public static final float BONUS_DAMAGE_MULTIPLIER = 0.60f;
    public static final int DEFAULT_STRIKES = 3;
    public static final int DEFAULT_INTERVAL_TICKS = 3;
    public static final int BONUS_DELAY_TICKS = 2;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final double REACQUIRE_RANGE = 2.5;

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private record State(long nextTick, int remainingStrikes, boolean bonusPending, boolean bonusDone,
                         UUID targetId, String weaponId, String skillId) {}

    private CarvingKnifeThrustTracker() {}

    public static void start(ServerPlayer player, long nowTick, LivingEntity target,
                             String weaponId, String skillId) {
        start(player, nowTick, target, weaponId, skillId, DEFAULT_STRIKES, DEFAULT_INTERVAL_TICKS);
    }

    public static void start(ServerPlayer player, long nowTick, LivingEntity target,
                             String weaponId, String skillId, int strikes, int intervalTicks) {
        if (player == null || weaponId == null || skillId == null || strikes <= 0) {
            return;
        }
        UUID targetId = target != null ? target.getUUID() : null;
        ACTIVE.put(player.getUUID(), new State(nowTick, strikes, false, false, targetId, weaponId, skillId));
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
            if (!state.bonusDone && state.bonusPending) {
                strike(player, target, nowTick, state.skillId, BONUS_DAMAGE_MULTIPLIER);
                ACTIVE.put(player.getUUID(), new State(nowTick + BONUS_DELAY_TICKS, 0, false, true,
                    target.getUUID(), state.weaponId, state.skillId));
                return;
            }
            ACTIVE.remove(player.getUUID());
            return;
        }

        boolean crit = strike(player, target, nowTick, state.skillId, BASE_DAMAGE_MULTIPLIER);
        boolean bonusPending = state.bonusPending || crit;
        int remaining = state.remainingStrikes - 1;

        long nextTick = nowTick + DEFAULT_INTERVAL_TICKS;
        if (remaining <= 0 && bonusPending) {
            nextTick = nowTick + BONUS_DELAY_TICKS;
        }

        ACTIVE.put(player.getUUID(), new State(nextTick, remaining, bonusPending, state.bonusDone,
            target.getUUID(), state.weaponId, state.skillId));
    }

    private static boolean strike(ServerPlayer player, LivingEntity target, long nowTick,
                                  String skillId, float damageMultiplier) {
        target.invulnerableTime = 0;
        target.hurtTime = 0;

        SkillContext context = SkillContext.builder()
            .skillId(skillId)
            .tier(SkillContext.SkillTier.MINOR)
            .damageMultiplier(damageMultiplier)
            .build();
        @SuppressWarnings("null")
        boolean hit = WeaponSkillDamage.apply(
                player,
                target,
                context,
                nowTick + HIT_CONTEXT_LIFETIME_TICKS
        );
        if (hit) {
            PacketDistributor.sendToPlayer(player, new CarvingKnifeThrustStrikePayload());
        }

        DamageNumberContextStore.Meta meta = DamageNumberContextStore.peek(player, nowTick);
        return meta != null && meta.crit() && skillId.equals(meta.skillId());
    }

    private static LivingEntity resolveTarget(ServerLevel level, ServerPlayer player, UUID targetId) {
        if (targetId != null) {
            Entity entity = level.getEntity(targetId);
            if (entity instanceof LivingEntity living && living.isAlive()) {
                return living;
            }
        }
        return findTargetInFront(player, REACQUIRE_RANGE);
    }

    @SuppressWarnings("null")
    private static LivingEntity findTargetInFront(ServerPlayer player, double range) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        @SuppressWarnings("null")
        Vec3 end = eyePos.add(lookVec.scale(range));
        @SuppressWarnings("null")
        AABB box = player.getBoundingBox().expandTowards(lookVec.scale(range)).inflate(1.0D);

        @SuppressWarnings("null")
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

    public static boolean isActive(UUID playerId) {
        return ACTIVE.containsKey(playerId);
    }
}
