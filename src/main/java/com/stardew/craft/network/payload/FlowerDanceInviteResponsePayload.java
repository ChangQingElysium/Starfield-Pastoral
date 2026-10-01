package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.npc.runtime.NpcInteractionService;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record FlowerDanceInviteResponsePayload(String npcId, boolean confirmed) implements CustomPacketPayload {
    public static final Type<FlowerDanceInviteResponsePayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "flower_dance_invite_response"));

    public static final StreamCodec<FriendlyByteBuf, FlowerDanceInviteResponsePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.npcId(), 64);
            buf.writeBoolean(payload.confirmed());
        },
        buf -> new FlowerDanceInviteResponsePayload(buf.readUtf(64), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FlowerDanceInviteResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                NpcInteractionService.handleFlowerDanceNpcInviteResponse(player, payload.npcId(), payload.confirmed());
            }
        });
    }
}
