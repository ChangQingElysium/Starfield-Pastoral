package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Server → Client: sync all museum exhibit stand display items for a specific player.
 * Sends a map of BlockPos → itemId (empty string = no item).
 */
@SuppressWarnings("null")
public record MuseumStandSyncPacket(Map<BlockPos, String> standItems) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MuseumStandSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(new ResourceLocation(StardewCraft.MODID, "museum_stand_sync"));

    public static final StreamCodec<ByteBuf, MuseumStandSyncPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MuseumStandSyncPacket decode(ByteBuf buf) {
            int size = ByteBufCodecs.VAR_INT.decode(buf);
            Map<BlockPos, String> items = new HashMap<>(Math.max(0, size));
            for (int i = 0; i < size; i++) {
                int x = buf.readInt();
                int y = buf.readInt();
                int z = buf.readInt();
                String itemId = ByteBufCodecs.STRING_UTF8.decode(buf);
                items.put(new BlockPos(x, y, z), itemId);
            }
            return new MuseumStandSyncPacket(items);
        }

        @Override
        public void encode(ByteBuf buf, MuseumStandSyncPacket value) {
            Map<BlockPos, String> items = value.standItems();
            ByteBufCodecs.VAR_INT.encode(buf, items.size());
            for (Map.Entry<BlockPos, String> entry : items.entrySet()) {
                BlockPos pos = entry.getKey();
                buf.writeInt(pos.getX());
                buf.writeInt(pos.getY());
                buf.writeInt(pos.getZ());
                ByteBufCodecs.STRING_UTF8.encode(buf, entry.getValue());
            }
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MuseumStandSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(packet));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(MuseumStandSyncPacket packet) {
        // The packet carries the full authoritative map for the player in the current
        // dimension, so we replace the cache wholesale. Stands whose chunks aren't loaded
        // yet will hydrate themselves from the cache in their onLoad() hook.
        com.stardew.craft.client.ClientMuseumStandCache.replaceAll(packet.standItems());
    }
}
