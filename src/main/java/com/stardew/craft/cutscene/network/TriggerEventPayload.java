package com.stardew.craft.cutscene.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.cutscene.data.EventData;
import com.stardew.craft.cutscene.data.EventRegistry;
import com.stardew.craft.cutscene.runtime.EventPlayer;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Server → Client: tells the client to start playing an event.
 * Used by debug commands and time_check triggers.
 */
public record TriggerEventPayload(String eventId, long sessionId) implements CustomPacketPayload {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Type<TriggerEventPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "trigger_event"));

    public static final StreamCodec<ByteBuf, TriggerEventPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, TriggerEventPayload::eventId,
            ByteBufCodecs.VAR_LONG, TriggerEventPayload::sessionId,
            TriggerEventPayload::new);

    @SuppressWarnings("null")
    public static void handle(TriggerEventPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            EventData data = EventRegistry.getById(payload.eventId);
            if (data != null) {
                if (!EventPlayer.get().start(data, payload.sessionId)) {
                    PacketDistributor.sendToServer(new AbortCutscenePayload(
                            payload.eventId, payload.sessionId));
                }
            } else {
                LOGGER.warn("Client EventRegistry has no event '{}' — was the registry synced?", payload.eventId);
                PacketDistributor.sendToServer(new AbortCutscenePayload(
                        payload.eventId, payload.sessionId));
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
