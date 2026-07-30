package com.stardew.craft.combat.skill;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.DwarfDaggerThrustPayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.player.PlayerStardewDataAPI;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class DwarfDaggerThrustTracker {
    public static final double HIT_RADIUS = 1.2;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int WEAK_POINT_DURATION_TICKS = 100;
    public static final int WEAK_POINT_AMPLIFIER = 3;
    public static final int RESISTANCE_DURATION_TICKS = 50;
    public static final int RESISTANCE_AMPLIFIER = 2;
    public static final float ENERGY_RESTORE = 2.0F;

    private static final class State {
        private final UUID playerId;
        private final ResourceKey<Level> dimension;
        private final Vec3 end;
        private final Vec3 step;
        private final long endTick;
        private final String weaponId;
        private final String skillId;
        private final float damageMultiplier;
        private Vec3 lastPos;
        private final Set<UUID> hitTargets = new HashSet<>();
        private boolean bonusApplied = false;

        private State(UUID playerId, ResourceKey<Level> dimension, Vec3 start, Vec3 end, Vec3 step,
                      long endTick, String weaponId, String skillId, float damageMultiplier) {
            this.playerId = playerId;
            this.dimension = dimension;
            this.end = end;
            this.step = step;
            this.endTick = endTick;
            this.weaponId = weaponId;
            this.skillId = skillId;
            this.damageMultiplier = damageMultiplier;
            this.lastPos = start;
        }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private DwarfDaggerThrustTracker() {}

    @SuppressWarnings("null")
    public static void start(ServerPlayer player, long nowTick, Vec3 end, int durationTicks,
                             String weaponId, String skillId, float damageMultiplier) {
        if (player == null || end == null || durationTicks <= 0 || weaponId == null || skillId == null) {
            return;
        }
        Vec3 start = player.position();
        Vec3 diff = end.subtract(start);
        Vec3 step = new Vec3(diff.x / durationTicks, 0.0, diff.z / durationTicks);
        ACTIVE.put(player.getUUID(), new State(player.getUUID(), player.level().dimension(), start, end, step,
            nowTick + durationTicks, weaponId, skillId, damageMultiplier));
        sendClientState(player, true, durationTicks, end);
    }

    @SuppressWarnings("null")
    public static boolean isThrusting(Player player, long nowTick) {
        if (player == null) return false;
        State state = ACTIVE.get(player.getUUID());
        return state != null && nowTick <= state.endTick;
    }

    public static void stop(ServerPlayer player) {
        if (player == null) {
            return;
        }
        State removed = ACTIVE.remove(player.getUUID());
        if (removed != null) {
            sendClientState(player, false, 0, null);
        }
    }

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null || ACTIVE.isEmpty()) {
            return;
        }
        Iterator<State> iterator = ACTIVE.values().iterator();
        while (iterator.hasNext()) {
            State state = iterator.next();
            if (state == null) {
                sendClientState(server, state, false, 0, null);
                iterator.remove();
                continue;
            }
            ServerLevel level = server.getLevel(state.dimension);
            if (level == null) {
                sendClientState(server, state, false, 0, null);
                iterator.remove();
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(state.playerId);
            if (player == null) {
                iterator.remove();
                continue;
            }
            if (player.level() != level) {
                sendClientState(player, false, 0, null);
                iterator.remove();
                continue;
            }
            if (com.stardew.craft.time.StardewTimePauseService.shouldPauseLevel(level)) {
                continue;
            }
            long nowTick = level.getGameTime();
            if (nowTick > state.endTick) {
                sendClientState(server, state, false, 0, null);
                iterator.remove();
                continue;
            }

            Vec3 current = player.position();
            Vec3 desired = current.add(state.step);
            if (nowTick + 1 >= state.endTick) {
                desired = state.end;
            }
            desired = new Vec3(desired.x, player.getY(), desired.z);

            Vec3 safe = findSafePosition(player, adjustForCollision(player, desired));
            if (safe == null) {
                sendClientState(player, false, 0, null);
                iterator.remove();
                continue;
            }

            Vec3 from = state.lastPos != null ? state.lastPos : current;
            applyHits(level, player, state, from, safe, nowTick);

            Vec3 desiredVel = safe.subtract(current);
            Vec3 currentVel = player.getDeltaMovement();
            Vec3 nextVel = currentVel.add(desiredVel.subtract(currentVel).scale(0.6));
            player.setDeltaMovement(nextVel.x, currentVel.y, nextVel.z);
            player.hasImpulse = true;
            player.move(net.minecraft.world.entity.MoverType.SELF, player.getDeltaMovement());
            player.fallDistance = 0.0F;

            Vec3 afterMove = player.position();
            if (afterMove.subtract(current).horizontalDistanceSqr() < 1.0e-4) {
                player.teleportTo(safe.x, safe.y, safe.z);
                player.fallDistance = 0.0F;
                afterMove = player.position();
            }

            state.lastPos = afterMove;

            spawnTrail(level, player.position());
        }
    }

    @SuppressWarnings("null")
    private static void sendClientState(MinecraftServer server, State state, boolean active,
                                        int durationTicks, Vec3 end) {
        if (server == null || state == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(state.playerId);
        if (player == null) return;
        sendClientState(player, active, durationTicks, end);
    }

    @SuppressWarnings("null")
    private static void sendClientState(ServerPlayer player, boolean active,
                                        int durationTicks, Vec3 end) {
        if (player == null) return;
        double endX = end != null ? end.x : 0.0;
        double endY = end != null ? end.y : 0.0;
        double endZ = end != null ? end.z : 0.0;
        PacketDistributor.sendToPlayer(player,
            new DwarfDaggerThrustPayload(active, durationTicks, endX, endY, endZ));
    }

    @SuppressWarnings("null")
    private static void applyHits(ServerLevel level, ServerPlayer player, State state, Vec3 start, Vec3 end, long nowTick) {
        List<LivingEntity> targets = findTargetsAlongPath(
            level,
            player,
            start,
            end,
            HIT_RADIUS
        );
        if (targets.isEmpty()) {
            return;
        }

        boolean hitAny = false;
        for (LivingEntity target : targets) {
            UUID targetId = target.getUUID();
            if (state.hitTargets.contains(targetId)) {
                continue;
            }
            state.hitTargets.add(targetId);
            hitAny = true;

            SkillContext context = SkillContext.builder()
                .skillId(state.skillId)
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(state.damageMultiplier)
                .build();
            target.invulnerableTime = 0;
            target.hurtTime = 0;
            boolean hit = WeaponSkillDamage.apply(
                    player,
                    target,
                    context,
                    nowTick + HIT_CONTEXT_LIFETIME_TICKS
            );
            if (hit) {
                target.addEffect(new MobEffectInstance(
                    ModMobEffects.WEAK_POINT,
                    WEAK_POINT_DURATION_TICKS,
                    WEAK_POINT_AMPLIFIER,
                    false,
                    true,
                    true
                ));
                hitAny = true;
            }
        }

        if (hitAny && !state.bonusApplied) {
            state.bonusApplied = true;
            player.addEffect(new MobEffectInstance(
                MobEffects.DAMAGE_RESISTANCE,
                RESISTANCE_DURATION_TICKS,
                RESISTANCE_AMPLIFIER,
                false,
                true,
                true
            ));
            PlayerStardewDataAPI.restoreEnergy(player, ENERGY_RESTORE);
            if (DwarfDaggerRushTracker.isActive(player, nowTick)) {
                WeaponSkillCooldowns.setCooldown(
                    player,
                    state.weaponId,
                    state.skillId,
                    nowTick,
                    DwarfDaggerRushTracker.THRUST_COOLDOWN_REFRESH_TICKS
                );
            }
        }
    }

    @SuppressWarnings("null")
    private static List<LivingEntity> findTargetsAlongPath(ServerLevel level, Player player, Vec3 start, Vec3 end, double radius) {
        Vec3 min = new Vec3(Math.min(start.x, end.x), Math.min(start.y, end.y), Math.min(start.z, end.z));
        Vec3 max = new Vec3(Math.max(start.x, end.x), Math.max(start.y, end.y), Math.max(start.z, end.z));
        AABB box = new AABB(min, max).inflate(radius, radius * 0.75, radius);
        List<LivingEntity> targets = level.getEntitiesOfClass(
            LivingEntity.class,
            box,
            entity -> entity.isPickable() && entity != player
        );

        targets.removeIf(entity -> distanceToSegmentSqr(entity.position(), start, end) > radius * radius);
        return targets;
    }

    @SuppressWarnings("null")
    private static double distanceToSegmentSqr(Vec3 point, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        Vec3 ap = point.subtract(a);
        double abLenSqr = ab.lengthSqr();
        if (abLenSqr <= 1.0e-6) {
            return ap.lengthSqr();
        }
        double t = ap.dot(ab) / abLenSqr;
        t = Math.max(0.0, Math.min(1.0, t));
        Vec3 closest = a.add(ab.scale(t));
        return point.distanceToSqr(closest);
    }

    @SuppressWarnings("null")
    private static Vec3 adjustForCollision(ServerPlayer player, Vec3 desired) {
        Vec3 start = player.position();
        Vec3 look = desired.subtract(start);
        if (look.lengthSqr() < 1.0E-6) {
            return desired;
        }
        Vec3 dir = new Vec3(look.x, 0.0, look.z).normalize();
        HitResult hit = player.level().clip(new ClipContext(
            start.add(0, player.getBbHeight() * 0.5, 0),
            desired.add(0, player.getBbHeight() * 0.5, 0),
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            player
        ));

        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 hitPos = hit.getLocation();
            return hitPos.subtract(dir.scale(0.4));
        }
        return desired;
    }

    @SuppressWarnings("null")
    private static Vec3 findSafePosition(Player player, Vec3 desired) {
        if (desired == null) return null;
        AABB box = player.getBoundingBox().move(desired.x - player.getX(), desired.y - player.getY(), desired.z - player.getZ());
        if (player.level().noCollision(player, box)) {
            return desired;
        }
        Vec3 raised = desired.add(0, 0.25, 0);
        AABB boxUp = player.getBoundingBox().move(raised.x - player.getX(), raised.y - player.getY(), raised.z - player.getZ());
        if (player.level().noCollision(player, boxUp)) {
            return raised;
        }
        return null;
    }

    @SuppressWarnings("null")
    private static void spawnTrail(ServerLevel level, Vec3 pos) {
        double y = pos.y + 0.6;
        level.sendParticles(ParticleTypes.ENCHANT,
            pos.x, y, pos.z,
            6, 0.2, 0.15, 0.2, 0.02);
        level.sendParticles(ParticleTypes.CRIT,
            pos.x, y, pos.z,
            4, 0.25, 0.15, 0.25, 0.03);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
            pos.x, y, pos.z,
            3, 0.2, 0.1, 0.2, 0.03);
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        ACTIVE.remove(playerId);
    }
}
