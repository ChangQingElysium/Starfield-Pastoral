package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record OpenNightMarketMermaidPayload(boolean gotPearl) implements CustomPacketPayload {
    public static final Type<OpenNightMarketMermaidPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "open_night_market_mermaid")
    );
    public static final StreamCodec<ByteBuf, OpenNightMarketMermaidPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL,
        OpenNightMarketMermaidPayload::gotPearl,
        OpenNightMarketMermaidPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenNightMarketMermaidPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenNightMarketMermaidPayload payload) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.setScreen(new com.stardew.craft.client.gui.NightMarketMermaidScreen(payload.gotPearl()));
        }
    }
}
