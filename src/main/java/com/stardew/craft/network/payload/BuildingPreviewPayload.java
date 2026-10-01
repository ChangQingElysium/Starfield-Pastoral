package com.stardew.craft.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record BuildingPreviewPayload(net.minecraft.core.BlockPos anchor, net.minecraft.core.Direction facing, boolean self, int sequence, String issue, net.minecraft.core.BlockPos problem, net.minecraft.core.BlockPos min, net.minecraft.core.BlockPos max, net.minecraft.core.BlockPos structureMin, net.minecraft.core.BlockPos structureMax, net.minecraft.core.BlockPos manager, ResourceLocation family, int tier) implements CustomPacketPayload {
    public static final Type<BuildingPreviewPayload> TYPE = new Type<>(new ResourceLocation("stardewcraft:building_preview_payload"));
    public static final StreamCodec<FriendlyByteBuf, BuildingPreviewPayload> STREAM_CODEC = StreamCodec.of(
        (b, p) -> { b.writeBlockPos(p.anchor); b.writeEnum(p.facing); b.writeBoolean(p.self); b.writeInt(p.sequence); b.writeUtf(p.issue, 32); b.writeBlockPos(p.problem); b.writeBlockPos(p.min); b.writeBlockPos(p.max); b.writeBlockPos(p.structureMin); b.writeBlockPos(p.structureMax); b.writeBlockPos(p.manager); b.writeResourceLocation(p.family); b.writeVarInt(p.tier); }, b -> new BuildingPreviewPayload(b.readBlockPos(), b.readEnum(net.minecraft.core.Direction.class), b.readBoolean(), b.readInt(), b.readUtf(32), b.readBlockPos(), b.readBlockPos(), b.readBlockPos(), b.readBlockPos(), b.readBlockPos(), b.readBlockPos(), b.readResourceLocation(), b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(BuildingPreviewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(BuildingPreviewPayload payload) { com.stardew.craft.client.building.BuildingPlacementPreview.accept(payload); }
}
