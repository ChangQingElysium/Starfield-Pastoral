package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.festival.WinterStarFestivalService;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: selected inventory slot for the secret gift. */
@SuppressWarnings("null")
public record WinterStarGiftSelectionPayload(String npcId, int slot) implements CustomPacketPayload {
    public static final Type<WinterStarGiftSelectionPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "winter_star_gift_selection"));
    public static final StreamCodec<FriendlyByteBuf, WinterStarGiftSelectionPayload> STREAM_CODEC = StreamCodec.of(
        (buf, value) -> {
            buf.writeUtf(value.npcId(), 64);
            buf.writeVarInt(value.slot());
        },
        buf -> new WinterStarGiftSelectionPayload(buf.readUtf(64), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WinterStarGiftSelectionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                WinterStarFestivalService.handleSelectedSecretGift(player, payload.npcId(), payload.slot());
            }
        });
    }
}
