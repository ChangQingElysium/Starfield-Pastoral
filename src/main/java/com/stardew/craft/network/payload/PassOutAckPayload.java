package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.player.PassOutService;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C→S: 客户端渐黑完成确认。
 * 服务端收到后执行本次击倒救援传送。
 */
@SuppressWarnings("null")
public record PassOutAckPayload(long transactionId) implements CustomPacketPayload {

    public static final Type<PassOutAckPayload> TYPE =
            new Type<>(new ResourceLocation(StardewCraft.MODID, "pass_out_ack"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PassOutAckPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG,
                    PassOutAckPayload::transactionId,
                    PassOutAckPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PassOutAckPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                PassOutService.acknowledgeCombatCollapse(sp, payload.transactionId());
            }
        });
    }
}
