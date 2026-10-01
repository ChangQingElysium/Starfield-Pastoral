package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.shop.GuntherService;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → Server: player chose an option from Gunther's menu.
 * 0 = Donate (start donation mode), 1 = End donation / Rearrange
 */
@SuppressWarnings("null")
public record GuntherMenuChoicePayload(int choice) implements CustomPacketPayload {

    public static final Type<GuntherMenuChoicePayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "gunther_menu_choice"));

    public static final StreamCodec<FriendlyByteBuf, GuntherMenuChoicePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeVarInt(payload.choice()),
        buf -> new GuntherMenuChoicePayload(buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @SuppressWarnings("null")
    public static void handle(GuntherMenuChoicePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            int choice = payload.choice();
            if (choice < 0 || choice > 1) return;
            GuntherService.handleChoice(player, choice);
        });
    }
}
