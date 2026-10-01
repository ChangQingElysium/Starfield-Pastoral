package com.stardew.craft.joja.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → Client: 购买结果。
 * @param buttonIdx      请求的按钮索引
 * @param resultCode     {@link com.stardew.craft.joja.JojaConstants#RESULT_OK / NOT_ENOUGH_MONEY / ALREADY_DONE}
 * @param newMoney       购买后玩家余额
 * @param newCompletedMask 更新后的完成掩码（5 bit）
 */
@SuppressWarnings("null")
public record JojaPurchaseResultPayload(int buttonIdx, int resultCode, int newMoney, int newCompletedMask)
    implements CustomPacketPayload {

    public static final Type<JojaPurchaseResultPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "joja_purchase_result"));

    public static final StreamCodec<ByteBuf, JojaPurchaseResultPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, JojaPurchaseResultPayload::buttonIdx,
        ByteBufCodecs.VAR_INT, JojaPurchaseResultPayload::resultCode,
        ByteBufCodecs.VAR_INT, JojaPurchaseResultPayload::newMoney,
        ByteBufCodecs.VAR_INT, JojaPurchaseResultPayload::newCompletedMask,
        JojaPurchaseResultPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(JojaPurchaseResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.stardew.craft.joja.client.JojaCDScreen.applyResult(
                    payload.buttonIdx(), payload.resultCode(), payload.newMoney(), payload.newCompletedMask());
            }
        });
    }
}
