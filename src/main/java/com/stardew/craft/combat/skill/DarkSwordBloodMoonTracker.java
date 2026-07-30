package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.CombatHealing;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@SuppressWarnings("null")
public final class DarkSwordBloodMoonTracker {
    public static final float LIFESTEAL_RATIO = 0.30F;
    public static final float DAMAGE_BONUS_MULTIPLIER = 1.35F;
    public static final float BURN_MAXIMUM_HEALTH_RATIO = 0.01F;
    public static final float MINIMUM_BURN_AMOUNT = 1.0F;
    public static final float MINIMUM_REMAINING_HEALTH = 1.0F;
    public static final float BURST_RADIUS = 3.5F;
    public static final float MINIMUM_BURST_DAMAGE_MULTIPLIER = 0.1F;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;

    private static final class State {
        private final long endTick;
        private long nextBurnTick;
        private final int burnIntervalTicks;
        private final ResourceKey<Level> originDimension;
        private final float averageWeaponDamage;
        private final WeaponDamageSnapshot weaponSnapshot;
        private float totalBurned;
        private float totalHealed;

        private State(
                long endTick,
                long nextBurnTick,
                int burnIntervalTicks,
                ResourceKey<Level> originDimension,
                float averageWeaponDamage,
                WeaponDamageSnapshot weaponSnapshot
        ) {
            this.endTick = endTick;
            this.nextBurnTick = nextBurnTick;
            this.burnIntervalTicks = burnIntervalTicks;
            this.originDimension = originDimension;
            this.averageWeaponDamage = averageWeaponDamage;
            this.weaponSnapshot = weaponSnapshot;
        }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private DarkSwordBloodMoonTracker() {}

    public static void start(
            ServerPlayer player,
            long nowTick,
            int durationTicks,
            int burnIntervalTicks,
            float averageWeaponDamage
    ) {
        startInternal(
                player,
                nowTick,
                durationTicks,
                burnIntervalTicks,
                averageWeaponDamage,
                null
        );
    }

    public static void start(
            ServerPlayer player,
            long nowTick,
            int durationTicks,
            int burnIntervalTicks,
            float averageWeaponDamage,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        startInternal(
                player,
                nowTick,
                durationTicks,
                burnIntervalTicks,
                averageWeaponDamage,
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
            int burnIntervalTicks,
            float averageWeaponDamage,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        if (player == null || durationTicks <= 0) {
            return;
        }
        long endTick = nowTick + durationTicks;
        int interval = Math.max(1, burnIntervalTicks);
        ACTIVE.put(
                player.getUUID(),
                new State(
                        endTick,
                        nowTick + interval,
                        interval,
                        player.level().dimension(),
                        Math.max(0.0F, averageWeaponDamage),
                        weaponSnapshot
                )
        );
        DarkSwordEffects.playBloodMoonStart(player);
    }

    public static boolean isActive(ServerPlayer player, long nowTick) {
        if (player == null) {
            return false;
        }
        State state = ACTIVE.get(player.getUUID());
        return state != null && shouldRemainActive(
                state.endTick,
                nowTick,
                player.isAlive() && !player.isRemoved(),
                isSameDimension(
                        state.originDimension,
                        player.level().dimension()
                )
        );
    }

    public static float getLifestealRatio(ServerPlayer player, long nowTick) {
        return isActive(player, nowTick) ? LIFESTEAL_RATIO : 0.0F;
    }

    public static float getDamageBonusMultiplier(ServerPlayer player, long nowTick) {
        return isActive(player, nowTick)
                ? DAMAGE_BONUS_MULTIPLIER
                : 1.0F;
    }

    public static void recordLifeSteal(ServerPlayer player, long nowTick, float healedAmount) {
        if (player == null) {
            return;
        }
        State state = ACTIVE.get(player.getUUID());
        if (state == null || !isActive(player, nowTick)) {
            return;
        }
        state.totalHealed += Math.max(0.0F, healedAmount);
    }

    public static void tick(ServerPlayer player, long nowTick) {
        if (player == null) {
            return;
        }
        State state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return;
        }

        if (!player.isAlive()
                || player.isRemoved()
                || !isSameDimension(
                        state.originDimension,
                        player.level().dimension()
                )) {
            ACTIVE.remove(player.getUUID());
            return;
        }

        if (nowTick > state.endTick) {
            finishNaturally(player, state, nowTick);
            ACTIVE.remove(player.getUUID());
            return;
        }

        if (nowTick >= state.nextBurnTick) {
            state.nextBurnTick += state.burnIntervalTicks;
            applyBurn(player, state);
        }
    }

