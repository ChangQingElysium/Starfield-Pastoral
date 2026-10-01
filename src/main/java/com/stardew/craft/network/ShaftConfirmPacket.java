package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 竖井确认包 - 服务端 → 客户端
 * 服务端发送此包让客户端显示"要跳入竖井吗？"确认对话框。
 */
public record ShaftConfirmPacket(BlockPos shaftPos) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final CustomPacketPayload.Type<ShaftConfirmPacket> TYPE =
            new CustomPacketPayload.Type<>(new ResourceLocation(StardewCraft.MODID, "shaft_confirm"));

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, ShaftConfirmPacket> STREAM_CODEC =
            StreamCodec.composite(
                    com.stardew.craft.port.PortCodecs.BLOCK_POS.cast(),
                    ShaftConfirmPacket::shaftPos,
                    ShaftConfirmPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 客户端处理：打开竖井确认 GUI。
     * 注意：客户端实际逻辑放在 {@link ShaftConfirmPacketClient} 中，避免在专用服务器
     * 注册阶段触发 {@code net.minecraft.client.*} 类加载。
     */
    public static void handle(ShaftConfirmPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
                ShaftConfirmPacketClient.open(packet);
            }
        });
    }
}
