package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → Server: the player answered the desert bus confirmation.
 */
@SuppressWarnings("null")
public record DesertBusConfirmPayload(boolean confirmed) implements CustomPacketPayload {

    public static final Type<DesertBusConfirmPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "desert_bus_confirm"));

    public static final StreamCodec<FriendlyByteBuf, DesertBusConfirmPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> buf.writeBoolean(p.confirmed()),
        buf -> new DesertBusConfirmPayload(buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DesertBusConfirmPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!payload.confirmed()) return;
            com.stardew.craft.desert.DesertBusService.onPlayerConfirmed(player);
        });
    }
}
