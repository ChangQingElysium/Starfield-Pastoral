package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.farm.FarmJoinManager;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

@SuppressWarnings("null")
public record FarmJoinResponsePayload(
        UUID requesterUUID,
        boolean accept
) implements CustomPacketPayload {

    public static final Type<FarmJoinResponsePayload> TYPE =
            new Type<>(new ResourceLocation(StardewCraft.MODID, "farm_join_response"));

    public static final StreamCodec<FriendlyByteBuf, FarmJoinResponsePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUUID(payload.requesterUUID());
                buf.writeBoolean(payload.accept());
            },
            buf -> new FarmJoinResponsePayload(buf.readUUID(), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FarmJoinResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            FarmJoinManager.handleResponse(player, payload.requesterUUID(), payload.accept(), player.server);
        });
    }
}