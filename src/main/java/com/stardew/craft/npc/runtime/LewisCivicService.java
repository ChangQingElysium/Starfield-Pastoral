package com.stardew.craft.npc.runtime;

import com.stardew.craft.item.ModItems;
import com.stardew.craft.auction.AuctionService;
import com.stardew.craft.lostandfound.LostAndFoundService;
import com.stardew.craft.network.payload.OpenLewisConfirmPayload;
import com.stardew.craft.network.payload.OpenLewisMenuPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

public final class LewisCivicService {
    private static final String STAT_CONTRACT_INTRODUCED = "stardewcraft.money_contract.introduced";
    private static final String STAT_CONTRACT_LAST_CLAIM_DAY = "stardewcraft.money_contract.last_claim_day";

    private LewisCivicService() {
    }

    public static void openMenu(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new OpenLewisMenuPayload());
    }

    public static void handleMenuChoice(ServerPlayer player, int choice) {
        switch (choice) {
            case 0 -> requestMoneyContract(player);
            case 1 -> AuctionService.openCreateScreen(player);
            case 2 -> AuctionService.openJoinList(player);
            case 3 -> AuctionService.requestCancelAuction(player);
            case 4 -> FarmCancellationService.requestCancellation(player);
            case 5 -> LostAndFoundService.open(player);
            default -> {
            }
        }
    }

    private static void requestMoneyContract(ServerPlayer player) {
        int today = currentAbsoluteDay();
        boolean introduced = PlayerStardewDataAPI.getStat(player, STAT_CONTRACT_INTRODUCED) > 0;
        boolean claimedToday = PlayerStardewDataAPI.getStat(player, STAT_CONTRACT_LAST_CLAIM_DAY) == today;

        String questionKey = claimedToday
            ? "stardewcraft.lewis.money_contract.claimed_today"
            : introduced
                ? "stardewcraft.lewis.money_contract.repeat_question"
                : "stardewcraft.lewis.money_contract.intro_question";

        if (!introduced) {
            PlayerStardewDataAPI.setStat(player, STAT_CONTRACT_INTRODUCED, 1);
        }

        PacketDistributor.sendToPlayer(player, new OpenLewisConfirmPayload(
            UUID.randomUUID(),
            OpenLewisConfirmPayload.KIND_MONEY_CONTRACT_CLAIM,
            questionKey,
            List.of(),
            claimedToday ? "stardewcraft.dialog.ok" : "stardewcraft.lewis.money_contract.claim",
            claimedToday ? "stardewcraft.dialog.close" : "stardewcraft.dialog.no"));
    }

    public static void handleMoneyContractClaimConfirm(ServerPlayer player, boolean accepted) {
        if (!accepted) {
            player.playNotifySound(ModSounds.CANCEL.get(), SoundSource.PLAYERS, 0.40f, 0.94f);
            return;
        }
        int today = currentAbsoluteDay();
        if (PlayerStardewDataAPI.getStat(player, STAT_CONTRACT_LAST_CLAIM_DAY) == today) {
            player.playNotifySound(ModSounds.CANCEL.get(), SoundSource.PLAYERS, 0.40f, 0.94f);
            return;
        }
        giveMoneyContract(player);
        PlayerStardewDataAPI.setStat(player, STAT_CONTRACT_LAST_CLAIM_DAY, today);
    }

    private static void giveMoneyContract(ServerPlayer player) {
        ItemStack stack = new ItemStack(ModItems.MONEY_CONTRACT.get());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        player.displayClientMessage(Component.translatable("stardewcraft.lewis.money_contract.received"), false);
        player.playNotifySound(ModSounds.BOOK_READ.get(), SoundSource.PLAYERS, 0.70f, 1.08f);
        player.playNotifySound(ModSounds.NEW_RECIPE.get(), SoundSource.PLAYERS, 0.55f, 1.0f);
    }

    private static int currentAbsoluteDay() {
        return StardewTimeManager.get().getAbsoluteDay();
    }
}
