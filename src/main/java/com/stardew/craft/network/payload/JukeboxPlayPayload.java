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
 * 服务端 → 客户端：广播唱片机播放状态变更。
 * trackId 为空字符串表示停止播放。
 */
public record JukeboxPlayPayload(BlockPos pos, String trackId) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<JukeboxPlayPayload> TYPE = new Type<>(
            new ResourceLocation(StardewCraft.MODID, "jukebox_play"));

    public static final StreamCodec<ByteBuf, JukeboxPlayPayload> STREAM_CODEC = StreamCodec.composite(
            com.stardew.craft.port.PortCodecs.BLOCK_POS, JukeboxPlayPayload::pos,
            ByteBufCodecs.STRING_UTF8, JukeboxPlayPayload::trackId,
            JukeboxPlayPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @SuppressWarnings("null")
    public static void handle(JukeboxPlayPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.stardew.craft.client.sound.StardewMusicManager.setJukeboxState(
                    payload.pos(), payload.trackId());
        });
    }
}
