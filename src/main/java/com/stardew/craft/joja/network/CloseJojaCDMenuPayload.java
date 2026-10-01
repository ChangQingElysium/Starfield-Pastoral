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

/** Client → Server: 玩家关闭了 CDMenu。若本次买过东西则服务器触发 Morris_JojaCDConfirm。 */
@SuppressWarnings("null")
public record CloseJojaCDMenuPayload(boolean boughtSomething) implements CustomPacketPayload {

    public static final Type<CloseJojaCDMenuPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "close_joja_cd_menu"));

    public static final StreamCodec<ByteBuf, CloseJojaCDMenuPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, CloseJojaCDMenuPayload::boughtSomething,
        CloseJojaCDMenuPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CloseJojaCDMenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                JojaCDService.handleClose(sp, payload.boughtSomething());
            }
        });
    }
}
