package com.stardew.craft.client;

import com.stardew.craft.block.FertilizerType;
import com.stardew.craft.network.FertilizerSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/** Applies decoded fertilizer payloads on the client main thread. */
public final class FertilizerClientSync {
    private FertilizerClientSync() {
    }

    public static void apply(FertilizerSyncPacket packet) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, packet.dimension());
        if (packet.replaceChunk()) {
            Map<BlockPos, FertilizerType> snapshot = new HashMap<>();
            for (FertilizerSyncPacket.Entry entry : packet.entries()) {
                FertilizerType type = entry.type();
                if (type != null) {
                    snapshot.put(entry.pos(), type);
                }
            }
            ClientFertilizerCache.replaceChunk(dimension, packet.chunkPos(), snapshot);
            return;
        }

        for (FertilizerSyncPacket.Entry entry : packet.entries()) {
            FertilizerType type = entry.type();
            if (type == null) {
                ClientFertilizerCache.removeFertilizer(dimension, entry.pos());
            } else {
                ClientFertilizerCache.setFertilizer(dimension, entry.pos(), type);
            }
        }
    }
}
