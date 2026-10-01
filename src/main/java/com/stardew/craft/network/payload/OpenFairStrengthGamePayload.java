package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record OpenFairStrengthGamePayload(int changeSpeed) implements CustomPacketPayload {
    public static final Type<OpenFairStrengthGamePayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "open_fair_strength_game"));

    public static final StreamCodec<ByteBuf, OpenFairStrengthGamePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT,
            OpenFairStrengthGamePayload::changeSpeed,
            OpenFairStrengthGamePayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenFairStrengthGamePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenFairStrengthGamePayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        mc.setScreen(new com.stardew.craft.client.gui.festival.FairStrengthGameScreen(payload.changeSpeed()));
    }
}
