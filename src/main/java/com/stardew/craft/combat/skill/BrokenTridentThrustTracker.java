package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.network.BrokenTridentThrustStrikePayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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

public final class BrokenTridentThrustTracker {

    public static final int DEFAULT_STRIKES = 3;
    public static final int DEFAULT_INTERVAL_TICKS = 3;
    public static final int FISH_CATCH_DURATION_TICKS = 100;
    public static final float FISH_CATCH_DAMAGE_BONUS = 0.10f;
    public static final int FISH_CATCH_SLOW_AMPLIFIER = 0;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final double REACQUIRE_RANGE = 2.5;

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private record State(long nextTick, int remainingStrikes, UUID targetId,
                         String weaponId, String skillId, float baseDamageMultiplier) {}

    private BrokenTridentThrustTracker() {}

    public static void start(ServerPlayer player, long nowTick, LivingEntity target,
                             String weaponId, String skillId, float baseDamageMultiplier,
                             int strikes, int intervalTicks) {
        if (player == null || weaponId == null || skillId == null || strikes <= 0) {
            return;
        }
        UUID targetId = target != null ? target.getUUID() : null;
        ACTIVE.put(player.getUUID(), new State(nowTick, strikes, targetId, weaponId, skillId, baseDamageMultiplier));
    }

    public static void start(ServerPlayer player, long nowTick, LivingEntity target,
                             String weaponId, String skillId, float baseDamageMultiplier) {
        start(
            player,
            nowTick,
            target,
            weaponId,
            skillId,
            baseDamageMultiplier,
            DEFAULT_STRIKES,
            DEFAULT_INTERVAL_TICKS
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

        strike(player, target, nowTick, state.skillId, state.baseDamageMultiplier);

        int remaining = state.remainingStrikes - 1;
        long nextTick = nowTick + DEFAULT_INTERVAL_TICKS;
        ACTIVE.put(player.getUUID(), new State(nextTick, remaining, target.getUUID(),
            state.weaponId, state.skillId, state.baseDamageMultiplier));
    }

    @SuppressWarnings("null")
    private static void strike(ServerPlayer player, LivingEntity target, long nowTick,
                               String skillId, float baseDamageMultiplier) {
        target.invulnerableTime = 0;
        target.hurtTime = 0;

        boolean fishCatchActive = BrokenTridentCatchTracker.isActive(player, nowTick);
        float damageMultiplier = damageMultiplier(baseDamageMultiplier, fishCatchActive);

        SkillContext context = SkillContext.builder()
            .skillId(skillId)
            .tier(SkillContext.SkillTier.MINOR)
            .damageMultiplier(damageMultiplier)
            .build();
        boolean hit = WeaponSkillDamage.apply(
                player,
                target,
                context,
                nowTick + HIT_CONTEXT_LIFETIME_TICKS
        );

        if (hit) {
            PacketDistributor.sendToPlayer(player, new BrokenTridentThrustStrikePayload());
            if (!fishCatchActive && BrokenTridentCatchTracker.hasFishInInventory(player)) {
                BrokenTridentCatchTracker.start(player, nowTick, FISH_CATCH_DURATION_TICKS);
                applyFishCatchSlow(target);
            } else if (fishCatchActive) {
                applyFishCatchSlow(target);
            }
        }
    }

    @SuppressWarnings("null")
    private static void applyFishCatchSlow(LivingEntity target) {
        target.addEffect(new MobEffectInstance(
            MobEffects.MOVEMENT_SLOWDOWN,
            FISH_CATCH_DURATION_TICKS,
            FISH_CATCH_SLOW_AMPLIFIER,
            false,
            true,
            true
        ));
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

    public static boolean isActive(UUID playerId) {
        return ACTIVE.containsKey(playerId);
    }

    static float damageMultiplier(float baseDamageMultiplier, boolean fishCatchActive) {
        return baseDamageMultiplier
            + (fishCatchActive ? FISH_CATCH_DAMAGE_BONUS : 0.0f);
    }
}
