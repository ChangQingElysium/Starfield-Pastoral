package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record OpenFairWheelGamePayload(int starTokens, int luckLevel) implements CustomPacketPayload {
    public static final Type<OpenFairWheelGamePayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "open_fair_wheel_game"));

    public static final StreamCodec<ByteBuf, OpenFairWheelGamePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT,
            OpenFairWheelGamePayload::starTokens,
            ByteBufCodecs.INT,
            OpenFairWheelGamePayload::luckLevel,
            OpenFairWheelGamePayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenFairWheelGamePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenFairWheelGamePayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        mc.setScreen(new com.stardew.craft.client.gui.festival.FairWheelGameScreen(
            payload.starTokens(),
            payload.luckLevel()
        ));
    }
}
