package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Museum donation sync - server -> client.
 */
@SuppressWarnings("null")
public record MuseumDonationSyncPacket(List<String> donatedIds) implements CustomPacketPayload {
    public MuseumDonationSyncPacket {
        if (donatedIds == null) {
            donatedIds = List.of();
        }
    }
    public static final CustomPacketPayload.Type<MuseumDonationSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(new ResourceLocation(StardewCraft.MODID, "museum_donations_sync"));

    public static final StreamCodec<ByteBuf, MuseumDonationSyncPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MuseumDonationSyncPacket decode(ByteBuf buf) {
            int size = ByteBufCodecs.VAR_INT.decode(buf);
            List<String> ids = new ArrayList<>(Math.max(0, size));
            for (int i = 0; i < size; i++) {
                ids.add(ByteBufCodecs.STRING_UTF8.decode(buf));
            }
            return new MuseumDonationSyncPacket(ids);
        }

        @Override
        public void encode(ByteBuf buf, MuseumDonationSyncPacket value) {
            List<String> ids = value.donatedIds();
            ByteBufCodecs.VAR_INT.encode(buf, ids.size());
            for (String id : ids) {
                ByteBufCodecs.STRING_UTF8.encode(buf, id);
            }
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MuseumDonationSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.ClientMuseumDonationCache.setDonated(packet.donatedIds()));
    }
}