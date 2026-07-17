package com.stardew.craft.communitycenter.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S→C: Junimo 已在星盘上放置一颗星。
 * 客户端收到后按区域标记星星，星盘纹理随之更新。
 */
@SuppressWarnings("null")
public record StarPlacedPayload(int areaId) implements CustomPacketPayload {

    public static final Type<StarPlacedPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "star_placed"));

    public static final StreamCodec<ByteBuf, StarPlacedPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, StarPlacedPayload::areaId,
                    StarPlacedPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 客户端处理: 按区域去重后更新星盘显示星星数。 */
    public static void handle(StarPlacedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            BundleClientData.INSTANCE.markDisplayStarArea(payload.areaId());
        });
    }

    /** 每个社区中心进度属于单独玩家，只同步给对应所有者。 */
    public static void sendToPlayer(ServerPlayer player, int areaId) {
        PacketDistributor.sendToPlayer(player, new StarPlacedPayload(areaId));
    }
}
