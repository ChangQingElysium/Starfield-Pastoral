package com.stardew.craft.combat.skill;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import net.neoforged.neoforge.network.PacketDistributor;

import com.stardew.craft.combat.VfxColors;
import com.stardew.craft.combat.network.SingularityRunePayload;
import com.stardew.craft.combat.network.SingularityCorePayload;
import com.stardew.craft.combat.network.RiftPathPayload;
import com.stardew.craft.combat.network.ShockwaveRingPayload;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 无限之刃 - 奇点进化：短暂聚拢 -> 爆心 -> 突进斩
 */
public final class SingularityEvolveTracker {

    public enum Status {
        ACTIVE,
        COMPLETED,
        INVALIDATED
    }

    public static final int ACTIVE_DURATION_TICKS = 20;
    public static final double EFFECT_RADIUS = 4.0D;
    public static final float EXPLOSION_DAMAGE_MULTIPLIER = 1.6F;
    public static final float SLASH_DAMAGE_MULTIPLIER = 1.2F;
    public static final double DASH_DISTANCE = 5.0D;
    public static final int DASH_DURATION_TICKS = 5;
    public static final double PULL_STRENGTH = 0.15D;
    public static final double SLASH_PATH_HALF_WIDTH = 0.9D;
    public static final float RIFT_LENGTH = 3.0F;
    public static final int RIFT_DURATION_TICKS = 40;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;

    private static final class State {
        private final long endTick;
        private final double radius;
        private final float explodeMultiplier;
        private final float slashMultiplier;
        private final String skillId;
        private final boolean evolved;
        private final ResourceKey<Level> dimension;
        private final WeaponDamageSnapshot weaponSnapshot;
        private long lastProcessedTick = Long.MIN_VALUE;

        private State(
                long endTick,
                double radius,
                float explodeMultiplier,
                float slashMultiplier,
                String skillId,
                boolean evolved,
                ResourceKey<Level> dimension,
                WeaponDamageSnapshot weaponSnapshot
        ) {
            this.endTick = endTick;
            this.radius = radius;
            this.explodeMultiplier = explodeMultiplier;
            this.slashMultiplier = slashMultiplier;
            this.skillId = skillId;
            this.evolved = evolved;
            this.dimension = dimension;
            this.weaponSnapshot = weaponSnapshot;
        }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private SingularityEvolveTracker() {}

    @SuppressWarnings("null")
    public static void start(ServerPlayer player, long nowTick, int durationTicks, double radius,
                             float explodeMultiplier, float slashMultiplier, String skillId, boolean evolved) {
        startInternal(
                player,
                nowTick,
                durationTicks,
                radius,
                explodeMultiplier,
                slashMultiplier,
                skillId,
                evolved,
                null
        );
    }

    @SuppressWarnings("null")
    public static void start(
            ServerPlayer player,
            long nowTick,
            int durationTicks,
            double radius,
            float explodeMultiplier,
            float slashMultiplier,
            String skillId,
            boolean evolved,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        startInternal(
                player,
                nowTick,
                durationTicks,
                radius,
                explodeMultiplier,
                slashMultiplier,
                skillId,
                evolved,
                java.util.Objects.requireNonNull(
                        weaponSnapshot,
                        "weaponSnapshot"
                )
        );
    }

    private static void startInternal(
            ServerPlayer player,
            long nowTick,
            int durationTicks,
            double radius,
            float explodeMultiplier,
            float slashMultiplier,
            String skillId,
            boolean evolved,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        if (player == null) {
            return;
        }
        ACTIVE.put(
                player.getUUID(),
                new State(
                        nowTick + Math.max(1, durationTicks),
                        radius,
                        explodeMultiplier,
                        slashMultiplier,
                        skillId,
                        evolved,
                        player.level().dimension(),
                        weaponSnapshot
                )
        );

        Vec3 pos = player.position();
        ServerLevel level = player.serverLevel();
        int color = evolved ? VfxColors.INFINITY_GOLD : VfxColors.GALAXY_PURPLE;
        PacketDistributor.sendToPlayersInDimension(level,
            new ShockwaveRingPayload((float) pos.x, (float) pos.y, (float) pos.z, 3.6f, 12, color));
        PacketDistributor.sendToPlayersInDimension(level,
            new SingularityRunePayload((float) pos.x, (float) pos.y, (float) pos.z, (float) radius, durationTicks, color));
        PacketDistributor.sendToPlayersInDimension(level,
            new SingularityCorePayload((float) pos.x, (float) pos.y + 0.05f, (float) pos.z, 1.15f, durationTicks, color));
    }

