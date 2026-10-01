package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record AcceptSpecialOrderPayload(String orderId) implements CustomPacketPayload {
    public static final Type<AcceptSpecialOrderPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "accept_special_order"));

    public static final StreamCodec<ByteBuf, AcceptSpecialOrderPayload> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.STRING_UTF8, AcceptSpecialOrderPayload::orderId, AcceptSpecialOrderPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AcceptSpecialOrderPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                com.stardew.craft.specialorder.SpecialOrderManager.accept(player, payload.orderId());
            }
        });
    }
}
