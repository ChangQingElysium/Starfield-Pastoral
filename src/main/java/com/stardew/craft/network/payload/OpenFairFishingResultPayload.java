package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record OpenFairFishingResultPayload(int baseScore, int fishCaught, int perfections, int totalStarTokens)
    implements CustomPacketPayload {
    public static final Type<OpenFairFishingResultPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "open_fair_fishing_result"));

    public static final StreamCodec<ByteBuf, OpenFairFishingResultPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            OpenFairFishingResultPayload::baseScore,
            ByteBufCodecs.VAR_INT,
            OpenFairFishingResultPayload::fishCaught,
            ByteBufCodecs.VAR_INT,
            OpenFairFishingResultPayload::perfections,
            ByteBufCodecs.VAR_INT,
            OpenFairFishingResultPayload::totalStarTokens,
            OpenFairFishingResultPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenFairFishingResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenFairFishingResultPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        mc.setScreen(new com.stardew.craft.client.gui.festival.FairFishingResultScreen(payload));
    }
}
