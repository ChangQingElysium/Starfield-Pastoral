package com.stardew.craft.money;

import com.stardew.craft.network.payload.OpenMoneyContractActionPayload;
import com.stardew.craft.network.payload.OpenMoneyContractTransferPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public final class MoneyContractService {
    public static final UUID DEBUG_TARGET_ID = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    public static final String DEBUG_TARGET_NAME = "Debug Farmer";
    private static final int DEBUG_SHARE_BALANCE = 500;

    private MoneyContractService() {
    }

    public static void openActionMenu(ServerPlayer player, ServerPlayer target) {
        if (!validateTarget(player, target)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new OpenMoneyContractActionPayload(target.getUUID(), target.getName().getString()));
    }

    public static void openDebugActionMenu(ServerPlayer player) {
        if (!player.hasPermissions(2)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new OpenMoneyContractActionPayload(DEBUG_TARGET_ID, DEBUG_TARGET_NAME));
    }

    public static void handleAction(ServerPlayer player, UUID targetId, int choice) {
        if (isDebugTarget(player, targetId)) {
            switch (choice) {
                case 0 -> {
                    SharedMoneyData.get().debugMergeWithMember(player, DEBUG_TARGET_ID, DEBUG_SHARE_BALANCE);
                    player.displayClientMessage(Component.translatable(
                            "stardewcraft.money_contract.debug.shared", DEBUG_SHARE_BALANCE), false);
                }
                case 1 -> openDebugTransferScreen(player);
                default -> {
                }
            }
            return;
        }

        ServerPlayer target = player.server.getPlayerList().getPlayer(targetId);
        if (!validateTarget(player, target)) {
            return;
        }
        switch (choice) {
            case 0 -> SharedMoneyService.beginContractInvite(player, target);
            case 1 -> openTransferScreen(player, target);
            default -> {
            }
        }
    }

    public static void transferMoney(ServerPlayer player, UUID targetId, int amount) {
        if (amount <= 0) {
            player.displayClientMessage(Component.translatable("stardewcraft.money_contract.transfer.invalid_amount"), true);
            playCancel(player);
            return;
        }
        if (isDebugTarget(player, targetId)) {
            if (!PlayerStardewDataAPI.removeMoney(player, amount)) {
                player.displayClientMessage(Component.translatable("stardewcraft.money_contract.transfer.not_enough"), true);
                playCancel(player);
                return;
            }
            player.displayClientMessage(Component.translatable(
                    "stardewcraft.money_contract.debug.transferred", amount), false);
            playMoney(player);
            return;
        }

        ServerPlayer target = player.server.getPlayerList().getPlayer(targetId);
        if (!validateTarget(player, target)) {
            return;
        }
        if (SharedMoneyService.sameGroup(player, target)) {
            player.displayClientMessage(Component.translatable("stardewcraft.money_contract.transfer.same_group"), true);
            playCancel(player);
            return;
        }
        if (!PlayerStardewDataAPI.removeMoney(player, amount)) {
            player.displayClientMessage(Component.translatable("stardewcraft.money_contract.transfer.not_enough"), true);
            playCancel(player);
            return;
        }
        PlayerStardewDataAPI.addMoney(target, amount);
        player.displayClientMessage(Component.translatable("stardewcraft.money_contract.transfer.sent", amount, target.getName()), false);
        target.displayClientMessage(Component.translatable("stardewcraft.money_contract.transfer.received", amount, player.getName()), false);
        playMoney(player);
        playMoney(target);
    }

    private static void openTransferScreen(ServerPlayer player, ServerPlayer target) {
        PacketDistributor.sendToPlayer(player,
                new OpenMoneyContractTransferPayload(PlayerStardewDataAPI.getMoney(player),
                        target.getUUID(), target.getName().getString()));
    }

    private static void openDebugTransferScreen(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new OpenMoneyContractTransferPayload(PlayerStardewDataAPI.getMoney(player),
                        DEBUG_TARGET_ID, DEBUG_TARGET_NAME));
    }

    private static boolean validateTarget(ServerPlayer player, ServerPlayer target) {
        if (target == null) {
            player.displayClientMessage(Component.translatable("stardewcraft.money_contract.transfer.target_offline"), true);
            playCancel(player);
            return false;
        }
        if (target.getUUID().equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("stardewcraft.money_contract.transfer.self"), true);
            playCancel(player);
            return false;
        }
        return true;
    }

    private static boolean isDebugTarget(ServerPlayer player, UUID targetId) {
        return DEBUG_TARGET_ID.equals(targetId) && player.hasPermissions(2);
    }

    private static void playMoney(ServerPlayer player) {
        player.playNotifySound(ModSounds.MONEY.get(), SoundSource.PLAYERS, 0.70f, 1.0f);
        player.playNotifySound(ModSounds.COIN.get(), SoundSource.PLAYERS, 0.45f, 1.18f);
    }

    private static void playCancel(ServerPlayer player) {
        player.playNotifySound(ModSounds.CANCEL.get(), SoundSource.PLAYERS, 0.45f, 0.92f);
    }
}
