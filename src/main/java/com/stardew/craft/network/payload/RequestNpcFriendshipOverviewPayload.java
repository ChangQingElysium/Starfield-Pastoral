package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.npc.StardewNpcProfiles;
import com.stardew.craft.npc.data.NpcCapabilityProfile;
import com.stardew.craft.npc.data.NpcDataRegistry;
import com.stardew.craft.npc.data.NpcSocialRules;
import com.stardew.craft.npc.runtime.NpcFriendshipDataManager;
import com.stardew.craft.time.StardewTimeManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Client -> server request for V-menu social tab friendship data.
 */
public record RequestNpcFriendshipOverviewPayload() implements CustomPacketPayload {
    @SuppressWarnings("null")
    public static final Type<RequestNpcFriendshipOverviewPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "request_npc_friendship_overview"));

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, RequestNpcFriendshipOverviewPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
        },
        buf -> new RequestNpcFriendshipOverviewPayload()
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestNpcFriendshipOverviewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            sendOverviewTo(player);
        });
    }

    /**
     * Build and send the current friendship overview to a specific player. Callable outside
     * of a request/response (e.g. from PlayerLoggedInEvent) so precondition checks that read
     * NpcFriendshipClientCache don't silently fail while the client waits for the user to
     * open the social menu.
     */
    public static void sendOverviewTo(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        int dayKey = currentDayKey();
        int weekKey = currentWeekKey(dayKey);
        NpcFriendshipDataManager friendshipManager = NpcFriendshipDataManager.get(serverLevel);
        Map<String, NpcCapabilityProfile> capabilities = NpcDataRegistry.capabilities();
        boolean normalizedAnyWeek = false;

        List<SyncNpcFriendshipOverviewPayload.Entry> rows = new ArrayList<>();
        for (ResourceLocation npcId : StardewNpcProfiles.ids()) {
            String normalizedNpcId = StardewCraft.MODID.equals(npcId.getNamespace())
                    ? npcId.getPath()
                    : npcId.toString();
            NpcCapabilityProfile profile = capabilities.get(normalizedNpcId);
            NpcFriendshipDataManager.FriendshipState state = friendshipManager.get(player.getUUID(), normalizedNpcId);
            if (!NpcSocialRules.shouldShowOnSocialPage(normalizedNpcId, profile, state, player)) {
                continue;
            }
            boolean met = NpcSocialRules.isMet(state);
            boolean persistentState = state != null;
            if (state == null) {
                if (NpcSocialRules.shouldCreateFriendshipForSocialPage(normalizedNpcId)) {
                    state = friendshipManager.getOrCreate(player.getUUID(), normalizedNpcId);
                    persistentState = true;
                    met = NpcSocialRules.isMet(state);
                } else {
                    state = new NpcFriendshipDataManager.FriendshipState();
                }
            }

            if (state.lastGiftWeekKey() != weekKey) {
                state.normalizeGiftWeek(weekKey);
                normalizedAnyWeek |= persistentState;
            }
            int points = Math.max(0, state.points());
            int hearts = Math.max(0, Math.min(14, points / 250));
            int gifts = Math.max(0, Math.min(2, state.giftsThisWeek()));
            boolean giftedToday = state.lastGiftDayKey() == dayKey;
            boolean talkedToday = state.lastTalkDayKey() == dayKey;
            int metOrder = state.firstMetDayKey() == Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, state.firstMetDayKey());
            rows.add(new SyncNpcFriendshipOverviewPayload.Entry(
                normalizedNpcId,
                met,
                NpcSocialRules.canReceiveGifts(normalizedNpcId, player),
                points,
                hearts,
                gifts,
                giftedToday,
                talkedToday,
                metOrder
            ));
        }

        rows.sort(Comparator
            .comparingInt(SyncNpcFriendshipOverviewPayload.Entry::points).reversed()
            .thenComparingInt(SyncNpcFriendshipOverviewPayload.Entry::metOrder)
            .thenComparing(SyncNpcFriendshipOverviewPayload.Entry::npcId));
        if (normalizedAnyWeek) {
            friendshipManager.setDirty();
        }
        PacketDistributor.sendToPlayer(player, new SyncNpcFriendshipOverviewPayload(rows));
    }

    private static int currentDayKey() {
        StardewTimeManager tm = StardewTimeManager.get();
        return (tm.getCurrentYear() - 1) * 112 + tm.getCurrentSeason() * 28 + tm.getCurrentDay();
    }

    private static int currentWeekKey(int dayKey) {
        return dayKey / 7;
    }
}
