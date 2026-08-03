package com.stardew.craft.cutscene.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → Server: releases an authorized session the client could not start. */
public record AbortCutscenePayload(String eventId, long sessionId) implements CustomPacketPayload {
    public static final Type<AbortCutscenePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "abort_cutscene"));

    public static final StreamCodec<ByteBuf, AbortCutscenePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AbortCutscenePayload::eventId,
            ByteBufCodecs.VAR_LONG, AbortCutscenePayload::sessionId,
            AbortCutscenePayload::new);

    @SuppressWarnings("null")
    public static void handle(AbortCutscenePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            boolean aborted = com.stardew.craft.cutscene.server.ServerCutsceneTracker.abortSession(
                    player, payload.sessionId, payload.eventId);
            if (aborted) {
                com.stardew.craft.festival.ActiveFestivalHandlers
                        .onCutsceneUnavailable(player, payload.eventId);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
