package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.shop.BlacksmithService;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → Server: player chose a blacksmith menu option.
 * 0 = Shop, 1 = Upgrade, 2 = Process (geode), 3 = Leave
 */
@SuppressWarnings("null")
public record BlacksmithActionPayload(
    int choice
) implements CustomPacketPayload {

    public static final Type<BlacksmithActionPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "blacksmith_action"));

    public static final StreamCodec<FriendlyByteBuf, BlacksmithActionPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeInt(payload.choice()),
        buf -> new BlacksmithActionPayload(buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BlacksmithActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BlacksmithService.handleMenuChoice(player, payload.choice());
            }
        });
    }
}
