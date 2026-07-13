package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.festival.nightmarket.NightMarketMermaidService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record NightMarketMermaidActionPayload(int clamIndex) implements CustomPacketPayload {
    public static final int CLOSE = -1;
    public static final Type<NightMarketMermaidActionPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "night_market_mermaid_action")
    );
    public static final StreamCodec<ByteBuf, NightMarketMermaidActionPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        NightMarketMermaidActionPayload::clamIndex,
        NightMarketMermaidActionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(NightMarketMermaidActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (payload.clamIndex() == CLOSE) {
                NightMarketMermaidService.close(player);
            } else {
                NightMarketMermaidService.handleClam(player, payload.clamIndex());
            }
        });
    }
}
