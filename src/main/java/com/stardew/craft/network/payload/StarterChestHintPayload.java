package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.render.ClientStarterChestState;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → Client: show or hide the starter chest hint at a given position.
 * show=true → display golden hint at pos; show=false → remove hint.
 */
public record StarterChestHintPayload(BlockPos pos, boolean show) implements CustomPacketPayload {

    public static final Type<StarterChestHintPayload> TYPE = new Type<>(
            new ResourceLocation(StardewCraft.MODID, "starter_chest_hint"));

    public static final StreamCodec<ByteBuf, StarterChestHintPayload> STREAM_CODEC = StreamCodec.composite(
            com.stardew.craft.port.PortCodecs.BLOCK_POS, StarterChestHintPayload::pos,
            ByteBufCodecs.BOOL, StarterChestHintPayload::show,
            StarterChestHintPayload::new);

    @SuppressWarnings("null")
    public static void handle(StarterChestHintPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.show) {
                ClientStarterChestState.setHintPos(payload.pos);
            } else {
                ClientStarterChestState.clear();
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
