package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client equivalent of SDV {@code Game1.showGlobalMessage}. */
@SuppressWarnings("null")
public record GlobalHudMessagePayload(Component message) implements CustomPacketPayload {
    public static final Type<GlobalHudMessagePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "global_hud_message")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, GlobalHudMessagePayload> STREAM_CODEC =
        StreamCodec.composite(
            ComponentSerialization.TRUSTED_STREAM_CODEC,
            GlobalHudMessagePayload::message,
            GlobalHudMessagePayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GlobalHudMessagePayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
            com.stardew.craft.client.hud.StardewHudMessageManager.showGlobalMessage(payload.message()));
    }

    public static void sendTo(ServerPlayer player, Component message) {
        if (player != null && message != null) {
            PacketDistributor.sendToPlayer(player, new GlobalHudMessagePayload(message));
        }
    }
}
