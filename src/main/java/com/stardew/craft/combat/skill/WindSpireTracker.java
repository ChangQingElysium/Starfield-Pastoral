package com.stardew.craft.combat.skill;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.WindSpirePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class WindSpireTracker {

    private static final float CRIT_BONUS = 0.10f;
    private static final Map<UUID, Long> ACTIVE = new HashMap<>();

    private WindSpireTracker() {}

    public static void start(ServerPlayer player, long nowTick, int durationTicks) {
        if (player == null || durationTicks <= 0) {
            return;
        }
        start(player.getUUID(), nowTick, durationTicks);
    }

    public static float getCritChanceBonus(ServerPlayer player, long nowTick) {
        if (player == null) {
            return 0.0f;
        }
        return getCritChanceBonus(player.getUUID(), nowTick);
    }

    /**
     * The authored gale bonus applies to normal attacks only.
     */
    public static float getCritChanceBonus(
            ServerPlayer player,
            SkillContext context,
            long nowTick
    ) {
        if (player == null) {
            return 0.0f;
        }
        return getCritChanceBonus(player.getUUID(), context, nowTick);
    }

    static void start(UUID playerId, long nowTick, int durationTicks) {
        if (playerId == null || durationTicks <= 0) {
            return;
        }
        ACTIVE.put(playerId, nowTick + durationTicks);
    }

    static float getCritChanceBonus(UUID playerId, long nowTick) {
        Long endTick = ACTIVE.get(playerId);
        if (endTick == null) {
            return 0.0f;
        }
        if (expireIfPast(playerId, nowTick)) {
            return 0.0f;
        }
        return CRIT_BONUS;
    }

    static float getCritChanceBonus(
            UUID playerId,
            SkillContext context,
            long nowTick
    ) {
        if (context == null || !"normal".equals(context.getSkillId())) {
            return 0.0f;
        }
        return getCritChanceBonus(playerId, nowTick);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (expireIfPast(player.getUUID(), player.level().getGameTime())) {
            PacketDistributor.sendToPlayer(
                    player,
                    new WindSpirePayload(false, 0)
            );
        }
    }

    static boolean expireIfPast(UUID playerId, long nowTick) {
        Long endTick = ACTIVE.get(playerId);
        return endTick != null
                && nowTick > endTick
                && ACTIVE.remove(playerId, endTick);
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        ACTIVE.remove(playerId);
    }
}
