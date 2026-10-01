package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record OpenNightMarketMermaidPayload(boolean gotPearl) implements CustomPacketPayload {
    public static final Type<OpenNightMarketMermaidPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "open_night_market_mermaid")
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

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenNightMarketMermaidPayload payload) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.setScreen(new com.stardew.craft.client.gui.NightMarketMermaidScreen(payload.gotPearl()));
        }
    }
}
