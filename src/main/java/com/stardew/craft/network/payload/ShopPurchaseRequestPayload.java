package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.shop.ShopPurchaseRequestTracker;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Optional replay-safe shop purchase request.
 *
 * <p>The legacy {@link ShopPurchasePayload} remains registered unchanged.
 * Negotiated clients use this payload and each logical click receives a fresh
 * request ID; retransmitting the same request is ignored before payment.
 */
@SuppressWarnings("null")
public record ShopPurchaseRequestPayload(
        UUID requestId,
        String shopId,
        int itemIndex,
        String itemId,
        int quantity
) implements CustomPacketPayload {
    public static final Type<ShopPurchaseRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID, "shop_purchase_request"));

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            ShopPurchaseRequestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeUUID(payload.requestId());
                        buffer.writeUtf(payload.shopId());
                        buffer.writeInt(payload.itemIndex());
                        buffer.writeUtf(payload.itemId());
                        buffer.writeInt(payload.quantity());
                    },
                    buffer -> new ShopPurchaseRequestPayload(
                            buffer.readUUID(),
                            buffer.readUtf(),
                            buffer.readInt(),
                            buffer.readUtf(),
                            buffer.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public ShopPurchasePayload legacyRequest() {
        return new ShopPurchasePayload(
                shopId, itemIndex, itemId, quantity);
    }

    public static void handle(
            ShopPurchaseRequestPayload payload,
            IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!ShopPurchaseRequestTracker.tryBegin(
                player.getUUID(), payload.requestId())) {
            return;
        }
        ShopPurchasePayload.handle(
                payload.legacyRequest(), context);
    }
}
