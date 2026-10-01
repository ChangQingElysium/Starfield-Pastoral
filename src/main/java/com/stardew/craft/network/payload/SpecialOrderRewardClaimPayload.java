package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record SpecialOrderRewardClaimPayload(String orderId) implements CustomPacketPayload {
    public static final Type<SpecialOrderRewardClaimPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "claim_special_order_reward"));

    public static final StreamCodec<ByteBuf, SpecialOrderRewardClaimPayload> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.STRING_UTF8, SpecialOrderRewardClaimPayload::orderId, SpecialOrderRewardClaimPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpecialOrderRewardClaimPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                com.stardew.craft.specialorder.SpecialOrderManager.claimReward(player, payload.orderId());
            }
        });
    }
}
