package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.shop.ShopItemEntry;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Server → Client payload that instructs the client to open the ShopScreen.
 * Mirrors SDV's approach: all data is sent in one packet.
 *
 * acceptedSellTypeKeys: set of IStardewItem.getItemTypeKey() values whose
 *   items this shop will buy from the player (mirrors SDV SalableItemTags).
 *   Empty = nothing can be sold here.
 */
@SuppressWarnings("null")
public record OpenShopScreenPayload(
    String             shopId,
    int                playerMoney,
    List<ShopItemEntry> items,
    String             ownerNpcId,
    String             ownerDialogue,
    List<String>       acceptedSellTypeKeys   // serialised as list; client converts to Set
) implements CustomPacketPayload {

    // ---- ShopItemEntry stream codec -----------------------------------------

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopItemEntry> ITEM_CODEC =
        StreamCodec.of(
            (buf, e) -> {
                buf.writeUtf(e.itemId());
                buf.writeUtf(e.displayName());
                buf.writeUtf(e.description());
                buf.writeInt(e.price());
                buf.writeInt(e.stock());
                buf.writeUtf(e.tradeItemId() != null ? e.tradeItemId() : "");
                buf.writeInt(e.tradeItemCount());
                buf.writeInt(e.purchaseStack());
                // seasons and minYear are server-side only; not transmitted
            },
            buf -> ShopItemEntry.fromNetwork(
                buf.readUtf(),  // itemId
                buf.readUtf(),  // displayName
                buf.readUtf(),  // description
                buf.readInt(),  // price
                buf.readInt(),  // stock
                buf.readUtf(),  // tradeItemId
                buf.readInt(),  // tradeItemCount
                buf.readInt()   // purchaseStack
            )
        );

    // ---- Payload plumbing ---------------------------------------------------

    public static final Type<OpenShopScreenPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "open_shop_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenShopScreenPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,            OpenShopScreenPayload::shopId,
            ByteBufCodecs.INT,                    OpenShopScreenPayload::playerMoney,
            ITEM_CODEC.apply(ByteBufCodecs.list()),OpenShopScreenPayload::items,
            ByteBufCodecs.STRING_UTF8,            OpenShopScreenPayload::ownerNpcId,
            ByteBufCodecs.STRING_UTF8,            OpenShopScreenPayload::ownerDialogue,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                                                  OpenShopScreenPayload::acceptedSellTypeKeys,
            OpenShopScreenPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenShopScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenShopScreenPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        if ("FurnitureCatalogue".equals(payload.shopId())) {
            mc.setScreen(new com.stardew.craft.client.gui.FurnitureCatalogueScreen(
                payload.items(), payload.playerMoney()));
        } else {
            mc.setScreen(new com.stardew.craft.client.gui.ShopScreen(payload));
        }
    }
}
