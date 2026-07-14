package com.stardew.craft.cutscene.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.cutscene.data.EventRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;

/**
 * Server → Client: sends all cutscene event JSON data so the client can
 * populate its {@link EventRegistry} on a dedicated server.
 */
public record SyncEventRegistryPayload(
        long version,
        String contentHash,
        Map<String, String> eventJsonMap
) implements CustomPacketPayload {

    public static final Type<SyncEventRegistryPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "sync_event_registry"));

    public static final StreamCodec<ByteBuf, SyncEventRegistryPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, SyncEventRegistryPayload::version,
            ByteBufCodecs.STRING_UTF8, SyncEventRegistryPayload::contentHash,
            ByteBufCodecs.map(java.util.HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8),
            SyncEventRegistryPayload::eventJsonMap,
            SyncEventRegistryPayload::new);

    public static SyncEventRegistryPayload current() {
        return new SyncEventRegistryPayload(
                EventRegistry.version(),
                EventRegistry.contentHash(),
                new java.util.HashMap<>(EventRegistry.getRawJsonMap()));
    }

    public static void handle(SyncEventRegistryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            EventRegistry.loadFromJsonStrings(payload.version, payload.contentHash, payload.eventJsonMap);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
