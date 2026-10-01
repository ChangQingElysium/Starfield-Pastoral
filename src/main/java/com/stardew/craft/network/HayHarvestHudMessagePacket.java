package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record HayHarvestHudMessagePacket(int hayCount, boolean siloFull) implements CustomPacketPayload {
    @SuppressWarnings("null")
    public static final Type<HayHarvestHudMessagePacket> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "hay_harvest_hud")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, HayHarvestHudMessagePacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.INT,
        HayHarvestHudMessagePacket::hayCount,
        ByteBufCodecs.BOOL,
        HayHarvestHudMessagePacket::siloFull,
        HayHarvestHudMessagePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @SuppressWarnings("null")
    public static void handle(HayHarvestHudMessagePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (packet.siloFull()) {
                com.stardew.craft.client.hud.StardewHudMessageManager.showGlobalMessage(Component.translatable("stardewcraft.hud.hay_silo_full"));
            } else {
                com.stardew.craft.client.hud.StardewHudMessageManager.showHayHarvest(packet.hayCount());
            }
        });
    }

    public static void sendTo(Player player, int hayCount, boolean siloFull) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new HayHarvestHudMessagePacket(hayCount, siloFull));
        }
    }
}
