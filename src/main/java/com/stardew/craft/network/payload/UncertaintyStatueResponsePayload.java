package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.statue.UncertaintyStatueService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record UncertaintyStatueResponsePayload(BlockPos statuePos, int action, int skillId)
        implements CustomPacketPayload {
    public static final Type<UncertaintyStatueResponsePayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "uncertainty_statue_response"));

    public static final StreamCodec<FriendlyByteBuf, UncertaintyStatueResponsePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeBlockPos(payload.statuePos());
            buf.writeVarInt(payload.action());
            buf.writeVarInt(payload.skillId());
        },
        buf -> new UncertaintyStatueResponsePayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UncertaintyStatueResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                UncertaintyStatueService.handleResponse(player, payload.statuePos(), payload.action(), payload.skillId());
            }
        });
    }
}
