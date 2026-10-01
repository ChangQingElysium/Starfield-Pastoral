package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenSofaColorScreenPayload(BlockPos targetPos, int currentColor, int targetEntityId) implements CustomPacketPayload {
    public OpenSofaColorScreenPayload(BlockPos targetPos, int currentColor) {
        this(targetPos, currentColor, -1);
    }

    @SuppressWarnings("null")
    public static final Type<OpenSofaColorScreenPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "open_sofa_color_screen"));

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

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenSofaColorScreenPayload payload) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        minecraft.setScreen(new com.stardew.craft.client.gui.SofaColorSelectionScreen(
            payload.targetPos(), payload.currentColor(), payload.targetEntityId()));
    }
}
