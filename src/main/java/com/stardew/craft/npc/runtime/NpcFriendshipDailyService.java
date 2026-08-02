package com.stardew.craft.npc.runtime;

import com.stardew.craft.api.v1.npc.StardewNpcProfiles;
import com.stardew.craft.npc.data.NpcCapabilityProfile;
import com.stardew.craft.npc.data.NpcDataRegistry;
import net.minecraft.server.level.ServerLevel;

/**
 * Source-parity friendship settlement for the non-romance friendship stage.
 */
public final class NpcFriendshipDailyService {
    static final int DATABLE_DECAY_LIMIT = 2_000;
    static final int NON_DATABLE_DECAY_LIMIT = 2_500;

    private NpcFriendshipDailyService() {
    }

    public static void onNewDay(ServerLevel level, int previousDayKey, int newDayKey) {
        NpcFriendshipDataManager.get(level).settleNewDay(
                previousDayKey,
                newDayKey / 7,
                NpcFriendshipDailyService::isKnownNpc,
                NpcFriendshipDailyService::isDatableNpc,
                NpcInteractionService::getMaxFriendshipPointsFor
        );
    }

    static int calculateDailyFriendshipDelta(int points, boolean talkedYesterday, boolean datable) {
        if (talkedYesterday) {
            return 0;
        }
        int decayLimit = datable ? DATABLE_DECAY_LIMIT : NON_DATABLE_DECAY_LIMIT;
        return points < decayLimit ? -2 : 0;
    }

    private static boolean isKnownNpc(String npcId) {
        return StardewNpcProfiles.resolve(npcId).isPresent()
                || NpcDataRegistry.capabilities().containsKey(npcId);
    }

    private static boolean isDatableNpc(String npcId) {
        NpcCapabilityProfile profile = NpcDataRegistry.capabilities().get(npcId);
        return StardewNpcProfiles.resolve(npcId)
                .map(definition -> definition.profile().datable())
                .orElse(profile != null && profile.datable());
    }
}
