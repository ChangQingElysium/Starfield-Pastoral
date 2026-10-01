package com.stardew.craft.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record BuildingPreviewRequestPayload(net.minecraft.core.BlockPos anchor, net.minecraft.core.Direction facing, boolean self, int sequence) implements CustomPacketPayload {
    public static final Type<BuildingPreviewRequestPayload> TYPE = new Type<>(new ResourceLocation("stardewcraft:building_preview_request_payload"));
    public static final StreamCodec<FriendlyByteBuf, BuildingPreviewRequestPayload> STREAM_CODEC = StreamCodec.of(
        (b, p) -> { b.writeBlockPos(p.anchor); b.writeEnum(p.facing); b.writeBoolean(p.self); b.writeInt(p.sequence); }, b -> new BuildingPreviewRequestPayload(b.readBlockPos(), b.readEnum(net.minecraft.core.Direction.class), b.readBoolean(), b.readInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(BuildingPreviewRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> { if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) com.stardew.craft.building.runtime.BuildingPreviewService.request(player, payload); });
    }
}
