package com.stardew.craft.shop;

import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.ItemPickupHudPacket;
import com.stardew.craft.network.payload.PrizeTicketClaimResultPayload;
import com.stardew.craft.network.payload.PrizeTicketRewardPreview;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Server-side Prize Machine reward logic, matching SDV PrizeTicketMenu reward order. */
@SuppressWarnings("null")
public final class PrizeTicketRewardService {
    private static final int MAX_SKIP_SCAN = 32;

    private PrizeTicketRewardService() {}

    public record PrizeReward(ItemStack stack, int prizeLevel) {}

    public static Optional<PrizeReward> getNextAvailableReward(ServerPlayer player, int startingPrizeLevel) {
        int start = Math.max(0, startingPrizeLevel);
        for (int prizeLevel = start; prizeLevel < start + MAX_SKIP_SCAN; prizeLevel++) {
            ItemStack stack = getRewardForPrizeLevel(player, prizeLevel);
            if (!stack.isEmpty()) {
                return Optional.of(new PrizeReward(stack, prizeLevel));
            }
        }
        return Optional.empty();
    }

    public static List<PrizeTicketRewardPreview> getPreviewRewards(ServerPlayer player, int startingPrizeLevel, int count) {
        List<PrizeTicketRewardPreview> previews = new ArrayList<>();
        int nextPrizeLevel = Math.max(0, startingPrizeLevel);
        for (int index = 0; index < count; index++) {
            Optional<PrizeReward> reward = getNextAvailableReward(player, nextPrizeLevel);
            if (reward.isEmpty()) {
                break;
            }
            PrizeReward preview = reward.get();
            ItemStack stack = preview.stack();
            previews.add(new PrizeTicketRewardPreview(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                stack.getCount(),
                preview.prizeLevel()
            ));
            nextPrizeLevel = preview.prizeLevel() + 1;
        }
        return previews;
    }

    public static void handlePrizeTicketClaim(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        int previousClaimed = data.getTicketPrizesClaimed();
        Optional<PrizeReward> reward = getNextAvailableReward(player, previousClaimed);
        if (reward.isEmpty() || !consumePrizeTicket(player)) {
            PacketDistributor.sendToPlayer(player,
                new PrizeTicketClaimResultPayload(false, "", 0, previousClaimed, previousClaimed, -1,
                    getPreviewRewards(player, previousClaimed, 4)));
            return;
        }

        PrizeReward claimed = reward.get();
        ItemStack prize = claimed.stack().copy();
        ItemStack hudStack = prize.copy();
        data.setTicketPrizesClaimed(claimed.prizeLevel() + 1);
        PlayerDataEventHandler.syncPlayerData(player, data);

        if (!player.getInventory().add(prize)) {
            player.drop(prize, false);
        }
        ItemPickupHudPacket.sendTo(player, hudStack, hudStack.getCount(), false);
        player.inventoryMenu.broadcastChanges();

        String itemId = BuiltInRegistries.ITEM.getKey(hudStack.getItem()).toString();
        PacketDistributor.sendToPlayer(player, new PrizeTicketClaimResultPayload(
            true,
            itemId,
            hudStack.getCount(),
            previousClaimed,
            data.getTicketPrizesClaimed(),
            claimed.prizeLevel(),
            getPreviewRewards(player, data.getTicketPrizesClaimed(), 4)
        ));
    }

    private static boolean consumePrizeTicket(ServerPlayer player) {
        Item ticket = ModItems.PRIZE_TICKET.get();
        if (player.getInventory().countItem(ticket) <= 0) {
            return false;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ticket)) {
                stack.shrink(1);
                player.inventoryMenu.broadcastChanges();
                return true;
            }
        }
        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(ticket)) {
            offhand.shrink(1);
            player.inventoryMenu.broadcastChanges();
            return true;
        }
        return false;
    }

    private static ItemStack getRewardForPrizeLevel(ServerPlayer player, int prizeLevel) {
        if (prizeLevel < 0) {
            return ItemStack.EMPTY;
        }
        int seedLevel = prizeLevel < 22 ? prizeLevel : prizeLevel - prizeLevel % 9;
        return PrizeTicketRewardData.resolve(player, prizeLevel, randomFor(player, seedLevel))
                .orElse(ItemStack.EMPTY);
    }

    private static Random randomFor(ServerPlayer player, int prizeLevel) {
        long uuidBits = player.getUUID().getMostSignificantBits() ^ player.getUUID().getLeastSignificantBits();
        long seed = player.serverLevel().getSeed() ^ uuidBits ^ ((long) prizeLevel * 0x9E3779B97F4A7C15L);
        return new Random(seed);
    }
}
