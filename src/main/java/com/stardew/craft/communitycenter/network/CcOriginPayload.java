package com.stardew.craft.communitycenter.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S→C: 同步玩家的 CC 内部原点坐标到客户端。
 */
@SuppressWarnings("null")
public record CcOriginPayload(BlockPos origin) implements CustomPacketPayload {

    public static final Type<CcOriginPayload> TYPE = new Type<>(
            new ResourceLocation(StardewCraft.MODID, "cc_origin")
    );

    public static final StreamCodec<ByteBuf, CcOriginPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public CcOriginPayload decode(ByteBuf buf) {
            int x = ByteBufCodecs.VAR_INT.decode(buf);
            int y = ByteBufCodecs.VAR_INT.decode(buf);
            int z = ByteBufCodecs.VAR_INT.decode(buf);
            return new CcOriginPayload(new BlockPos(x, y, z));
        }

        @Override
        public void encode(ByteBuf buf, CcOriginPayload payload) {
            ByteBufCodecs.VAR_INT.encode(buf, payload.origin.getX());
            ByteBufCodecs.VAR_INT.encode(buf, payload.origin.getY());
            ByteBufCodecs.VAR_INT.encode(buf, payload.origin.getZ());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CcOriginPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            BundleClientData.INSTANCE.setCCOrigin(payload.origin);
        });
    }
}
