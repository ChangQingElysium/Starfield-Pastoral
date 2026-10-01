package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record NightMarketStatePayload(boolean open) implements CustomPacketPayload {
    public static final Type<NightMarketStatePayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "night_market_state")
    );
    public static final StreamCodec<ByteBuf, NightMarketStatePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL,
        NightMarketStatePayload::open,
        NightMarketStatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(NightMarketStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(NightMarketStatePayload payload) {
        com.stardew.craft.client.sound.StardewMusicManager.setNightMarketFestivalOpen(payload.open());
    }
}
