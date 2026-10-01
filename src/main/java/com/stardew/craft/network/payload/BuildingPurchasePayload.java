package com.stardew.craft.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record BuildingPurchasePayload(long revision, boolean self, java.util.UUID requestId, ResourceLocation family) implements CustomPacketPayload {
    public static final Type<BuildingPurchasePayload> TYPE = new Type<>(new ResourceLocation("stardewcraft:building_purchase_payload"));
    public static final StreamCodec<FriendlyByteBuf, BuildingPurchasePayload> STREAM_CODEC = StreamCodec.of(
        (b, p) -> { b.writeLong(p.revision); b.writeBoolean(p.self); b.writeUUID(p.requestId); b.writeResourceLocation(p.family); }, b -> new BuildingPurchasePayload(b.readLong(), b.readBoolean(), b.readUUID(), b.readResourceLocation()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(BuildingPurchasePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> { if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) com.stardew.craft.building.runtime.BuildingPurchaseService.purchase(player, payload.revision, payload.self, payload.requestId, payload.family); });
    }
}
