package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.network.DragontoothShivBreathPayload;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import java.util.Objects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

/** One runtime-owned Dragontooth Shiv breath stance window. */
final class DragontoothShivBreathExecutionState
        implements SkillInstance.ExecutionState {
    private final ResourceKey<Level> dimension;
    private final long endTick;
    private boolean settled;

    DragontoothShivBreathExecutionState(
            ResourceKey<Level> dimension,
            long nowTick,
            int durationTicks
    ) {
        if (durationTicks <= 0) {
            throw new IllegalArgumentException(
                    "Dragontooth breath duration must be positive"
            );
        }
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.endTick = nowTick + durationTicks;
    }

    void start(ServerPlayer player, int durationTicks) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                new DragontoothShivBreathPayload(player.getId(), true, durationTicks, player.level().getGameTime())
        );
    }

    void syncTo(ServerPlayer caster, ServerPlayer viewer) {
        long now = caster.level().getGameTime();
        if (!isActive(now, caster.level().dimension(), caster.isAlive()) || endTick <= now) return;
        PacketDistributor.sendToPlayer(viewer,
                new DragontoothShivBreathPayload(caster.getId(), true, (int) (endTick - now), now));
    }

    boolean isActive(
            long nowTick,
            ResourceKey<Level> currentDimension,
            boolean casterAvailable
    ) {
        return !settled
                && casterAvailable
                && dimension.equals(currentDimension)
                && isWithinActiveWindow(nowTick, endTick);
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
            cancel(context.player());
            return SkillTickResult.CANCEL;
        }
        if (isWithinActiveWindow(context.nowTick(), endTick)) {
            return SkillTickResult.CONTINUE;
        }
        cancel(context.player());
        return SkillTickResult.COMPLETE;
    }

    void cancel(ServerPlayer player) {
        if (settled) {
            return;
        }
        settled = true;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                new DragontoothShivBreathPayload(player.getId(), false, 0, player.level().getGameTime())
        );
    }

    static boolean isWithinActiveWindow(
            long nowTick,
            long endTick
    ) {
        return nowTick <= endTick;
    }
}
