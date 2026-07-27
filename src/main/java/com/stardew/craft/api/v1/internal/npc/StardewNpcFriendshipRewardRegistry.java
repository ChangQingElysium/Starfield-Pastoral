package com.stardew.craft.api.v1.internal.npc;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.npc.StardewNpcFriendshipRewards;
import com.stardew.craft.api.v1.npc.StardewNpcInteractions;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Internal friendship reward dispatch. */
public final class StardewNpcFriendshipRewardRegistry {
    private static final String COMPLETION_PREFIX = "api:npc_friendship_reward:";
    private static final Map<ResourceLocation, Registered> HANDLERS = new HashMap<>();
    private static volatile List<Registered> snapshot = List.of();

    private StardewNpcFriendshipRewardRegistry() {
    }

    public static synchronized void register(
            ResourceLocation id,
            int priority,
            StardewNpcFriendshipRewards.Handler handler
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        if (HANDLERS.containsKey(id)) {
            throw new IllegalStateException("NPC friendship reward handler already registered: " + id);
        }
        HANDLERS.put(id, new Registered(id, priority, handler));
        ArrayList<Registered> ordered = new ArrayList<>(HANDLERS.values());
        ordered.sort(Comparator.comparingInt(Registered::priority).reversed()
                .thenComparing(value -> value.id().toString()));
        snapshot = List.copyOf(ordered);
    }

    public static boolean apply(ServerPlayer player, String rawNpcId, int rawPoints) {
        ResourceLocation npcId = StardewNpcInteractions.normalizeNpcId(rawNpcId);
        if (player == null || npcId == null) {
            return false;
        }
        int points = Math.max(0, rawPoints);
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        boolean changed = false;
        for (Registered registered : snapshot) {
            String completionFlag = COMPLETION_PREFIX + registered.id();
            if (data.hasMailFlag(completionFlag)) {
                continue;
            }
            try {
                StardewNpcFriendshipRewards.Outcome outcome = registered.handler().apply(
                        new StardewNpcFriendshipRewards.Context(
                                player, registered.id(), npcId, points, points / 250));
                if (outcome == null) {
                    continue;
                }
                changed |= outcome.changed();
                if (outcome.complete()) {
                    data.addMailFlag(completionFlag);
                    changed = true;
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "NPC friendship reward handler {} failed for {} at {} points",
                        registered.id(), npcId, points, exception);
            }
        }
        if (changed) {
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
        return changed;
    }

    private record Registered(
            ResourceLocation id,
            int priority,
            StardewNpcFriendshipRewards.Handler handler
    ) {
    }
}
