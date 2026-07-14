package com.stardew.craft.shop;

import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.cutscene.server.EventSeenData;
import com.stardew.craft.museum.MuseumDonationData;
import com.stardew.craft.museum.MuseumDonationItems;
import com.stardew.craft.museum.MuseumRewardRegistry;
import com.stardew.craft.network.MuseumDonationSyncPacket;
import com.stardew.craft.network.payload.OpenGuntherMenuPayload;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import com.stardew.craft.sewer.SewerStoryFlags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server-side handler for Gunther's museum interactions.
 * SDV parity: Gunther shows a question dialog:
 * - If donation mode is NOT active: "Donate" (if has items) / "Leave"
 * - If donation mode IS active: "End Donation" / "Leave"
 */
@SuppressWarnings("null")
public final class GuntherService {

    // Counter area: (107,40,41) to (114,37,44) — museum
    private static final int COUNTER_MIN_X = 107;
    private static final int COUNTER_MAX_X = 114;
    private static final int COUNTER_MIN_Y = 37;
    private static final int COUNTER_MAX_Y = 40;
    private static final int COUNTER_MIN_Z = 41;
    private static final int COUNTER_MAX_Z = 44;

    private GuntherService() {}

    public static boolean isPlayerAtCounter(ServerPlayer player) {
        int px = (int) Math.floor(player.getX());
        int py = (int) Math.floor(player.getY());
        int pz = (int) Math.floor(player.getZ());
        boolean result = px >= COUNTER_MIN_X && px <= COUNTER_MAX_X
            && py >= COUNTER_MIN_Y && py <= COUNTER_MAX_Y
            && pz >= COUNTER_MIN_Z && pz <= COUNTER_MAX_Z;
        return result;
    }

    public static InteractionResult handleGuntherInteraction(ServerPlayer player, StardewNpcEntity gunther) {
        gunther.setYRot(90f);
        gunther.setYHeadRot(90f);

        MuseumDonationData data = MuseumDonationData.get(player.serverLevel());
        UUID playerId = player.getUUID();
        boolean donationActive = data.isDonationModeActive(playerId);
        boolean hasDonatable = !donationActive && playerHasDonatableItem(player);

        if (!donationActive && !hasDonatable) {
            // No donatable items and not in donation mode: show normal dialogue
            // SDV parity: "You don't have anything to donate right now."
            PacketDistributor.sendToPlayer(player, new OpenNpcDialogueScreenPayload(
                "gunther",
                "stardewcraft.npc.gunther.dialogue.nothing_to_donate",
                0
            ));
            return InteractionResult.SUCCESS;
        }

        PacketDistributor.sendToPlayer(player, new OpenGuntherMenuPayload(donationActive, hasDonatable));
        return InteractionResult.SUCCESS;
    }

    /**
     * Handle the player's choice from Gunther's question dialog.
     * 0 = Start donation mode
     * 1 = End donation mode
     */
    public static void handleChoice(ServerPlayer player, int choice) {
        switch (choice) {
            case 0 -> startDonation(player);
            case 1 -> endDonation(player);
        }
    }

    private static void startDonation(ServerPlayer player) {
        MuseumDonationData data = MuseumDonationData.get(player.serverLevel());
        UUID playerId = player.getUUID();
        if (data.isDonationModeActive(playerId)) return;
        data.startDonationMode(playerId);
        data.ensureManagedStandLayout(player.serverLevel(), playerId);
        syncDonations(data, player);
        // SDV parity: Gunther tells the player to come back when done
        PacketDistributor.sendToPlayer(player, new OpenNpcDialogueScreenPayload(
            "gunther",
            "stardewcraft.npc.gunther.donation_started",
            0
        ));
    }

    private static void endDonation(ServerPlayer player) {
        MuseumDonationData data = MuseumDonationData.get(player.serverLevel());
        UUID playerId = player.getUUID();
        if (!data.isDonationModeActive(playerId)) return;
        MuseumDonationData.EndSessionResult result = data.endDonationMode(playerId);
        syncDonations(data, player);

        if (!result.success()) {
            PacketDistributor.sendToPlayer(player, new OpenNpcDialogueScreenPayload(
                "gunther",
                "stardewcraft.npc.gunther.donation_ended",
                0
            ));
            return;
        }

        boolean grantedRewards = grantUnclaimedMuseumRewards(player, data);
        if (!grantedRewards) {
            PacketDistributor.sendToPlayer(player, new OpenNpcDialogueScreenPayload(
                "gunther",
                "stardewcraft.npc.gunther.donation_ended",
                0
            ));
        }
    }

    private static boolean grantUnclaimedMuseumRewards(ServerPlayer player, MuseumDonationData data) {
        UUID playerId = player.getUUID();
        List<MuseumRewardRegistry.MuseumReward> claimable =
            MuseumRewardRegistry.getClaimableRewards(data, playerId, data.getClaimedMuseumRewards(playerId));

        if (claimable.isEmpty()) {
            return false;
        }

        boolean queuedRustyKeyEvent = false;
        for (MuseumRewardRegistry.MuseumReward reward : claimable) {
            if (MuseumRewardRegistry.RUSTY_KEY_REWARD_ID.equals(reward.id())) {
                EventSeenData.get(player.serverLevel()).markSeen(playerId, SewerStoryFlags.RUSTY_KEY_EVENT_READY);
                queuedRustyKeyEvent = true;
            }
            var actionContext = com.stardew.craft.api.v1.action.StardewActionContext.forPlayer(player);
            boolean actionFailed = false;
            for (var action : reward.actions()) {
                var actionResult = com.stardew.craft.api.v1.action.StardewActions.execute(action, actionContext)
                        .resultOrPartial(message -> com.stardew.craft.StardewCraft.LOGGER.error(
                                "[Museum reward] {} failed for {}: {}", reward.id(), player.getName().getString(), message))
                        .orElse(null);
                if (actionResult == null || !actionResult.success()) {
                    actionFailed = true;
                    break;
                }
            }
            if (actionFailed) continue;
            data.claimReward(playerId, reward.id());
        }

        PacketDistributor.sendToPlayer(player, new OpenNpcDialogueScreenPayload(
            "gunther",
            queuedRustyKeyEvent ? "stardewcraft.npc.gunther.rusty_key_pending" : "stardewcraft.npc.gunther.reward_granted",
            0
        ));
        return true;
    }

    private static void syncDonations(MuseumDonationData data, ServerPlayer player) {
        UUID playerId = player.getUUID();
        List<String> ids = new ArrayList<>(data.getDonatedItems(playerId));
        PacketDistributor.sendToPlayer(player, new MuseumDonationSyncPacket(ids));
        com.stardew.craft.block.utility.MuseumExhibitStandBlock.syncStands(player.serverLevel(), data, player);
    }

    /**
     * Check if the player has any mineral/artifact items that haven't been donated yet.
     */
    public static boolean playerHasDonatableItem(ServerPlayer player) {
        MuseumDonationData data = MuseumDonationData.get(player.serverLevel());
        UUID playerId = player.getUUID();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            if (!MuseumDonationItems.isDonatable(stack)) continue;
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (!data.isDonated(playerId, itemId)) return true;
        }
        return false;
    }
}
