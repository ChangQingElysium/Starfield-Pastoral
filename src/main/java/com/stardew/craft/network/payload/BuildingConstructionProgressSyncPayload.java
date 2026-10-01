package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.client.StardewConstructionOrderSnapshot;
import com.stardew.craft.api.v1.client.StardewConstructionProgressSnapshot;
import com.stardew.craft.api.v1.internal.client.StardewConstructionProgressCache;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;

/** Complete bounded replacement snapshot of Robin work visible to one client. */
@SuppressWarnings("null")
public record BuildingConstructionProgressSyncPayload(
        StardewConstructionProgressSnapshot snapshot
) implements CustomPacketPayload {
    public static final int MAX_ORDERS = 256;
    private static final int MAX_DISPLAY_NAME_LENGTH = 32;

    public static final Type<BuildingConstructionProgressSyncPayload> TYPE =
            new Type<>(new ResourceLocation(
                    StardewCraft.MODID, "building_construction_progress"));
    public static final StreamCodec<FriendlyByteBuf, BuildingConstructionProgressSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    BuildingConstructionProgressSyncPayload::write,
                    BuildingConstructionProgressSyncPayload::read);

    public BuildingConstructionProgressSyncPayload {
        java.util.Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.orders().size() > MAX_ORDERS) {
            throw new IllegalArgumentException("construction snapshot exceeds " + MAX_ORDERS + " entries");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BuildingConstructionProgressSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().getUUID().equals(payload.snapshot().playerId())) {
                StardewConstructionProgressCache.replace(payload.snapshot());
            }
        });
    }

    private static void write(FriendlyByteBuf buffer, BuildingConstructionProgressSyncPayload payload) {
        StardewConstructionProgressSnapshot snapshot = payload.snapshot();
        buffer.writeUUID(snapshot.playerId());
        buffer.writeVarInt(snapshot.totalOrderCount());
        buffer.writeVarInt(snapshot.orders().size());
        for (StardewConstructionOrderSnapshot order : snapshot.orders()) {
            buffer.writeUUID(order.buildingId());
            buffer.writeResourceLocation(order.buildingFamilyId());
            buffer.writeUtf(order.customName(), MAX_DISPLAY_NAME_LENGTH);
            buffer.writeEnum(order.workType());
            buffer.writeVarInt(order.targetTier());
            buffer.writeVarInt(order.remainingWorkDays());
        }
    }

    private static BuildingConstructionProgressSyncPayload read(FriendlyByteBuf buffer) {
        var playerId = buffer.readUUID();
        int totalOrderCount = buffer.readVarInt();
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_ORDERS || totalOrderCount < size) {
            throw new IllegalArgumentException("invalid construction snapshot size: " + size + "/" + totalOrderCount);
        }
        var orders = new ArrayList<StardewConstructionOrderSnapshot>(size);
        for (int index = 0; index < size; index++) {
            orders.add(new StardewConstructionOrderSnapshot(
                    buffer.readUUID(),
                    buffer.readResourceLocation(),
                    buffer.readUtf(MAX_DISPLAY_NAME_LENGTH),
                    buffer.readEnum(StardewConstructionOrderSnapshot.WorkType.class),
                    buffer.readVarInt(),
                    buffer.readVarInt()));
        }
        return new BuildingConstructionProgressSyncPayload(
                new StardewConstructionProgressSnapshot(playerId, totalOrderCount, orders));
    }
}
