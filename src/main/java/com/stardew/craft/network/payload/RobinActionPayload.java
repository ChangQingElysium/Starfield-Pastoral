package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.shop.RobinService;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → Server: player chose a Robin menu option.
 * 0 = Build (open CarpenterMenu), 1 = Shop (open material shop), 2 = Leave
 */
@SuppressWarnings("null")
public record RobinActionPayload(
    int choice
) implements CustomPacketPayload {

    public static final Type<RobinActionPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "robin_action"));

    public static final StreamCodec<FriendlyByteBuf, RobinActionPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeInt(payload.choice()),
        buf -> new RobinActionPayload(buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RobinActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                RobinService.handleMenuChoice(player, payload.choice());
            }
        });
    }
}
