package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Legacy client payload retained for protocol compatibility. Purchases are now
 * granted atomically by {@link ShopPurchasePayload}; accepting an item ID from a
 * follow-up client packet would allow unverified item creation.
 */
@SuppressWarnings("null")
public record ShopPickupPayload(
    String itemId,
    int    quantity,
    int    targetSlot  // ≥0 = place in this specific inventory slot; -1 = auto (first available)
) implements CustomPacketPayload {

    public static final Type<ShopPickupPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "shop_pickup"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopPickupPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ShopPickupPayload::itemId,
            ByteBufCodecs.INT,         ShopPickupPayload::quantity,
            ByteBufCodecs.INT,         ShopPickupPayload::targetSlot,
            ShopPickupPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ShopPickupPayload payload, IPayloadContext context) {
        // Intentionally ignored. The server has already delivered validated purchases.
    }
}
