package com.stardew.craft.network.payload;

import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record BuildingManagerInfoPayload(net.minecraft.network.chat.Component text, net.minecraft.core.BlockPos min, net.minecraft.core.BlockPos max) implements CustomPacketPayload {
    public static final Type<BuildingManagerInfoPayload> TYPE = new Type<>(new ResourceLocation("stardewcraft:building_manager_info_payload"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BuildingManagerInfoPayload> STREAM_CODEC = StreamCodec.of(
        (b, p) -> { com.stardew.craft.port.PortCodecs.COMPONENT.encode(b, p.text); b.writeBlockPos(p.min); b.writeBlockPos(p.max); }, b -> new BuildingManagerInfoPayload(com.stardew.craft.port.PortCodecs.COMPONENT.decode(b), b.readBlockPos(), b.readBlockPos()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(BuildingManagerInfoPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(BuildingManagerInfoPayload payload) { com.stardew.craft.client.building.BuildingPlacementPreview.showManager(payload); }
}
