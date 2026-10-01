package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record FestivalHudStatePayload(boolean hidden) implements CustomPacketPayload {
    public static final Type<FestivalHudStatePayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "festival_hud_state"));

    public static final StreamCodec<FriendlyByteBuf, FestivalHudStatePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeBoolean(payload.hidden()),
        buf -> new FestivalHudStatePayload(buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FestivalHudStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(FestivalHudStatePayload payload) {
        com.stardew.craft.client.hud.FestivalHudState.setHidden(payload.hidden());
    }
}