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
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import com.stardew.craft.museum.MuseumQuestService;
import com.stardew.craft.player.PlayerDataManager;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
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
        MuseumQuestService.syncDonationMailFlags(player);

        MuseumDonationData data = MuseumDonationData.get(player.serverLevel());
        UUID playerId = player.getUUID();
        boolean donationActive = data.isDonationModeActive(playerId);
        boolean hasDonatable = !donationActive && playerHasDonatableItem(player);

        if (!donationActive && !hasDonatable) {
            // 补领入口：此前因背包满等原因没领到的奖励，再次与 Gunther 交谈时重新发放
            if (grantUnclaimedMuseumRewards(player, data)) {
                return InteractionResult.SUCCESS;
            }
            // 原版 LibraryMuseum.OpenGuntherDialogueMenu 三个分支：
            // 成就 5（集齐全部展品）→ MuseumComplete；已发现古物（artifactFound）→ NothingToDonate；否则 NoArtifactsFound
            String key;
            if (isMuseumComplete(player, data)) {
                key = "stardewcraft.npc.gunther.dialogue.museum_complete";
            } else if (PlayerDataManager.getPlayerData(player).hasMailFlag(MuseumQuestService.FIRST_ARTIFACT_FLAG)) {
                key = "stardewcraft.npc.gunther.dialogue.nothing_to_donate";
            } else {
                key = "stardewcraft.npc.gunther.dialogue.no_artifacts_found";
            }
            com.stardew.craft.npc.runtime.NpcInteractionService.sendDialogue(player, new OpenNpcDialogueScreenPayload(
                "gunther",
                key,
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
        com.stardew.craft.npc.runtime.NpcInteractionService.sendDialogue(player, new OpenNpcDialogueScreenPayload(
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
        MuseumQuestService.syncDonationMailFlags(player);

        if (!result.success()) {
            com.stardew.craft.npc.runtime.NpcInteractionService.sendDialogue(player, new OpenNpcDialogueScreenPayload(
                "gunther",
                "stardewcraft.npc.gunther.donation_ended",
                0
            ));
            return;
        }

        boolean grantedRewards = grantUnclaimedMuseumRewards(player, data);
        if (!grantedRewards) {
            com.stardew.craft.npc.runtime.NpcInteractionService.sendDialogue(player, new OpenNpcDialogueScreenPayload(
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

        // 原版 CanCollectReward：已能读懂矮人语时不再发矮人语指南(326)，也不会重复领取
        if (DwarfService.canUnderstandDwarves(player)) {
            claimable = claimable.stream()
                .filter(reward -> !reward.actions().toString().contains(DwarfService.SPECIAL_ITEM_ID))
                .toList();
        }

        if (claimable.isEmpty()) {
            return false;
        }

        boolean queuedRustyKeyEvent = false;
        boolean anyFailed = false;
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
            if (actionFailed) {
                anyFailed = true;
                continue;
            }
            data.claimReward(playerId, reward.id());
        }

        String dialogueKey = queuedRustyKeyEvent ? "stardewcraft.npc.gunther.rusty_key_pending"
            : anyFailed ? "stardewcraft.npc.gunther.reward_pending"
            : "stardewcraft.npc.gunther.reward_granted";
        com.stardew.craft.npc.runtime.NpcInteractionService.sendDialogue(player, new OpenNpcDialogueScreenPayload(
            "gunther",
            dialogueKey,
            0
        ));
        return true;
    }

    private static volatile Set<String> allDonatableIds;

    /** 原版成就 5（A Complete Collection）：全部可捐展品都已捐赠（项目无成就系统，以捐赠集合直接判定）。 */
    private static boolean isMuseumComplete(ServerPlayer player, MuseumDonationData data) {
        Set<String> all = allDonatableIds;
        if (all == null) {
            Set<String> computed = new HashSet<>();
            for (Item item : BuiltInRegistries.ITEM) {
                var id = BuiltInRegistries.ITEM.getKey(item);
                if (!com.stardew.craft.StardewCraft.MODID.equals(id.getNamespace())) continue;
                if ("lost_book".equals(id.getPath())) continue;
                if (MuseumDonationItems.isDonatable(new ItemStack(item))) computed.add(id.toString());
            }
            if (computed.isEmpty()) return false;
            allDonatableIds = all = computed;
        }
        return data.getDonatedItems(player.getUUID()).containsAll(all);
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
