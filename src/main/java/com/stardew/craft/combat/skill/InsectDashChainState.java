package com.stardew.craft.combat.skill;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.skill.runtime.DeferredSkillCooldown;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 昆虫头部 - 甲翼疾掠：连续突进的链式状态
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class InsectDashChainState {

    public static final int CHAIN_WINDOW_TICKS = 40; // 2s 内可继续连段
    private static final class State {
        private final int stage;
        private final long expireTick;
        private final ResourceKey<Level> dimension;
        private final DeferredSkillCooldown cooldown;

        private State(
                int stage,
                long expireTick,
                ResourceKey<Level> dimension,
                DeferredSkillCooldown cooldown
        ) {
            this.stage = stage;
            this.expireTick = expireTick;
            this.dimension = dimension;
            this.cooldown = cooldown;
        }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private InsectDashChainState() {}

    public static int getCurrentStage(ServerPlayer player, long nowTick) {
        if (player == null) return 0;
        State state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return 0;
        }
        boolean sameDimension = player.level().dimension().equals(state.dimension);
        if (!shouldRemainActive(state.expireTick, nowTick, sameDimension)) {
            settleCooldown(player, state, nowTick);
            return 0;
        }
        return state.stage;
    }

    public static int getNextStage(ServerPlayer player, long nowTick) {
        return nextStageFor(getCurrentStage(player, nowTick));
    }

    public static void setStage(
            ServerPlayer player,
            long nowTick,
            int stage,
            DeferredSkillCooldown cooldown
    ) {
        if (player == null || cooldown == null) return;
        if (stage <= 0 || stage >= 3) {
            clear(player);
            return;
        }
        State previous = ACTIVE.put(player.getUUID(), new State(
                stage,
                nowTick + CHAIN_WINDOW_TICKS,
                player.level().dimension(),
                cooldown
        ));
        if (previous != null && previous.cooldown != cooldown) {
            WeaponSkillRuntime.abandonDeferredCooldown(previous.cooldown);
        }
    }

    public static void clear(ServerPlayer player) {
        if (player == null) return;
        State state = ACTIVE.remove(player.getUUID());
        if (state != null) {
            WeaponSkillRuntime.abandonDeferredCooldown(state.cooldown);
        }
    }

    public static void cancel(ServerPlayer player, long nowTick) {
        if (player == null) return;
        State state = ACTIVE.get(player.getUUID());
        if (state != null) {
            settleCooldown(player, state, nowTick);
        }
    }

    public static void tick(ServerPlayer player, long nowTick) {
        getCurrentStage(player, nowTick);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event
    ) {
        if (event.getEntity() instanceof ServerPlayer player) {
            cancel(player, player.level().getGameTime());
        }
    }

    static int nextStageFor(int currentStage) {
        return Math.min(3, Math.max(0, currentStage) + 1);
    }

    static boolean shouldRemainActive(
            long expireTick,
            long nowTick,
            boolean sameDimension
    ) {
        return sameDimension && nowTick <= expireTick;
    }

    private static void settleCooldown(
            ServerPlayer player,
            State state,
            long nowTick
    ) {
        ACTIVE.remove(player.getUUID());
        WeaponSkillRuntime.commitDeferredCooldown(
                player,
                state.cooldown,
                nowTick
        );
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        State state = ACTIVE.remove(playerId);
        if (state != null) {
            WeaponSkillRuntime.abandonDeferredCooldown(state.cooldown);
        }
    }
}
