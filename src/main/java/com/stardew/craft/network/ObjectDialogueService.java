package com.stardew.craft.network;

import com.stardew.craft.network.payload.OpenObjectDialoguePayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class ObjectDialogueService {
    private ObjectDialogueService() {
    }

    public static void show(ServerPlayer player, Component message) {
        PacketDistributor.sendToPlayer(player, new OpenObjectDialoguePayload(message));
    }

    public static void show(ServerPlayer player, List<Component> messages) {
        PacketDistributor.sendToPlayer(
                player, new OpenObjectDialoguePayload(List.copyOf(messages)));
    }

    public static void show(ServerPlayer player, String translationKey, Object... args) {
        show(player, Component.translatable(translationKey, args));
    }
}
