package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → Client: drive the desert bus fade overlay.
 * <p>phase 0 = fade to black, phase 1 = fade from black.
 */
@SuppressWarnings("null")
public record DesertBusFadePayload(byte phase, int ticks) implements CustomPacketPayload {

    public static final Type<DesertBusFadePayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "desert_bus_fade"));

    public static final StreamCodec<FriendlyByteBuf, DesertBusFadePayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> { buf.writeByte(p.phase()); buf.writeVarInt(p.ticks()); },
        buf -> new DesertBusFadePayload(buf.readByte(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DesertBusFadePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(DesertBusFadePayload payload) {
        int ticks = Math.max(1, payload.ticks());
        if (payload.phase() == 0) {
            com.stardew.craft.cutscene.runtime.EventScreenFade.startFadeToBlack(ticks);
        } else {
            com.stardew.craft.cutscene.runtime.EventScreenFade.startFadeFromBlack(ticks);
        }
    }
}
