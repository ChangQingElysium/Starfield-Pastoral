package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record FestivalCurrencyHudStatePayload(byte currencyType) implements CustomPacketPayload {
    public static final byte NONE = 0;
    public static final byte FAIR_STAR_TOKEN = 1;
    public static final byte CALICO_EGG = 2;

    public static final Type<FestivalCurrencyHudStatePayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "festival_currency_hud_state"));

    public static final StreamCodec<FriendlyByteBuf, FestivalCurrencyHudStatePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeByte(payload.currencyType()),
        buf -> new FestivalCurrencyHudStatePayload(buf.readByte())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FestivalCurrencyHudStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(FestivalCurrencyHudStatePayload payload) {
        com.stardew.craft.client.hud.FestivalCurrencyHudState.set(payload.currencyType());
    }
}