    public static Status tick(ServerPlayer player, long nowTick) {
        if (player == null) {
            return Status.INVALIDATED;
        }
        State state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return Status.INVALIDATED;
        }
        if (!isValidContext(
                player.isAlive() && !player.isRemoved(),
                isSameDimension(
                        state.dimension,
                        player.level().dimension()
                )
        )) {
            ACTIVE.remove(player.getUUID());
            return Status.INVALIDATED;
        }
        if (!shouldProcessTick(nowTick, state.lastProcessedTick)) {
            return Status.ACTIVE;
        }
        state.lastProcessedTick = nowTick;
        if (isWithinPullWindow(nowTick, state.endTick)) {
            pullTargets(player, state);
            return Status.ACTIVE;
        }

        try {
            explodeAndDash(player, nowTick, state);
        } finally {
            ACTIVE.remove(player.getUUID());
        }
        return Status.COMPLETED;
    }

    @SuppressWarnings("null")
    private static void pullTargets(ServerPlayer player, State state) {
        ServerLevel level = player.serverLevel();
        Vec3 center = player.position();
        AABB box = new AABB(
            center.x - state.radius, center.y - 1.5, center.z - state.radius,
            center.x + state.radius, center.y + 2.0, center.z + state.radius
        );

        List<LivingEntity> targets = List.copyOf(
                level.getEntitiesOfClass(
                        LivingEntity.class,
                        box,
                        entity -> entity.isPickable()
                                && entity != player
                )
        );

        for (LivingEntity target : targets) {
            Vec3 dir = center.subtract(target.position());
            if (dir.lengthSqr() < 1.0E-4) {
                continue;
            }
            Vec3 pull = dir.normalize().scale(PULL_STRENGTH);
            target.setDeltaMovement(target.getDeltaMovement().add(pull));
            target.hurtMarked = true;
            if ((level.getGameTime() & 1L) == 0L) {
                double px = target.getX();
                double py = target.getY() + target.getBbHeight() * 0.5;
                double pz = target.getZ();
                level.sendParticles(ParticleTypes.END_ROD,
                    px, py, pz,
                    1, 0.15, 0.2, 0.15, 0.01);
                if (level.random.nextFloat() < 0.6f) {
                    level.sendParticles(ParticleTypes.ENCHANT,
                        px, py, pz,
                        1, 0.12, 0.18, 0.12, 0.01);
                }
            }
        }
    }

