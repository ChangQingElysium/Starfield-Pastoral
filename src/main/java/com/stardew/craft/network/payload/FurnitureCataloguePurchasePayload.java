package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → Server: player requested a free item from the Furniture Catalogue.
 * Sends item ID directly (not index-based) because the catalogue list is
 * dynamically built and filtered client-side.
 */
@SuppressWarnings("null")
public record FurnitureCataloguePurchasePayload(
    String itemId,
    int    quantity
) implements CustomPacketPayload {

    public static final Type<FurnitureCataloguePurchasePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "furniture_catalogue_purchase"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FurnitureCataloguePurchasePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, FurnitureCataloguePurchasePayload::itemId,
            ByteBufCodecs.INT,         FurnitureCataloguePurchasePayload::quantity,
            FurnitureCataloguePurchasePayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FurnitureCataloguePurchasePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            int qty = Math.max(1, payload.quantity());

            // Validate: item must exist and be a furniture item or wallpaper/flooring
            ResourceLocation rl;
            try {
                rl = ResourceLocation.parse(payload.itemId());
            } catch (Exception e) {
                return;
            }

            Item mcItem = BuiltInRegistries.ITEM.get(rl);
            if (mcItem == null || mcItem == Items.AIR) return;

            // Security: only allow furniture-type items, wallpaper, or flooring
            boolean allowed = false;
            {
                String typeKey = StardewItemDataApi.getTypeKey(new ItemStack(mcItem));
                if ("stardewcraft.type.furniture".equals(typeKey) ||
                    "stardewcraft.type.utility".equals(typeKey)) {
                    allowed = true;
                }
            }
            // Also allow explicitly by ID for wallpaper/flooring
            String id = payload.itemId();
            if (id.equals("stardewcraft:wallpaper_block") || id.equals("stardewcraft:flooring_block")) {
                allowed = true;
            }
            if (!allowed) return;

            // Clamp to max stack size
            qty = Math.min(qty, mcItem.getDefaultMaxStackSize());

            // Grant items directly to inventory
            ItemStack grant = new ItemStack(mcItem, qty);
            if (!player.getInventory().add(grant)) {
                // Drop on ground if inventory full
                player.drop(grant, false);
            }

            // Send result back to client
            int money = com.stardew.craft.player.PlayerStardewDataAPI.getMoney(player);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new FurnitureCatalogueResultPayload(true, money, payload.itemId(), qty));
        });
    }
}
