package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.sound.JukeboxData;
import com.stardew.craft.sound.JukeboxTrackRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端：玩家在唱片机 GUI 中选择了一首曲目。
 */
public record JukeboxSelectPayload(BlockPos pos, String trackId) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<JukeboxSelectPayload> TYPE = new Type<>(
            new ResourceLocation(StardewCraft.MODID, "jukebox_select"));

    public static final StreamCodec<ByteBuf, JukeboxSelectPayload> STREAM_CODEC = StreamCodec.composite(
            com.stardew.craft.port.PortCodecs.BLOCK_POS, JukeboxSelectPayload::pos,
            ByteBufCodecs.STRING_UTF8, JukeboxSelectPayload::trackId,
            JukeboxSelectPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @SuppressWarnings("null")
    public static void handle(JukeboxSelectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            // 距离检查
            if (player.distanceToSqr(payload.pos().getX() + 0.5, payload.pos().getY() + 0.5,
                    payload.pos().getZ() + 0.5) > 64.0) {
                return;
            }

            if (!(player.level() instanceof ServerLevel serverLevel)) return;

            String trackId = payload.trackId();

            // 处理随机
            if (JukeboxTrackRegistry.RANDOM.equals(trackId)) {
                var tracks = JukeboxTrackRegistry.getAllTracks();
                if (!tracks.isEmpty()) {
                    trackId = tracks.get(serverLevel.getRandom().nextInt(tracks.size())).id();
                } else {
                    return;
                }
            }

            // 保存到 SavedData
            JukeboxData data = JukeboxData.get(serverLevel);
            String finalTrackId;
            if (JukeboxTrackRegistry.TURN_OFF.equals(trackId)) {
                data.setTrack(payload.pos(), "");
                finalTrackId = "";
            } else {
                data.setTrack(payload.pos(), trackId);
                finalTrackId = trackId;
            }

            // 广播给附近所有玩家，由客户端播放音乐 + 抑制背景音乐
            JukeboxPlayPayload broadcastPayload = new JukeboxPlayPayload(payload.pos(), finalTrackId);
            for (ServerPlayer nearby : serverLevel.players()) {
                if (nearby.distanceToSqr(payload.pos().getX() + 0.5, payload.pos().getY() + 0.5,
                        payload.pos().getZ() + 0.5) <= 4096.0) { // 64 blocks
                    PacketDistributor.sendToPlayer(nearby, broadcastPayload);
                }
            }
        });
    }
}
