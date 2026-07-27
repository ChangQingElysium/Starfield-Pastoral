package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.economy.StardewCurrencies;
import com.stardew.craft.festival.FairFestivalService;
import com.stardew.craft.shop.ShopCostService;
import com.stardew.craft.shop.ShopRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;

/** Client request for custom cost views of the currently open standard shop. */
public record ShopCostRequestPayload(
        String shopId
) implements CustomPacketPayload {
    public static final Type<ShopCostRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID, "shop_cost_request"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            ShopCostRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    ShopCostRequestPayload::shopId,
                    ShopCostRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            ShopCostRequestPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player()
                    instanceof ServerPlayer player)) {
                return;
            }
            ShopRegistry.ShopDefinition shop =
                    ShopRegistry.get(payload.shopId());
            if (shop == null) {
                return;
            }
            var items = ShopRegistry.getFilteredItemsForPlayer(
                    payload.shopId(), shop, player);
            ResourceLocation defaultCurrency =
                    FairFestivalService.STAR_TOKEN_SHOP_ID
                            .equals(payload.shopId())
                    ? StardewCurrencies.FAIR_STAR_TOKENS
                    : StardewCurrencies.MONEY;
            ArrayList<ShopCostSnapshotPayload.Row> rows =
                    new ArrayList<>();
            for (int index = 0; index < items.size(); index++) {
                var resolved = ShopCostService.resolve(
                        player, payload.shopId(), items.get(index),
                        1, defaultCurrency);
                if (resolved.isEmpty()
                        || !resolved.get().modified()) {
                    continue;
                }
                rows.add(ShopCostSnapshotPayload.row(
                        player, index, items.get(index).itemId(),
                        resolved.get().cost()));
            }
            PacketDistributor.sendToPlayer(
                    player,
                    new ShopCostSnapshotPayload(
                            payload.shopId(), rows));
        });
    }
}
