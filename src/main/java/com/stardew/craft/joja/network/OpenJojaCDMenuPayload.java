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
 * Server → Client: 打开 Joja CD Menu。
 * @param completedMask 5 bit 掩码：bit i 为 1 表示按钮 i 的 cc* flag 已置位
 * @param money         玩家当前金币（避免客户端 HUD 缓存滞后）
 */
@SuppressWarnings("null")
public record OpenJojaCDMenuPayload(int completedMask, int money) implements CustomPacketPayload {

    public static final Type<OpenJojaCDMenuPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "open_joja_cd_menu"));

    public static final StreamCodec<ByteBuf, OpenJojaCDMenuPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, OpenJojaCDMenuPayload::completedMask,
        ByteBufCodecs.VAR_INT, OpenJojaCDMenuPayload::money,
        OpenJojaCDMenuPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenJojaCDMenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.stardew.craft.joja.client.JojaCDScreen.openFromServer(payload.completedMask(), payload.money());
            }
        });
    }
}
