package com.stardew.craft.fishpond.service;

import com.stardew.craft.fishpond.data.FishPondWorldData;
import com.stardew.craft.fishpond.model.FishPondRecord;
import com.stardew.craft.network.payload.FishPondWaterColorSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.Map;

public final class FishPondColorSyncService {
    private FishPondColorSyncService() {
    }

    public static void sendFullSnapshot(ServerPlayer player, ServerLevel level) {
        PacketDistributor.sendToPlayer(player, createPayload(level));
    }

    public static void broadcastSnapshot(ServerLevel level) {
        FishPondWaterColorSyncPayload payload = createPayload(level);
        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private static FishPondWaterColorSyncPayload createPayload(ServerLevel level) {
        Map<BlockPos, Integer> overrides = new LinkedHashMap<>();
        String dimensionId = level.dimension().location().toString();
        for (FishPondRecord pond : FishPondWorldData.get(level).getPonds()) {
            if (!dimensionId.equals(pond.dimensionId()) || pond.waterColor() < 0) {
                continue;
            }
            for (Long waterCell : pond.waterCells()) {
                overrides.put(BlockPos.of(waterCell), pond.waterColor());
            }
        }
        return new FishPondWaterColorSyncPayload(dimensionId, overrides);
    }
}