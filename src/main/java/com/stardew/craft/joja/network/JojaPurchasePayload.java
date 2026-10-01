package com.stardew.craft.joja.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.joja.JojaCDService;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → Server: 玩家点击 CDMenu 某格按钮尝试购买。 */
@SuppressWarnings("null")
public record JojaPurchasePayload(int buttonIdx) implements CustomPacketPayload {

    public static final Type<JojaPurchasePayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "joja_purchase"));

    public static final StreamCodec<ByteBuf, JojaPurchasePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, JojaPurchasePayload::buttonIdx,
        JojaPurchasePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(JojaPurchasePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                JojaCDService.handlePurchase(sp, payload.buttonIdx());
            }
        });
    }
}
