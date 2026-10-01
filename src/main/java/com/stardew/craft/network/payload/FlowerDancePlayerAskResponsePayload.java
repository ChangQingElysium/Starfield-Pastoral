package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.festival.FlowerDanceService;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

@SuppressWarnings("null")
public record FlowerDancePlayerAskResponsePayload(UUID targetPlayerId, boolean confirmed) implements CustomPacketPayload {
    public static final Type<FlowerDancePlayerAskResponsePayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "flower_dance_player_ask_response"));

    public static final StreamCodec<FriendlyByteBuf, FlowerDancePlayerAskResponsePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUUID(payload.targetPlayerId());
            buf.writeBoolean(payload.confirmed());
        },
        buf -> new FlowerDancePlayerAskResponsePayload(buf.readUUID(), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FlowerDancePlayerAskResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                FlowerDanceService.handlePlayerDanceAskResponse(player, payload.targetPlayerId(), payload.confirmed());
            }
        });
    }
}
