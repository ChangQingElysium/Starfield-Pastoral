package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** Detached Steel Falchion damage-over-time effects that outlive executions. */
public final class SteelFalchionDotTracker {
    public static final int DOT_DURATION_TICKS = 100;
    public static final int DOT_INTERVAL_TICKS = 20;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final String LINE_DOT_SKILL_ID =
            "steel_falchion_line_dot";

    private static final class DotState {
        private final UUID targetId;
        private final float damageMultiplier;
        private final SkillContext.SkillTier tier;
        private final WeaponDamageSnapshot weaponSnapshot;
        private long endTick;
        private long nextDamageTick;

        private DotState(
                UUID targetId,
                float damageMultiplier,
                SkillContext.SkillTier tier,
                long endTick,
                long nextDamageTick,
                WeaponDamageSnapshot weaponSnapshot
        ) {
            this.targetId = targetId;
            this.damageMultiplier = damageMultiplier;
            this.tier = tier;
            this.endTick = endTick;
            this.nextDamageTick = nextDamageTick;
            this.weaponSnapshot = weaponSnapshot;
        }
    }

    private static final class PlayerDots {
        private final ResourceKey<Level> dimension;
        private final Map<UUID, DotState> dots = new HashMap<>();
        private long lastTick = Long.MIN_VALUE;

        private PlayerDots(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }
    }

    private static final Map<UUID, PlayerDots> ACTIVE = new HashMap<>();

    private SteelFalchionDotTracker() {}

    static void apply(
            ServerPlayer player,
            LivingEntity target,
            long nowTick,
            float damageMultiplier,
            SkillContext.SkillTier tier,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        PlayerDots playerDots = stateFor(player);
        DotState existing = playerDots.dots.get(target.getUUID());
        long endTick = nowTick + DOT_DURATION_TICKS;
        if (existing != null) {
            float finalMultiplier = Math.max(
                    existing.damageMultiplier,
                    damageMultiplier
            );
            SkillContext.SkillTier finalTier =
                    existing.tier == SkillContext.SkillTier.MAJOR
                            ? existing.tier
                            : tier;
            long finalEndTick = Math.max(existing.endTick, endTick);
            long nextDamageTick = Math.min(
                    existing.nextDamageTick,
                    nowTick
            );
            WeaponDamageSnapshot finalSnapshot = weaponSnapshot == null
                    ? existing.weaponSnapshot
                    : weaponSnapshot;
            playerDots.dots.put(
                    target.getUUID(),
                    new DotState(
                            target.getUUID(),
                            finalMultiplier,
                            finalTier,
                            finalEndTick,
                            nextDamageTick,
                            finalSnapshot
                    )
            );
            return;
        }
        playerDots.dots.put(
                target.getUUID(),
                new DotState(
                        target.getUUID(),
                        damageMultiplier,
                        tier,
                        endTick,
                        nowTick,
                        weaponSnapshot
                )
        );
    }

    public static void tickDetachedEffects(
            ServerPlayer player,
            long nowTick
    ) {
        PlayerDots playerDots = ACTIVE.get(player.getUUID());
        if (playerDots == null || playerDots.lastTick == nowTick) {
            return;
        }
        playerDots.lastTick = nowTick;
        if (!SteelFalchionExecutionSupport.isSameDimension(
                playerDots.dimension,
                player.level().dimension()
        )) {
            ACTIVE.remove(player.getUUID());
            return;
        }

        tickDots(player, playerDots, nowTick);
        if (playerDots.dots.isEmpty()) {
            ACTIVE.remove(player.getUUID());
        }
    }

    public static void removePlayer(UUID playerId) {
        ACTIVE.remove(playerId);
    }

    @SuppressWarnings("null")
    private static void tickDots(
            ServerPlayer player,
            PlayerDots playerDots,
            long nowTick
    ) {
        ServerLevel level = player.serverLevel();
        Iterator<DotState> iterator = playerDots.dots.values().iterator();
        while (iterator.hasNext()) {
            DotState dot = iterator.next();
            if (SteelFalchionExecutionSupport.isExpired(
                    nowTick,
                    dot.endTick
            )) {
                iterator.remove();
                continue;
            }
            if (nowTick < dot.nextDamageTick) {
                continue;
            }
            dot.nextDamageTick += DOT_INTERVAL_TICKS;
            Entity entity = level.getEntity(dot.targetId);
            if (!(entity instanceof LivingEntity target)
                    || !target.isAlive()) {
                    iterator.remove();
                    continue;
            }

            applyDamage(
                    player,
                    target,
                    SteelFalchionExecutionSupport.createDamageContext(
                            LINE_DOT_SKILL_ID,
                            dot.tier,
                            dot.damageMultiplier
                    ),
                    dot.weaponSnapshot,
                    nowTick + HIT_CONTEXT_LIFETIME_TICKS
            );
        }
    }

    private static PlayerDots stateFor(ServerPlayer player) {
        PlayerDots existing = ACTIVE.get(player.getUUID());
        if (existing != null
                && !SteelFalchionExecutionSupport.isSameDimension(
                        existing.dimension,
                        player.level().dimension()
                )) {
            ACTIVE.remove(player.getUUID());
            existing = null;
        }
        if (existing == null) {
            existing = new PlayerDots(player.level().dimension());
            ACTIVE.put(player.getUUID(), existing);
        }
        return existing;
    }

    private static void applyDamage(
            ServerPlayer player,
            LivingEntity target,
            SkillContext context,
            WeaponDamageSnapshot weaponSnapshot,
            long expireTick
    ) {
        if (weaponSnapshot == null) {
            WeaponSkillDamage.apply(
                    player,
                    target,
                    context,
                    expireTick,
                    WeaponSkillDamage.AttackGatePolicy.SKILL_DAMAGE,
                    WeaponSkillDamage.HitCooldownPolicy
                            .BYPASS_FOR_AUTHORED_SEQUENCE
            );
            return;
        }
        WeaponSkillDamage.apply(
                player,
                target,
                context,
                weaponSnapshot,
                expireTick,
                WeaponSkillDamage.AttackGatePolicy.SKILL_DAMAGE,
                WeaponSkillDamage.HitCooldownPolicy
                        .BYPASS_FOR_AUTHORED_SEQUENCE
        );
    }
}
