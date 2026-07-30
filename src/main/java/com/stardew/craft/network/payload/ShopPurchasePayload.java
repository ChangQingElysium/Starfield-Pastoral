package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.economy.StardewCosts;
import com.stardew.craft.api.v1.economy.StardewCurrencies;
import com.stardew.craft.api.v1.economy.StardewPaymentResult;
import com.stardew.craft.api.v1.internal.shop.StardewShopProductRegistry;
import com.stardew.craft.api.v1.shop.StardewShopProductContext;
import com.stardew.craft.api.v1.shop.StardewShopProductDecision;
import com.stardew.craft.shop.ShopCostService;
import com.stardew.craft.shop.ShopItemEntry;
import com.stardew.craft.shop.ShopRegistry;
import com.stardew.craft.shop.ShopStockTracker;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Client → Server: player wants to buy qty units of an item from a shop.
 *
 * itemIndex is the client-side slot index for UI stock sync.
 * itemId is the stable server-side identity used to avoid index drift when
 * sold-out entries remain visible client-side but are filtered out server-side.
 */
@SuppressWarnings("null")
public record ShopPurchasePayload(
    String shopId,
    int itemIndex,
    String itemId,
    int quantity
) implements CustomPacketPayload {

    public static final Type<ShopPurchasePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "shop_purchase"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopPurchasePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ShopPurchasePayload::shopId,
            ByteBufCodecs.INT,         ShopPurchasePayload::itemIndex,
            ByteBufCodecs.STRING_UTF8, ShopPurchasePayload::itemId,
            ByteBufCodecs.INT,         ShopPurchasePayload::quantity,
            ShopPurchasePayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ShopPurchasePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            // Special handling for Clint's tool upgrade shop (dynamic, per-player)
            if (payload.shopId().equals("ClintUpgrade")) {
                com.stardew.craft.shop.BlacksmithService.handleToolUpgradePurchaseFromShop(
                    player, payload.itemIndex(), payload.quantity());
                return;
            }

            // Special handling for Furniture Catalogue (dynamic item list, all free)
            if (payload.shopId().equals(com.stardew.craft.block.decor.FurnitureCatalogueBlock.SHOP_ID)) {
                handleFurnitureCataloguePurchase(player, payload.itemIndex(), payload.quantity());
                return;
            }

            // Special handling for Marlon's item recovery shop (SDV parity)
            // SDV: player buys ONE item → that item is returned → ALL lost items cleared
            if (payload.shopId().equals("MarlonRecovery")) {
                com.stardew.craft.shop.MarlonService.handleRecoveryPurchaseFromShop(
                    player, payload.itemIndex());
                return;
            }

            if (payload.shopId().startsWith(com.stardew.craft.lostandfound.LostAndFoundService.SHOP_ID_PREFIX)) {
                com.stardew.craft.lostandfound.LostAndFoundService.claim(
                    player, payload.shopId(), payload.itemIndex(), payload.itemId());
                return;
            }

            ShopRegistry.ShopDefinition shop = ShopRegistry.get(payload.shopId());
            if (shop == null) return;

            // Rebuild the current filtered list. Client-side sold-out entries may still be visible as
            // greyed-out rows, so itemIndex alone is not stable enough after a purchase.
            List<ShopItemEntry> items = ShopRegistry.getFilteredItemsForPlayer(
                payload.shopId(), shop, player);
            ShopItemEntry entry = null;
            if (payload.itemIndex() >= 0 && payload.itemIndex() < items.size()) {
                ShopItemEntry indexed = items.get(payload.itemIndex());
                if (indexed.itemId().equals(payload.itemId())) {
                    entry = indexed;
                }
            }
            if (entry == null) {
                for (ShopItemEntry candidate : items) {
                    if (candidate.itemId().equals(payload.itemId())) {
                        entry = candidate;
                        break;
                    }
                }
            }
            if (entry == null) return;

            int qty = entry.itemId().startsWith("recipe:") ? 1 : Math.max(1, payload.quantity());
            ResourceLocation defaultCurrency = isCasinoShop(payload.shopId())
                    ? StardewCurrencies.QI_COINS
                    : StardewCurrencies.MONEY;

            // Clamp to available stock (also checks per-player daily stock via tracker)
            if (entry.stock() != Integer.MAX_VALUE) {
                qty = Math.min(qty, entry.stock());
                if (qty <= 0) {
                    sendResult(player, payload.shopId(), false,
                        currentShopBalance(player, defaultCurrency),
                        "", 0, payload.itemIndex());
                    return;
                }
            }

            if (isFairStarTokenShop(payload.shopId())) {
                if (handleVirtualProduct(
                        player, payload, entry, qty,
                        StardewCurrencies.FAIR_STAR_TOKENS)) {
                    return;
                }
                handleFairStarTokenPurchase(
                    player, payload, entry, qty,
                    entry.price() * qty);
                return;
            }

            if (handleVirtualProduct(
                    player, payload, entry, qty,
                    defaultCurrency)) {
                return;
            }

            // Validate the physical item before confirming. The client holds the
            // confirmed result on the cursor and sends ShopPickupPayload when placed.
            int deliveredQuantity = qty * Math.max(1, entry.purchaseStack());
            if (!isValidPhysicalItem(entry.itemId())) {
                sendResult(player, payload.shopId(), false,
                        currentShopBalance(player, defaultCurrency),
                        "", 0, payload.itemIndex());
                return;
            }

            StardewPaymentResult payment = payShopCost(
                player, payload.shopId(), entry, qty,
                defaultCurrency);
            if (!payment.success()) {
                sendResult(player, payload.shopId(), false,
                    currentShopBalance(player, defaultCurrency),
                    "", 0, payload.itemIndex());
                return;
            }

            int newMoney = currentShopBalance(player, defaultCurrency);

            // Record the purchase so remaining daily stock is tracked (SDV: SynchronizedShopStock parity)
            if (entry.stock() != Integer.MAX_VALUE) {
                ShopStockTracker.recordPurchase(
                        player, payload.shopId(),
                        entry.itemId(), qty);
            }

            if (payload.shopId().equals("ShadowShop")
                    && entry.itemId().equals("stardewcraft:stardrop")) {
                com.stardew.craft.player.PlayerStardewData data =
                    com.stardew.craft.player.PlayerDataManager.getPlayerData(player);
                data.addMailFlag(com.stardew.craft.sewer.SewerStoryFlags.SEWER_STARDROP_PURCHASED);
                com.stardew.craft.player.PlayerDataManager.get().savePlayerData(player.getUUID(), data);
                com.stardew.craft.player.PlayerDataEventHandler.syncPlayerData(player, data);
            }
            if (payload.shopId().equals("ShadowShop")
                    && entry.itemId().equals("stardewcraft:warp_wand")) {
                com.stardew.craft.player.PlayerStardewData data =
                    com.stardew.craft.player.PlayerDataManager.getPlayerData(player);
                data.addMailFlag(com.stardew.craft.sewer.SewerStoryFlags.RETURN_SCEPTER_PURCHASED);
                data.addSpecialItem(com.stardew.craft.sewer.SewerStoryFlags.RETURN_SCEPTER_SPECIAL_ITEM);
                com.stardew.craft.player.PlayerDataManager.get().savePlayerData(player.getUUID(), data);
                com.stardew.craft.player.PlayerDataEventHandler.syncPlayerData(player, data);
            }

            ShopPickupPayload.recordValidatedPurchase(player, entry.itemId(), deliveredQuantity);
            sendResult(player, payload.shopId(), true, newMoney, entry.itemId(), deliveredQuantity, payload.itemIndex());
        });
    }

    private static boolean isValidPhysicalItem(String itemId) {
        try {
            ResourceLocation id = ResourceLocation.parse(itemId);
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
            return item != null && item != net.minecraft.world.item.Items.AIR;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void sendResult(ServerPlayer player, String shopId, boolean ok, int money, String itemId, int qty, int idx) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
            new com.stardew.craft.network.payload.ShopPurchaseResultPayload(ok, shopId, money, itemId, qty, idx));
    }

    private static boolean isFairStarTokenShop(String shopId) {
        return com.stardew.craft.festival.FairFestivalService.STAR_TOKEN_SHOP_ID.equals(shopId);
    }

    private static boolean isCasinoShop(String shopId) {
        return com.stardew.craft.casino.CasinoContentService.SHOP_ID.equals(shopId);
    }

    private static boolean handleVirtualProduct(
            ServerPlayer player,
            ShopPurchasePayload payload,
            ShopItemEntry entry,
            int quantity,
            ResourceLocation defaultCurrency
    ) {
        StardewShopProductContext productContext =
                new StardewShopProductContext(
                        player, payload.shopId(),
                        ShopCostService.toApiEntry(entry), quantity);
        var resolution =
                StardewShopProductRegistry.resolve(productContext);
        if (resolution.decision()
                == StardewShopProductDecision.PASS) {
            return false;
        }
        int currentBalance = currentShopBalance(
                player, defaultCurrency);
        if (resolution.decision()
                == StardewShopProductDecision.REJECT) {
            sendResult(player, payload.shopId(), false,
                    currentBalance, "", 0, payload.itemIndex());
            return true;
        }
        StardewPaymentResult payment = payShopCost(
                player, payload.shopId(), entry, quantity,
                defaultCurrency);
        if (!payment.success()) {
            sendResult(player, payload.shopId(), false,
                    currentShopBalance(player, defaultCurrency),
                    "", 0, payload.itemIndex());
            return true;
        }
        if (!StardewShopProductRegistry.grant(
                resolution, productContext)) {
            boolean refunded =
                    payment.receipt().orElseThrow().refund();
            if (!refunded) {
                StardewCraft.LOGGER.error(
                        "Failed to refund virtual shop product "
                                + "{} / {} for {}",
                        payload.shopId(), entry.itemId(),
                        player.getGameProfile().getName());
            }
            sendResult(player, payload.shopId(), false,
                    currentShopBalance(player, defaultCurrency),
                    "", 0, payload.itemIndex());
            return true;
        }
        if (entry.stock() != Integer.MAX_VALUE) {
            ShopStockTracker.recordPurchase(
                    player, payload.shopId(),
                    entry.itemId(), quantity);
        }
        sendResult(player, payload.shopId(), true,
                currentShopBalance(player, defaultCurrency),
                "", quantity, payload.itemIndex());
        return true;
    }

    private static int currentShopBalance(
            ServerPlayer player,
            ResourceLocation currency
    ) {
        long balance = StardewCurrencies.balance(
                currency, player).orElse(0L);
        return (int) Math.min(Integer.MAX_VALUE, balance);
    }

    private static void handleFairStarTokenPurchase(ServerPlayer player, ShopPurchasePayload payload,
                                                    ShopItemEntry entry, int qty,
                                                    int compatibilityTotalPrice) {
        int currentTokens = com.stardew.craft.player.PlayerStardewDataAPI.getFairStarTokens(player);
        if (qty != 1 || entry.requiresTrade()) {
            sendResult(player, payload.shopId(), false, currentTokens, "", 0, payload.itemIndex());
            return;
        }

        if ("stardewcraft:stardrop".equals(entry.itemId())) {
            com.stardew.craft.player.PlayerStardewData data =
                com.stardew.craft.player.PlayerDataManager.getPlayerData(player);
            if (data.hasMailFlag(com.stardew.craft.festival.FairFestivalService.FAIR_STARDROP_FLAG)) {
                sendResult(player, payload.shopId(), false, currentTokens, "", 0, payload.itemIndex());
                return;
            }
            StardewPaymentResult payment = payShopCost(
                player, payload.shopId(), entry, qty,
                StardewCurrencies.FAIR_STAR_TOKENS);
            if (!payment.success()) {
                sendResult(player, payload.shopId(), false, currentTokens, "", 0, payload.itemIndex());
                return;
            }
            if (!com.stardew.craft.item.misc.StardropItem.consumeImmediately(player)) {
                payment.receipt().orElseThrow().refund();
                sendResult(player, payload.shopId(), false, currentTokens, "", 0, payload.itemIndex());
                return;
            }
            data = com.stardew.craft.player.PlayerDataManager.getPlayerData(player);
            data.addMailFlag(com.stardew.craft.festival.FairFestivalService.FAIR_STARDROP_FLAG);
            com.stardew.craft.player.PlayerDataManager.get().savePlayerData(player.getUUID(), data);
            com.stardew.craft.player.PlayerDataEventHandler.syncPlayerData(player, data);
            if (entry.stock() != Integer.MAX_VALUE) {
                ShopStockTracker.recordPurchase(
                        player, payload.shopId(),
                        entry.itemId(), qty);
            }
            sendResult(player, payload.shopId(), true,
                com.stardew.craft.player.PlayerStardewDataAPI.getFairStarTokens(player),
                "", 0, payload.itemIndex());
            return;
        }

        try {
            ResourceLocation rl = ResourceLocation.parse(entry.itemId());
            net.minecraft.world.item.Item mcItem =
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
            if (mcItem == null || mcItem == net.minecraft.world.item.Items.AIR) {
                sendResult(player, payload.shopId(), false, currentTokens, "", 0, payload.itemIndex());
                return;
            }
        } catch (Exception e) {
            sendResult(player, payload.shopId(), false, currentTokens, "", 0, payload.itemIndex());
            return;
        }

        if (!payShopCost(
                player, payload.shopId(), entry, qty,
                StardewCurrencies.FAIR_STAR_TOKENS).success()) {
            sendResult(player, payload.shopId(), false, currentTokens, "", 0, payload.itemIndex());
            return;
        }
        int deliveredQuantity = qty * Math.max(1, entry.purchaseStack());
        if (entry.stock() != Integer.MAX_VALUE) {
            ShopStockTracker.recordPurchase(
                    player, payload.shopId(),
                    entry.itemId(), qty);
        }
        ShopPickupPayload.recordValidatedPurchase(player, entry.itemId(), deliveredQuantity);
        sendResult(player, payload.shopId(), true,
            com.stardew.craft.player.PlayerStardewDataAPI.getFairStarTokens(player),
            entry.itemId(), deliveredQuantity, payload.itemIndex());
    }

    private static StardewPaymentResult payShopCost(
            ServerPlayer player,
            String shopId,
            ShopItemEntry entry,
            int quantity,
            ResourceLocation currencyId
    ) {
        return ShopCostService.resolve(
                        player, shopId, entry, quantity,
                        currencyId)
                .map(resolved -> StardewCosts.pay(
                        player, resolved.cost()))
                .orElseGet(() ->
                        StardewPaymentResult.failed(
                                "invalid_shop_cost"));
    }

    /**
     * Handles purchases from the Furniture Catalogue.
     * All items are free and unlimited — just validate the index and send success.
     */
    private static void handleFurnitureCataloguePurchase(ServerPlayer player, int itemIndex, int quantity) {
        List<ShopItemEntry> items = com.stardew.craft.block.decor.FurnitureCatalogueBlock.buildCatalogueItems();
        if (itemIndex < 0 || itemIndex >= items.size()) return;

        ShopItemEntry entry = items.get(itemIndex);
        int qty = Math.max(1, quantity);
        int money = com.stardew.craft.player.PlayerStardewDataAPI.getMoney(player);

        // Validate the item exists in MC registry
        try {
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.parse(entry.itemId());
            net.minecraft.world.item.Item mcItem =
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
            if (mcItem == null || mcItem == net.minecraft.world.item.Items.AIR) {
                sendResult(player, com.stardew.craft.block.decor.FurnitureCatalogueBlock.SHOP_ID, false, money, "", 0, itemIndex);
                return;
            }
        } catch (Exception e) {
            sendResult(player, com.stardew.craft.block.decor.FurnitureCatalogueBlock.SHOP_ID, false, money, "", 0, itemIndex);
            return;
        }

        // Free purchase — no money deduction or stock tracking.
        ShopPickupPayload.recordValidatedPurchase(player, entry.itemId(), qty);
        sendResult(player, com.stardew.craft.block.decor.FurnitureCatalogueBlock.SHOP_ID, true, money, entry.itemId(), qty, itemIndex);
    }
}
