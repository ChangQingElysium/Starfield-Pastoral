package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.shop.GeodeLootService;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Objects;

/**
 * Client → Server: request to crack a geode in the given inventory slot.
 */
@SuppressWarnings("null")
public record GeodeCrackPayload(int slot) implements CustomPacketPayload {

    public static final Type<GeodeCrackPayload> TYPE =
        new Type<>(Objects.requireNonNull(new ResourceLocation(StardewCraft.MODID, "geode_crack")));

    public static final StreamCodec<FriendlyByteBuf, GeodeCrackPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeVarInt(payload.slot()),
        buf -> new GeodeCrackPayload(buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GeodeCrackPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                GeodeLootService.handleGeodeCrack(sp, payload.slot());
            }
        });
    }
}
