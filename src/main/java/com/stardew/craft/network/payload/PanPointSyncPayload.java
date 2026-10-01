package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.render.ClientPanPointState;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → Client: update this player's active ore-pan point.
 * active=false means "no point" (clear the client-side marker).
 * <p>
 * Per-player: sent only to the owning player — other players never see
 * someone else's pan point.
 */
public record PanPointSyncPayload(boolean active, BlockPos pos) implements CustomPacketPayload {

    public static final Type<PanPointSyncPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "pan_point_sync"));

    public static final StreamCodec<ByteBuf, PanPointSyncPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, PanPointSyncPayload::active,
        com.stardew.craft.port.PortCodecs.BLOCK_POS, PanPointSyncPayload::pos,
        PanPointSyncPayload::new);

    @SuppressWarnings("null")
    public static void handle(PanPointSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.active) {
                ClientPanPointState.setPoint(payload.pos);
            } else {
                ClientPanPointState.clear();
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
