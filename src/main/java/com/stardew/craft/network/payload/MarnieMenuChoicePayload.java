package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.shop.MarnieService;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → Server: player chose an option from Marnie's menu.
 * 0 = Supplies, 1 = Purchase Animals, 2 = Leave
 */
@SuppressWarnings("null")
public record MarnieMenuChoicePayload(int choice) implements CustomPacketPayload {

    public static final Type<MarnieMenuChoicePayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "marnie_menu_choice"));

    public static final StreamCodec<FriendlyByteBuf, MarnieMenuChoicePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeVarInt(payload.choice()),
        buf -> new MarnieMenuChoicePayload(buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @SuppressWarnings("null")
    public static void handle(MarnieMenuChoicePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            int choice = payload.choice();
            if (choice < 0 || choice > 3) return;
            MarnieService.handleChoice(player, choice);
        });
    }
}