    private static void applyBurn(ServerPlayer player, State state) {
        float burn = burnAmount(CombatHealing.maximumHealth(player));
        float actualBurn = CombatHealing.spendNonlethal(
                player,
                burn,
                MINIMUM_REMAINING_HEALTH
        );

        if (actualBurn > 0.0F) {
            state.totalBurned += actualBurn;
            DarkSwordEffects.playBloodMoonBurn(player);
        }
    }

    private static void finishNaturally(
            ServerPlayer player,
            State state,
            long nowTick
    ) {
        float netBurn = netBurn(state.totalBurned, state.totalHealed);
        if (netBurn <= 0.0f) {
            return;
        }

        float damageMultiplier = burstDamageMultiplier(
                netBurn,
                state.averageWeaponDamage
        );
        if (damageMultiplier <= 0.0F) {
            return;
        }

        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        Vec3 center = player.position();
        List<LivingEntity> targets = getTargetsInRadius(
                level,
                center,
                BURST_RADIUS,
                player
        );
        if (targets.isEmpty()) {
            return;
        }

        DarkSwordEffects.playBloodMoonBurst(player);

        for (LivingEntity target : targets) {
            target.invulnerableTime = 0;
            target.hurtTime = 0;
            applyDamage(
                    player,
                    target,
                    createBurstContext(damageMultiplier),
                    state.weaponSnapshot,
                    nowTick + HIT_CONTEXT_LIFETIME_TICKS
            );
        }
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
                    expireTick
            );
            return;
        }
        WeaponSkillDamage.apply(
                player,
                target,
                context,
                weaponSnapshot,
                expireTick
        );
    }

    static boolean shouldRemainActive(
            long endTick,
            long nowTick,
            boolean casterAvailable,
            boolean sameDimension
    ) {
        return casterAvailable
                && sameDimension
                && nowTick <= endTick;
    }

    static boolean isSameDimension(
            ResourceKey<Level> expected,
            ResourceKey<Level> actual
    ) {
        return expected.equals(actual);
    }

    static float burnAmount(float maximumHealth) {
        return Math.max(
                MINIMUM_BURN_AMOUNT,
                Math.max(0.0F, maximumHealth)
                        * BURN_MAXIMUM_HEALTH_RATIO
        );
    }

    static float netBurn(float totalBurned, float totalHealed) {
        return Math.max(
                0.0F,
                Math.max(0.0F, totalBurned)
                        - Math.max(0.0F, totalHealed)
        );
    }

    static float burstDamageMultiplier(
            float netBurn,
            float averageWeaponDamage
    ) {
        if (netBurn <= 0.0F || averageWeaponDamage <= 0.0F) {
            return 0.0F;
        }
        return Math.max(
                MINIMUM_BURST_DAMAGE_MULTIPLIER,
                netBurn / averageWeaponDamage
        );
    }

    static SkillContext createBurstContext(float damageMultiplier) {
        return SkillContext.builder()
                .skillId("dark_sword_blood_moon_burst")
                .tier(SkillContext.SkillTier.MAJOR)
                .damageMultiplier(damageMultiplier)
                .build();
    }

    private static List<LivingEntity> getTargetsInRadius(
            ServerLevel level,
            Vec3 center,
            float radius,
            Player owner
    ) {
        AABB box = new AABB(
            center.x - radius, center.y - radius * 0.6, center.z - radius,
            center.x + radius, center.y + radius * 0.6, center.z + radius
        );
        return level.getEntitiesOfClass(LivingEntity.class, box,
            entity -> entity.isPickable() && entity.isAlive() && entity != owner);
    }

    public static boolean hasState(UUID playerId) {
        return ACTIVE.containsKey(playerId);
    }

    public static boolean isBoundToCurrentContext(ServerPlayer player) {
        State state = ACTIVE.get(player.getUUID());
        return state != null
                && player.isAlive()
                && !player.isRemoved()
                && isSameDimension(
                        state.originDimension,
                        player.level().dimension()
                );
    }

    public static boolean cancel(ServerPlayer player) {
        return player != null
                && ACTIVE.remove(player.getUUID()) != null;
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        ACTIVE.remove(playerId);
    }
}
