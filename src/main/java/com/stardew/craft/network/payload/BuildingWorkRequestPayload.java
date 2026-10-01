package com.stardew.craft.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.UUID;

public record BuildingWorkRequestPayload(ResourceLocation family, long catalog, UUID building, long revision, String action, UUID requestId) implements CustomPacketPayload {
    public BuildingWorkRequestPayload(ResourceLocation family, long catalog, UUID building, long revision, String action) {
        this(family, catalog, building, revision, action, UUID.randomUUID());
    }
    public static final Type<BuildingWorkRequestPayload> TYPE = new Type<>(new ResourceLocation("stardewcraft:building_work_request"));
    public static final StreamCodec<FriendlyByteBuf, BuildingWorkRequestPayload> STREAM_CODEC = StreamCodec.of(
            (b,p) -> { b.writeResourceLocation(p.family); b.writeLong(p.catalog); b.writeUUID(p.building); b.writeLong(p.revision); b.writeUtf(p.action, 16); b.writeUUID(p.requestId); },
            b -> new BuildingWorkRequestPayload(b.readResourceLocation(), b.readLong(), b.readUUID(), b.readLong(), b.readUtf(16), b.readUUID()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(BuildingWorkRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> { if (context.player() instanceof net.minecraft.server.level.ServerPlayer player)
            com.stardew.craft.building.runtime.BuildingLifecycleService.request(player, payload.family, payload.catalog, payload.building, payload.revision, payload.action, payload.requestId); });
    }
}
