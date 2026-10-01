package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record TableClothColorSyncPayload(BlockPos pos, int colorIndex) implements CustomPacketPayload {
    @SuppressWarnings("null")
    public static final Type<TableClothColorSyncPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "table_cloth_color_sync"));

    @SuppressWarnings("null")
    public static final StreamCodec<FriendlyByteBuf, TableClothColorSyncPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeBlockPos(payload.pos());
            buf.writeVarInt(payload.colorIndex());
        },
        buf -> new TableClothColorSyncPayload(buf.readBlockPos(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TableClothColorSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(TableClothColorSyncPayload payload) {
        com.stardew.craft.client.ClientTableClothColorSync.apply(payload.pos(), payload.colorIndex());
    }
}
