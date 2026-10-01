package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.shop.MarlonService;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → Server: player chose an option from Marlon's menu.
 * 0 = Shop, 1 = Monster Slayer Goals, 2 = Recovery, 3 = Desert Festival rating, 4 = Desert Festival challenge
 */
@SuppressWarnings("null")
public record MarlonMenuChoicePayload(int choice) implements CustomPacketPayload {

    public static final Type<MarlonMenuChoicePayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "marlon_menu_choice"));

    public static final StreamCodec<FriendlyByteBuf, MarlonMenuChoicePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeVarInt(payload.choice()),
        buf -> new MarlonMenuChoicePayload(buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @SuppressWarnings("null")
    public static void handle(MarlonMenuChoicePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            int choice = payload.choice();
            if (choice < 0 || choice > 4) return;
            MarlonService.handleChoice(player, choice);
        });
    }
}