    @SuppressWarnings("null")
    private static void explodeAndDash(ServerPlayer player, long nowTick, State state) {
        ServerLevel level = player.serverLevel();
        Vec3 center = player.position();
        AABB box = new AABB(
            center.x - state.radius, center.y - 1.5, center.z - state.radius,
            center.x + state.radius, center.y + 2.0, center.z + state.radius
        );

        List<LivingEntity> targets = List.copyOf(
                level.getEntitiesOfClass(
                        LivingEntity.class,
                        box,
                        entity -> entity.isPickable()
                                && entity != player
                )
        );

        for (LivingEntity target : targets) {
            SkillContext context = createDamageContext(
                    state.skillId,
                    state.explodeMultiplier
            );
            target.invulnerableTime = 0;
            target.hurtTime = 0;
            if (state.weaponSnapshot == null) {
                WeaponSkillDamage.apply(
                        player,
                        target,
                        context,
                        nowTick + HIT_CONTEXT_LIFETIME_TICKS,
                        WeaponSkillDamage.AttackGatePolicy
                                .RESPECT_AT_IMPACT
                );
            } else {
                WeaponSkillDamage.apply(
                        player,
                        target,
                        context,
                        state.weaponSnapshot,
                        nowTick + HIT_CONTEXT_LIFETIME_TICKS,
                        WeaponSkillDamage.AttackGatePolicy
                                .RESPECT_AT_IMPACT
                );
            }
        }

        level.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8f, 1.1f);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 1.2f);
        level.sendParticles(ParticleTypes.PORTAL,
            center.x, center.y + 0.8, center.z,
            30, state.radius * 0.4, 0.6, state.radius * 0.4, 0.05);

        dashForward(player, nowTick, DASH_DISTANCE);

        if (state.evolved) {
            Vec3 look = getHorizontalLook(player).normalize();
            Vec3 pos = player.position();
            float yaw = (float) (Math.atan2(-look.x, look.z) * (180.0 / Math.PI));
            PacketDistributor.sendToPlayersInDimension(level,
                new RiftPathPayload(
                        (float) pos.x,
                        (float) pos.y,
                        (float) pos.z,
                        yaw,
                        RIFT_LENGTH,
                        RIFT_DURATION_TICKS,
                        VfxColors.INFINITY_GOLD
                ));
            if (state.weaponSnapshot == null) {
                RiftPathDamageTracker.start(
                        player,
                        pos,
                        yaw,
                        RIFT_LENGTH,
                        RIFT_DURATION_TICKS,
                        "singularity_rift_path"
                );
            } else {
                RiftPathDamageTracker.start(
                        player,
                        pos,
                        yaw,
                        RIFT_LENGTH,
                        RIFT_DURATION_TICKS,
                        "singularity_rift_path",
                        state.weaponSnapshot
                );
            }
        }

        Vec3 start = player.position();
        Vec3 look = getHorizontalLook(player).normalize();
        Vec3 end = start.add(look.scale(DASH_DISTANCE));
        List<LivingEntity> slashTargets = findTargetsOnPath(
                level,
                player,
                start,
                end,
                SLASH_PATH_HALF_WIDTH
        );
        for (LivingEntity target : slashTargets) {
            SkillContext context = createDamageContext(
                    state.skillId,
                    state.slashMultiplier
            );
            target.invulnerableTime = 0;
            target.hurtTime = 0;
            if (state.weaponSnapshot == null) {
                WeaponSkillDamage.apply(
                        player,
                        target,
                        context,
                        nowTick + HIT_CONTEXT_LIFETIME_TICKS,
                        WeaponSkillDamage.AttackGatePolicy
                                .RESPECT_AT_IMPACT
                );
            } else {
                WeaponSkillDamage.apply(
                        player,
                        target,
                        context,
                        state.weaponSnapshot,
                        nowTick + HIT_CONTEXT_LIFETIME_TICKS,
                        WeaponSkillDamage.AttackGatePolicy
                                .RESPECT_AT_IMPACT
                );
            }
        }
    }

    @SuppressWarnings("null")
    private static Vec3 getHorizontalLook(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0.0, look.z);
        if (dir.lengthSqr() < 1.0E-4) {
            dir = look;
        }
        return dir.normalize();
    }

    @SuppressWarnings("null")
    private static void dashForward(ServerPlayer player, long nowTick, double distance) {
        Vec3 start = player.position();
        Vec3 look = getHorizontalLook(player);
        Vec3 end = start.add(look.scale(distance));

        HitResult hit = player.level().clip(new ClipContext(
            start.add(0, player.getBbHeight() * 0.5, 0),
            end.add(0, player.getBbHeight() * 0.5, 0),
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            player
        ));

        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 hitPos = hit.getLocation();
            end = hitPos.subtract(look.scale(0.4));
        }

        DashMovementTracker.start(
                player,
                nowTick,
                end,
                DASH_DURATION_TICKS
        );
    }

    @SuppressWarnings("null")
    private static List<LivingEntity> findTargetsOnPath(ServerLevel level, ServerPlayer player, Vec3 start, Vec3 end, double halfWidth) {
        Vec3 min = new Vec3(Math.min(start.x, end.x), Math.min(start.y, end.y), Math.min(start.z, end.z));
        Vec3 max = new Vec3(Math.max(start.x, end.x), Math.max(start.y, end.y), Math.max(start.z, end.z));
        AABB box = new AABB(min, max).inflate(halfWidth, 1.2, halfWidth);
        return List.copyOf(level.getEntitiesOfClass(
                LivingEntity.class,
                box,
                entity -> entity.isPickable() && entity != player
        ));
    }

    public static boolean hasState(UUID playerId) {
        return playerId != null && ACTIVE.containsKey(playerId);
    }

    public static void cancel(ServerPlayer player) {
        if (player != null) {
            ACTIVE.remove(player.getUUID());
        }
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        ACTIVE.remove(playerId);
    }

    static boolean isWithinPullWindow(long nowTick, long endTick) {
        return nowTick < endTick;
    }

    static boolean shouldProcessTick(
            long nowTick,
            long lastProcessedTick
    ) {
        return nowTick != lastProcessedTick;
    }

    static boolean isValidContext(
            boolean casterAvailable,
            boolean sameDimension
    ) {
        return casterAvailable && sameDimension;
    }

    static boolean isSameDimension(
            ResourceKey<Level> expected,
            ResourceKey<Level> actual
    ) {
        return expected.equals(actual);
    }

    static SkillContext createDamageContext(
            String skillId,
            float damageMultiplier
    ) {
        return SkillContext.builder()
                .skillId(skillId)
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(damageMultiplier)
                .build();
    }

}
