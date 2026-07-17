package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenSofaColorScreenPayload(BlockPos targetPos, int currentColor, int targetEntityId) implements CustomPacketPayload {
    public OpenSofaColorScreenPayload(BlockPos targetPos, int currentColor) {
        this(targetPos, currentColor, -1);
    }

    @SuppressWarnings("null")
    public static final Type<OpenSofaColorScreenPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "open_sofa_color_screen"));

    @SuppressWarnings("null")
    public static final StreamCodec<FriendlyByteBuf, OpenSofaColorScreenPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeBlockPos(payload.targetPos());
            buf.writeVarInt(payload.currentColor());
            buf.writeVarInt(payload.targetEntityId());
        },
        buf -> new OpenSofaColorScreenPayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenSofaColorScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenSofaColorScreenPayload payload) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        minecraft.setScreen(new com.stardew.craft.client.gui.SofaColorSelectionScreen(
            payload.targetPos(), payload.currentColor(), payload.targetEntityId()));
    }
}
