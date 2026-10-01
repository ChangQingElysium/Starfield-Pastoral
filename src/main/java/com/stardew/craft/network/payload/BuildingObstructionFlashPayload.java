package com.stardew.craft.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Briefly identifies the exact cells that rejected a confirmed building placement. */
public record BuildingObstructionFlashPayload(ResourceLocation dimension, List<BlockPos> positions)
        implements CustomPacketPayload {
    private static final int MAX_POSITIONS = 256;
    public static final Type<BuildingObstructionFlashPayload> TYPE =
            new Type<>(new ResourceLocation("stardewcraft:building_obstruction_flash"));
    public static final StreamCodec<FriendlyByteBuf, BuildingObstructionFlashPayload> STREAM_CODEC =
            StreamCodec.of(BuildingObstructionFlashPayload::encode, BuildingObstructionFlashPayload::decode);

    public BuildingObstructionFlashPayload {
        positions = List.copyOf(positions.subList(0, Math.min(MAX_POSITIONS, positions.size())));
    }

    private static void encode(FriendlyByteBuf buffer, BuildingObstructionFlashPayload payload) {
        buffer.writeResourceLocation(payload.dimension());
        buffer.writeVarInt(payload.positions().size());
        payload.positions().forEach(buffer::writeBlockPos);
    }

    private static BuildingObstructionFlashPayload decode(FriendlyByteBuf buffer) {
        ResourceLocation dimension = buffer.readResourceLocation();
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_POSITIONS) throw new IllegalArgumentException("Invalid obstruction count: " + size);
        List<BlockPos> positions = new ArrayList<>(size);
        for (int i = 0; i < size; i++) positions.add(buffer.readBlockPos());
        return new BuildingObstructionFlashPayload(dimension, positions);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BuildingObstructionFlashPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(BuildingObstructionFlashPayload payload) {
        com.stardew.craft.client.building.BuildingPlacementPreview.flashObstructions(
                payload.dimension(), payload.positions());
    }
}
