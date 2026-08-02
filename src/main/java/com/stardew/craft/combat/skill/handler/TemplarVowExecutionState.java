package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.network.TemplarVowPayload;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.runtime.DeferredSkillCooldown;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.effect.ModMobEffects;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** One Templar Vow window, counter token, and deferred cooldown. */
final class TemplarVowExecutionState
        implements SkillInstance.ExecutionState {
    private final ResourceKey<Level> dimension;
    private final long endTick;
    private final WeaponDamageSnapshot weaponSnapshot;
    private final DeferredSkillCooldown cooldown;
    private boolean counterConsumed;
    private boolean settled;
    private boolean advancing;

    TemplarVowExecutionState(
            ResourceKey<Level> dimension,
            long nowTick,
            int durationTicks,
            WeaponDamageSnapshot weaponSnapshot,
            DeferredSkillCooldown cooldown
    ) {
        if (durationTicks <= 0) {
            throw new IllegalArgumentException(
                    "Templar Vow duration must be positive"
            );
        }
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.endTick = nowTick + durationTicks;
        this.weaponSnapshot = weaponSnapshot;
        this.cooldown = Objects.requireNonNull(cooldown, "cooldown");
    }

    void start(ServerPlayer player, int durationTicks) {
        PacketDistributor.sendToPlayer(
                player,
                new TemplarVowPayload(true, durationTicks)
        );
    }

    Optional<TemplarVowSkillHandler.CounterActivation> consumeCounter(
            long nowTick,
            ResourceKey<Level> currentDimension
    ) {
        if (!isActive(nowTick, currentDimension)) {
            return Optional.empty();
        }
        counterConsumed = true;
        return Optional.of(
                new TemplarVowSkillHandler.CounterActivation(
                        weaponSnapshot
                )
        );
    }

    SkillTickResult advance(SkillExecutionContext context) {
        if (settled) {
            return SkillTickResult.COMPLETE;
        }
        if (advancing) {
            return SkillTickResult.CONTINUE;
        }
        if (!dimension.equals(context.player().level().dimension())) {
            return SkillTickResult.CANCEL;
        }
        if (counterConsumed) {
            settle(context.player(), context.nowTick(), true);
            return SkillTickResult.COMPLETE;
        }
        if (isWithinActiveWindow(context.nowTick(), endTick)) {
            return SkillTickResult.CONTINUE;
        }

        advancing = true;
        try {
            applyExpirySlash(context);
            context.player().swing(InteractionHand.MAIN_HAND, true);
            settle(context.player(), context.nowTick(), true);
            return SkillTickResult.COMPLETE;
        } finally {
            advancing = false;
        }
    }

    void finishCounter(ServerPlayer player, long nowTick) {
        if (counterConsumed) {
            settle(player, nowTick, true);
        }
    }

    void cancel(ServerPlayer player, long nowTick, boolean notifyClient) {
        settle(player, nowTick, notifyClient);
    }

    boolean isActive(
            long nowTick,
            ResourceKey<Level> currentDimension
    ) {
        return !counterConsumed
                && !settled
                && dimension.equals(currentDimension)
                && isWithinActiveWindow(nowTick, endTick);
    }

    static boolean isWithinActiveWindow(long nowTick, long endTick) {
        return nowTick <= endTick;
    }

    private void settle(
            ServerPlayer player,
            long nowTick,
            boolean notifyClient
    ) {
        if (settled) {
            return;
        }
        WeaponSkillRuntime.commitDeferredCooldown(
                player,
                cooldown,
                nowTick
        );
        if (notifyClient) {
            PacketDistributor.sendToPlayer(
                    player,
                    new TemplarVowPayload(false, 0)
            );
        }
        settled = true;
    }

    @SuppressWarnings("null")
    private void applyExpirySlash(SkillExecutionContext context) {
        ServerPlayer player = context.player();
        MobEffect shelter = Objects.requireNonNull(
                ModMobEffects.SHELTER.get(),
                "shelter"
        );
        player.addEffect(new MobEffectInstance(
                Holder.direct(shelter),
                TemplarVowSkillHandler.EXPIRE_SHELTER_DURATION_TICKS,
                TemplarVowSkillHandler.EXPIRE_SHELTER_AMPLIFIER,
                false,
                false,
                true
        ));

        LivingEntity target = findTargetEntity(
                player,
                TemplarVowSkillHandler.COUNTER_TARGET_RANGE
        );
        if (target == null) {
            return;
        }
        SkillContext strikeContext = TemplarVowSkillHandler
                .createStrikeContext(
                        TemplarVowSkillHandler
                                .EXPIRE_SLASH_DAMAGE_MULTIPLIER
                );
        long expireTick = context.nowTick()
                + TemplarVowSkillHandler.HIT_CONTEXT_LIFETIME_TICKS;
        if (weaponSnapshot == null) {
            WeaponSkillDamage.apply(
                    player,
                    target,
                    strikeContext,
                    expireTick,
                    WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT,
                    WeaponSkillDamage.HitCooldownPolicy.RESPECT_VANILLA
            );
        } else {
            WeaponSkillDamage.apply(
                    player,
                    target,
                    strikeContext,
                    weaponSnapshot,
                    expireTick,
                    WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT,
                    WeaponSkillDamage.HitCooldownPolicy.RESPECT_VANILLA
            );
        }
    }

    private static LivingEntity findTargetEntity(
            ServerPlayer player,
            double range
    ) {
        Level level = Objects.requireNonNull(player.level(), "level");
        Vec3 eyePosition = Objects.requireNonNull(
                player.getEyePosition(),
                "eyePosition"
        );
        Vec3 look = Objects.requireNonNull(player.getLookAngle(), "look");
        Vec3 end = Objects.requireNonNull(
                eyePosition.add(look.scale(range)),
                "end"
        );
        AABB bounds = Objects.requireNonNull(
                player.getBoundingBox()
                        .expandTowards(look.scale(range))
                        .inflate(1.0D),
                "bounds"
        );
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                level,
                player,
                eyePosition,
                end,
                bounds,
                entity -> entity instanceof LivingEntity
                        && entity.isPickable()
                        && entity != player
        );
        return hit == null ? null : (LivingEntity) hit.getEntity();
    }
}
