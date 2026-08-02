package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.network.SteelSpineFuryEnterPayload;
import com.stardew.craft.combat.network.SteelSpineFuryHitPayload;
import com.stardew.craft.combat.network.SteelSpineFuryPayload;
import com.stardew.craft.combat.skill.runtime.DeferredSkillCooldown;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import java.util.Objects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/** One Steel Spine stance, its charge, pending strike, and cooldown. */
final class SteelSpineFuryExecutionState
        implements SkillInstance.ExecutionState {
    private final ResourceKey<Level> dimension;
    private long endTick;
    private final DeferredSkillCooldown cooldown;
    private boolean tookHit;
    private boolean ready;
    private boolean weak;
    private int bonusDamage;
    private boolean consumed;
    private boolean interrupted;
    private long nextReservationId;
    private Long activeReservation;

    SteelSpineFuryExecutionState(
            ResourceKey<Level> dimension,
            long nowTick,
            int durationTicks,
            DeferredSkillCooldown cooldown
    ) {
        if (durationTicks <= 0) {
            throw new IllegalArgumentException(
                    "Steel Spine duration must be positive"
            );
        }
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.endTick = nowTick + durationTicks;
        this.cooldown = Objects.requireNonNull(cooldown, "cooldown");
    }

    void start(ServerPlayer player, int durationTicks) {
        PacketDistributor.sendToPlayer(
                player,
                new SteelSpineFuryPayload(true, durationTicks)
        );
        PacketDistributor.sendToPlayer(
                player,
                new SteelSpineFuryEnterPayload()
        );
    }

    @SuppressWarnings("null")
    void onDamageTaken(
            ServerPlayer player,
            long nowTick,
            int stardewDamage
    ) {
        if (interrupted
                || stardewDamage <= 0
                || nowTick > endTick
                || tookHit
                || !isCasterContextValid(player)) {
            return;
        }

        tookHit = true;
        ready = true;
        weak = false;
        bonusDamage = calculateBonusDamage(stardewDamage);
        endTick = nowTick;

        commitCooldownAndClose(player, nowTick);
        player.playSound(
                SoundEvents.AMETHYST_BLOCK_CHIME,
                1.0F,
                1.8F
        );
        PacketDistributor.sendToPlayer(
                player,
                new SteelSpineFuryHitPayload()
        );
    }

    SteelSpineFurySkillHandler.AttackBoost consumeAttack(
            ServerPlayer player,
            long nowTick
    ) {
        SteelSpineFurySkillHandler.AttackReservation reservation =
                reserveAttack(player, nowTick);
        if (reservation == null) {
            return null;
        }
        reservation.commit().run();
        return reservation.boost();
    }

    SteelSpineFurySkillHandler.AttackReservation reserveAttack(
            ServerPlayer player,
            long nowTick
    ) {
        if (interrupted
                || !ready
                || activeReservation != null
                || !isCasterContextValid(player)) {
            return null;
        }

        SteelSpineFurySkillHandler.AttackBoost boost =
                createAttackBoost(weak, bonusDamage);
        long reservationId = ++nextReservationId;
        activeReservation = reservationId;
        return new SteelSpineFurySkillHandler.AttackReservation(
                boost,
                () -> commitReservation(reservationId),
                () -> releaseReservation(reservationId)
        );
    }

    private void commitReservation(long reservationId) {
        if (activeReservation != null && activeReservation == reservationId) {
            activeReservation = null;
            ready = false;
            consumed = true;
        }
    }

    private void releaseReservation(long reservationId) {
        if (activeReservation != null && activeReservation == reservationId) {
            activeReservation = null;
        }
    }

    SkillTickResult advance(SkillExecutionContext context) {
        if (interrupted || isCompleted()) {
            return SkillTickResult.COMPLETE;
        }
        if (!context.player().isAlive()
                || context.player().isRemoved()
                || !dimension.equals(
                        context.player().level().dimension()
                )) {
            interrupt(context.player(), context.nowTick());
            return SkillTickResult.CANCEL;
        }

        if (context.nowTick() > endTick) {
            if (!ready) {
                ready = true;
                weak = true;
                bonusDamage = 0;
            }
            commitCooldownAndClose(
                    context.player(),
                    context.nowTick()
            );
        }
        return isCompleted()
                ? SkillTickResult.COMPLETE
                : SkillTickResult.CONTINUE;
    }

    void interrupt(ServerPlayer player, long nowTick) {
        if (interrupted) {
            return;
        }
        interrupted = true;
        commitCooldownAndClose(player, nowTick);
    }

    static int calculateBonusDamage(int stardewDamage) {
        return Math.min(
                SteelSpineFurySkillHandler.MAX_BONUS_DAMAGE,
                (int) Math.ceil(
                        stardewDamage
                                * SteelSpineFurySkillHandler.BONUS_RATIO
                )
        );
    }

    static SteelSpineFurySkillHandler.AttackBoost createAttackBoost(
            boolean weak,
            int bonusDamage
    ) {
        return weak
                ? new SteelSpineFurySkillHandler.AttackBoost(
                        false,
                        0,
                        SteelSpineFurySkillHandler.FALLBACK_MULTIPLIER
                )
                : new SteelSpineFurySkillHandler.AttackBoost(
                        true,
                        bonusDamage,
                        1.0F
                );
    }

    private boolean isCompleted() {
        return cooldown.committed() && consumed;
    }

    private boolean isCasterContextValid(ServerPlayer player) {
        return player.isAlive()
                && !player.isRemoved()
                && dimension.equals(player.level().dimension());
    }

    private void commitCooldownAndClose(
            ServerPlayer player,
            long nowTick
    ) {
        if (WeaponSkillRuntime.commitDeferredCooldown(
                player,
                cooldown,
                nowTick
        )) {
            PacketDistributor.sendToPlayer(
                    player,
                    new SteelSpineFuryPayload(false, 0)
            );
        }
    }
}
