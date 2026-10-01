package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端 → 客户端：通知客户端打开唱片机选曲 GUI。
 */
public record OpenJukeboxPayload(BlockPos pos, String currentTrack) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<OpenJukeboxPayload> TYPE = new Type<>(
            new ResourceLocation(StardewCraft.MODID, "open_jukebox"));

    public static final StreamCodec<ByteBuf, OpenJukeboxPayload> STREAM_CODEC = StreamCodec.composite(
            com.stardew.craft.port.PortCodecs.BLOCK_POS, OpenJukeboxPayload::pos,
            ByteBufCodecs.STRING_UTF8, OpenJukeboxPayload::currentTrack,
            OpenJukeboxPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @SuppressWarnings("null")
    public static void handle(OpenJukeboxPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenJukeboxPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            mc.setScreen(new com.stardew.craft.client.gui.JukeboxScreen(
                    payload.pos(), payload.currentTrack()));
        }
    }
}
