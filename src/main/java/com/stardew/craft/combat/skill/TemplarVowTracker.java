package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.network.TemplarVowPayload;
import com.stardew.craft.effect.ModMobEffects;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class TemplarVowTracker {
    public static final int ACTIVE_DURATION_TICKS = 40;
    public static final double COUNTER_TARGET_RANGE = 4.0;
    public static final float COUNTER_DAMAGE_MULTIPLIER = 1.10F;
    public static final float EXPIRE_SLASH_DAMAGE_MULTIPLIER = 0.80F;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int EXPIRE_SHELTER_DURATION_TICKS = 40;
    public static final int EXPIRE_SHELTER_AMPLIFIER = 0;

    private static final class State {
        private long endTick;
        private final String weaponId;
        private final String skillId;
        private final int cooldownTicks;
        private final WeaponDamageSnapshot weaponSnapshot;
        private boolean cooldownApplied;

        private State(
                long endTick,
                String weaponId,
                String skillId,
                int cooldownTicks,
                WeaponDamageSnapshot weaponSnapshot
        ) {
            this.endTick = endTick;
            this.weaponId = weaponId;
            this.skillId = skillId;
            this.cooldownTicks = cooldownTicks;
            this.weaponSnapshot = weaponSnapshot;
            this.cooldownApplied = false;
        }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private TemplarVowTracker() {}

    public static void start(ServerPlayer player, long nowTick, int durationTicks, String weaponId, String skillId, int cooldownTicks) {
        start(
                player,
                nowTick,
                durationTicks,
                weaponId,
                skillId,
                cooldownTicks,
                null
        );
    }

    public static void start(
            ServerPlayer player,
            long nowTick,
            int durationTicks,
            String weaponId,
            String skillId,
            int cooldownTicks,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        if (player == null || durationTicks <= 0 || weaponId == null || skillId == null) {
            return;
        }
        ACTIVE.put(
                player.getUUID(),
                new State(
                        nowTick + durationTicks,
                        weaponId,
                        skillId,
                        cooldownTicks,
                        weaponSnapshot
                )
        );
        PacketDistributor.sendToPlayer(player, new TemplarVowPayload(true, durationTicks));
    }

    public static boolean isActive(ServerPlayer player, long nowTick) {
        State state = ACTIVE.get(player.getUUID());
        return state != null && isWithinActiveWindow(nowTick, state.endTick);
    }

    public static boolean isActiveRaw(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static Optional<WeaponDamageSnapshot> getWeaponSnapshot(
            ServerPlayer player
    ) {
        State state = ACTIVE.get(player.getUUID());
        return state == null
                ? Optional.empty()
                : Optional.ofNullable(state.weaponSnapshot);
    }

    public static void endNow(ServerPlayer player, long nowTick) {
        cancel(player, nowTick, true);
    }

    public static void cancel(
            ServerPlayer player,
            long nowTick,
            boolean notifyClient
    ) {
        State state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return;
        }
        applyCooldown(player, state, nowTick);
        if (notifyClient) {
            PacketDistributor.sendToPlayer(player, new TemplarVowPayload(false, 0));
        }
        ACTIVE.remove(player.getUUID());
    }

    public static void tick(ServerPlayer player, long nowTick) {
        State state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return;
        }
        if (!isWithinActiveWindow(nowTick, state.endTick)) {
            applyLightSlash(player, nowTick, state.weaponSnapshot);
            player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            applyCooldown(player, state, nowTick);
            PacketDistributor.sendToPlayer(player, new TemplarVowPayload(false, 0));
            ACTIVE.remove(player.getUUID());
        }
    }

    private static void applyCooldown(ServerPlayer player, State state, long nowTick) {
        if (!state.cooldownApplied && state.cooldownTicks > 0) {
            WeaponSkillCooldowns.setCooldown(player, state.weaponId, state.skillId, nowTick, state.cooldownTicks);
            state.cooldownApplied = true;
        }
    }

    @SuppressWarnings("null")
    private static void applyLightSlash(
            ServerPlayer player,
            long nowTick,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        if (player == null) {
            return;
        }

        MobEffect shelter = Objects.requireNonNull(ModMobEffects.SHELTER.get(), "shelter");
        Holder<MobEffect> shelterHolder = Holder.direct(shelter);
        player.addEffect(new MobEffectInstance(
                shelterHolder,
                EXPIRE_SHELTER_DURATION_TICKS,
                EXPIRE_SHELTER_AMPLIFIER,
                false,
                false,
                true
        ));

        LivingEntity target = findTargetEntity(player, COUNTER_TARGET_RANGE);
        if (target == null) {
            return;
        }

        SkillContext context = createStrikeContext(
                EXPIRE_SLASH_DAMAGE_MULTIPLIER
        );
        long expireTick = nowTick + HIT_CONTEXT_LIFETIME_TICKS;
        if (weaponSnapshot == null) {
            WeaponSkillDamage.apply(
                    player,
                    target,
                    context,
                    expireTick,
                    WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT
            );
        } else {
            WeaponSkillDamage.apply(
                    player,
                    target,
                    context,
                    weaponSnapshot,
                    expireTick,
                    WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT
            );
        }
    }

    static SkillContext createStrikeContext(float damageMultiplier) {
        return SkillContext.builder()
                .skillId("templar_vow")
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(damageMultiplier)
                .build();
    }

    static boolean isWithinActiveWindow(long nowTick, long endTick) {
        return nowTick <= endTick;
    }

    private static LivingEntity findTargetEntity(ServerPlayer player, double range) {
        Level level = Objects.requireNonNull(player.level(), "level");
        Vec3 eyePos = Objects.requireNonNull(player.getEyePosition(), "eyePos");
        Vec3 lookVec = Objects.requireNonNull(player.getLookAngle(), "lookVec");
        Vec3 scaledLook = Objects.requireNonNull(lookVec.scale(range), "scaledLook");
        Vec3 end = Objects.requireNonNull(eyePos.add(scaledLook), "end");
        Vec3 scaledBox = Objects.requireNonNull(lookVec.scale(range), "scaledBox");
        AABB box = Objects.requireNonNull(player.getBoundingBox().expandTowards(scaledBox).inflate(1.0D), "box");

        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
            level,
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
