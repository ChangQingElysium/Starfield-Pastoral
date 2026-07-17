package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → Server: player wants to sell the item at inventorySlot.
 *
 * The server validates the item, calculates sell price, removes it from
 * the inventory, and sends back a ShopSellResultPayload.
 */
@SuppressWarnings("null")
public record ShopSellPayload(
    String shopId,
    int    inventorySlot,  // 0-35 in MC (0=hotbar[0] … 8=hotbar[8], 9-35=main)
    int    quantity        // -1 = sell whole stack; otherwise partial
) implements CustomPacketPayload {

    public static final Type<ShopSellPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "shop_sell"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopSellPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ShopSellPayload::shopId,
            ByteBufCodecs.INT,         ShopSellPayload::inventorySlot,
            ByteBufCodecs.INT,         ShopSellPayload::quantity,
            ShopSellPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ShopSellPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof net.minecraft.server.level.ServerPlayer player)) return;
            try {
                handleServer(payload, player);
            } catch (RuntimeException exception) {
                com.stardew.craft.StardewCraft.LOGGER.error(
                    "Failed to sell inventory slot {} at shop {} for {}",
                    payload.inventorySlot(), payload.shopId(), player.getGameProfile().getName(), exception);
                sendResult(player, false, payload.inventorySlot(), 0, 0);
            }
        });
    }

    private static void handleServer(ShopSellPayload payload, net.minecraft.server.level.ServerPlayer player) {
        int slot = payload.inventorySlot();

        com.stardew.craft.shop.ShopRegistry.ShopDefinition shop =
            com.stardew.craft.shop.ShopRegistry.get(payload.shopId());
        if (shop == null) {
            sendResult(player, false, slot, 0, 0);
            return;
        }

        if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
            sendResult(player, false, slot, 0, 0);
            return;
        }
        net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(slot);
        if (stack.isEmpty()) {
            sendResult(player, false, slot, 0, 0);
            return;
        }

        int sellUnit = com.stardew.craft.shop.ShopRegistry.getSellPrice(stack, shop);
        if (sellUnit <= 0) {
            sendResult(player, false, slot, 0, 0);
            return;
        }

        com.stardew.craft.economy.sell.SellQuote quote =
            com.stardew.craft.economy.sell.ProfessionSellPriceService.quoteItem(
                player, stack, com.stardew.craft.economy.sell.SellSource.SHOP_COUNTER);
        if (quote.sellable() && quote.finalUnitPrice() > 0) {
            sellUnit = quote.finalUnitPrice();
        }

        int qty = payload.quantity() < 0
            ? stack.getCount()
            : Math.min(payload.quantity(), stack.getCount());
        long earnedLong = (long) sellUnit * qty;
        if (qty <= 0 || earnedLong > Integer.MAX_VALUE) {
            sendResult(player, false, slot, 0, 0);
            return;
        }
        int earned = (int) earnedLong;

        stack.shrink(qty);
        if (stack.isEmpty()) player.getInventory().setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();

        com.stardew.craft.player.PlayerStardewDataAPI.addMoney(player, earned);
        sendResult(player, true, slot, qty, earned);
    }

    private static void sendResult(net.minecraft.server.level.ServerPlayer player, boolean success,
                                   int slot, int qty, int earned) {
        int money = com.stardew.craft.player.PlayerStardewDataAPI.getMoney(player);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
            new ShopSellResultPayload(success, money, slot, qty, earned));
    }
}
