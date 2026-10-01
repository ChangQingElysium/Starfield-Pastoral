package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.shop.BooksellerService;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record BooksellerActionPayload(int choice) implements CustomPacketPayload {
    public static final Type<BooksellerActionPayload> TYPE =
            new Type<>(new ResourceLocation(StardewCraft.MODID, "bookseller_action"));

    public static final StreamCodec<FriendlyByteBuf, BooksellerActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeInt(payload.choice()),
            buf -> new BooksellerActionPayload(buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BooksellerActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BooksellerService.handleMenuChoice(player, payload.choice());
            }
        });
    }
}
