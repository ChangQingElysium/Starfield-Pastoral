package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.npc.runtime.NpcInteractionService;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → Server: player confirmed (or declined) giving a gift to an NPC.
 */
@SuppressWarnings("null")
public record ConfirmGiftPayload(
    String npcId,
    boolean confirmed
) implements CustomPacketPayload {

    public static final Type<ConfirmGiftPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "confirm_gift"));

    public static final StreamCodec<FriendlyByteBuf, ConfirmGiftPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.npcId(), 64);
            buf.writeBoolean(payload.confirmed());
        },
        buf -> new ConfirmGiftPayload(buf.readUtf(64), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @SuppressWarnings("null")
    public static void handle(ConfirmGiftPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!payload.confirmed()) return;
            NpcInteractionService.handleConfirmedGift(player, payload.npcId());
        });
    }
}
