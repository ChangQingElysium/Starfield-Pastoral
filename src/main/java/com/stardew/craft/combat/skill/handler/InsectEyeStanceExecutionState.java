package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.network.InsectEyeStancePayload;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.runtime.DeferredSkillCooldown;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import java.util.Objects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/** One Compound Eye window, its first-hit token, and deferred cooldown. */
final class InsectEyeStanceExecutionState
        implements SkillInstance.ExecutionState {
    private final ResourceKey<Level> dimension;
    private final long endTick;
    private final String skillId;
    private final DeferredSkillCooldown cooldown;
    private boolean firstHitPending = true;
    private long nextReservationId;
    private Long firstHitReservation;
    private boolean settled;

    InsectEyeStanceExecutionState(
            ResourceKey<Level> dimension,
            long nowTick,
            int durationTicks,
            String skillId,
            DeferredSkillCooldown cooldown
    ) {
        if (durationTicks <= 0) {
            throw new IllegalArgumentException(
                    "Compound Eye duration must be positive"
            );
        }
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.endTick = nowTick + durationTicks;
        this.skillId = Objects.requireNonNull(skillId, "skillId");
        this.cooldown = Objects.requireNonNull(cooldown, "cooldown");
    }

    void start(ServerPlayer player, int durationTicks) {
        PacketDistributor.sendToPlayer(
                player,
                new InsectEyeStancePayload(true, durationTicks)
        );
    }

    SkillContext consumeAttack(ServerPlayer player, long nowTick) {
        InsectEyeStanceSkillHandler.AttackReservation reservation =
                reserveAttack(player, nowTick);
        if (reservation == null) {
            return null;
        }
        reservation.commit().run();
        return reservation.skillContext();
    }

    InsectEyeStanceSkillHandler.AttackReservation reserveAttack(
            ServerPlayer player,
            long nowTick
    ) {
        if (!isActive(
                nowTick,
                player.level().dimension(),
                player.isAlive() && !player.isRemoved()
        )) {
            settle(player, nowTick);
            return null;
        }

        long reservationId = ++nextReservationId;
        boolean guaranteedCrit = firstHitPending
                && firstHitReservation == null;
        if (guaranteedCrit) {
            firstHitReservation = reservationId;
        }
        return new InsectEyeStanceSkillHandler.AttackReservation(
                createSkillContext(skillId, guaranteedCrit),
                () -> commitReservation(reservationId),
                () -> releaseReservation(reservationId)
        );
    }

    private void commitReservation(long reservationId) {
        if (firstHitReservation != null
                && firstHitReservation == reservationId) {
            firstHitPending = false;
            firstHitReservation = null;
        }
    }

    private void releaseReservation(long reservationId) {
        if (firstHitReservation != null
                && firstHitReservation == reservationId) {
            firstHitReservation = null;
        }
    }

    SkillTickResult advance(SkillExecutionContext context) {
        if (settled) {
            return SkillTickResult.COMPLETE;
        }
        if (!context.player().isAlive()
                || context.player().isRemoved()
                || !dimension.equals(
                        context.player().level().dimension()
                )) {
            settle(context.player(), context.nowTick());
            return SkillTickResult.CANCEL;
        }
        if (shouldRemainActive(
                endTick,
                context.nowTick(),
                true
        )) {
            return SkillTickResult.CONTINUE;
        }

        settle(context.player(), context.nowTick());
        return SkillTickResult.COMPLETE;
    }

    void cancel(ServerPlayer player, long nowTick) {
        settle(player, nowTick);
    }

    boolean isActive(
            long nowTick,
            ResourceKey<Level> currentDimension,
            boolean casterAvailable
    ) {
        return !settled
                && casterAvailable
                && shouldRemainActive(
                        endTick,
                        nowTick,
                        dimension.equals(currentDimension)
                );
    }

    private void settle(ServerPlayer player, long nowTick) {
        if (settled) {
            return;
        }
        settled = true;
        WeaponSkillRuntime.commitDeferredCooldown(
                player,
                cooldown,
                nowTick
        );
        PacketDistributor.sendToPlayer(
                player,
                new InsectEyeStancePayload(false, 0)
        );
    }

    static SkillContext createSkillContext(
            String skillId,
            boolean guaranteedCrit
    ) {
        return SkillContext.builder()
                .skillId(skillId)
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(
                        InsectEyeStanceSkillHandler.DAMAGE_MULTIPLIER
                )
                .guaranteedCrit(guaranteedCrit)
                .build();
    }

    static boolean shouldRemainActive(
            long endTick,
            long nowTick,
            boolean sameDimension
    ) {
        return sameDimension && nowTick <= endTick;
    }
}
