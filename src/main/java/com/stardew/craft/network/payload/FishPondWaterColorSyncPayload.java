package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.fishpond.ClientFishPondWaterColorCache;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.LinkedHashMap;
import java.util.Map;

public record FishPondWaterColorSyncPayload(String dimensionId, Map<BlockPos, Integer> colors)
        implements CustomPacketPayload {

    public static final Type<FishPondWaterColorSyncPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "fish_pond_water_color_sync")
    );

    public static final StreamCodec<ByteBuf, FishPondWaterColorSyncPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,
        FishPondWaterColorSyncPayload::dimensionId,
        ByteBufCodecs.map(
            LinkedHashMap::new,
            com.stardew.craft.port.PortCodecs.BLOCK_POS,
            ByteBufCodecs.INT
        ),
        FishPondWaterColorSyncPayload::colors,
        FishPondWaterColorSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FishPondWaterColorSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientFishPondWaterColorCache.applySnapshot(payload.dimensionId(), payload.colors()));
    }
}