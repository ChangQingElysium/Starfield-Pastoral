package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.economy.StardewCost;
import com.stardew.craft.api.v1.economy.StardewCurrencies;
import com.stardew.craft.api.v1.economy.StardewCurrencyCost;
import com.stardew.craft.api.v1.economy.StardewItemCost;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server snapshot used to render non-legacy standard-shop costs. */
public record ShopCostSnapshotPayload(
        String shopId,
        List<Row> rows
) implements CustomPacketPayload {
    private static final int MAX_ROWS = 2048;
    private static final int MAX_COMPONENTS = 32;

    public ShopCostSnapshotPayload {
        rows = List.copyOf(rows);
    }

    public record Row(
            int index,
            String itemId,
            List<CostView> costs
    ) {
        public Row {
            costs = List.copyOf(costs);
        }
    }

    public record CostView(
            Kind kind,
            ResourceLocation id,
            long amount,
            long available,
            Component displayName,
            ItemStack icon
    ) {
        public CostView {
            displayName = displayName.copy();
            icon = icon.copy();
        }

        @Override
        public Component displayName() {
            return displayName.copy();
        }

        @Override
        public ItemStack icon() {
            return icon.copy();
        }
    }

    public enum Kind {
        CURRENCY,
        ITEM
    }

    public static final Type<ShopCostSnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID, "shop_cost_snapshot"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            ShopCostSnapshotPayload> STREAM_CODEC =
            StreamCodec.of(
                    ShopCostSnapshotPayload::encode,
                    ShopCostSnapshotPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static Row row(
            ServerPlayer player,
            int index,
            String itemId,
            StardewCost cost
    ) {
        ArrayList<CostView> views = new ArrayList<>();
        LinkedHashMap<ResourceLocation, Long> currencies =
                new LinkedHashMap<>();
        LinkedHashMap<ResourceLocation, Long> items =
                new LinkedHashMap<>();
        for (var entry : cost.entries()) {
            if (entry instanceof StardewCurrencyCost currency) {
                mergeAmount(
                        currencies,
                        currency.currency(),
                        currency.amount());
            } else if (entry instanceof StardewItemCost itemCost) {
                mergeAmount(
                        items, itemCost.item(),
                        itemCost.amount());
            }
        }
        for (Map.Entry<ResourceLocation, Long> currency
                : currencies.entrySet()) {
                var definition = StardewCurrencies.definitions()
                        .stream()
                        .filter(candidate -> candidate.id()
                                .equals(currency.getKey()))
                        .findFirst();
                long balance = StardewCurrencies.balance(
                                currency.getKey(), player)
                        .orElse(0L);
                views.add(new CostView(
                        Kind.CURRENCY,
                        currency.getKey(),
                        currency.getValue(),
                        balance,
                        definition.map(
                                        com.stardew.craft.api.v1
                                                .economy.StardewCurrency
                                                ::displayName)
                                .orElseGet(() -> Component.literal(
                                        currency.getKey().toString())),
                        definition.map(
                                        com.stardew.craft.api.v1
                                                .economy.StardewCurrency
                                                ::icon)
                                .orElse(ItemStack.EMPTY)));
        }
        for (Map.Entry<ResourceLocation, Long> itemCost
                : items.entrySet()) {
                var item = BuiltInRegistries.ITEM.get(
                        itemCost.getKey());
                ItemStack icon = item == null || item == Items.AIR
                        ? ItemStack.EMPTY
                        : new ItemStack(item);
                long available = item == null || item == Items.AIR
                        ? 0L
                        : player.getInventory().countItem(item);
                views.add(new CostView(
                        Kind.ITEM,
                        itemCost.getKey(),
                        itemCost.getValue(),
                        available,
                        icon.isEmpty()
                                ? Component.literal(
                                        itemCost.getKey().toString())
                                : icon.getHoverName(),
                        icon));
        }
        return new Row(index, itemId, views);
    }

    private static void mergeAmount(
            Map<ResourceLocation, Long> amounts,
            ResourceLocation id,
            long amount
    ) {
        try {
            amounts.merge(id, amount, Math::addExact);
        } catch (ArithmeticException overflow) {
            amounts.put(id, Long.MAX_VALUE);
        }
    }

    public static void handle(
            ShopCostSnapshotPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.neoforged.api.distmarker.OnlyIn(
            net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(
            ShopCostSnapshotPayload payload
    ) {
        var minecraft =
                net.minecraft.client.Minecraft.getInstance();
        if (minecraft.screen
                instanceof com.stardew.craft.client.gui.ShopScreen
                        screen
                && screen.acceptsPurchaseResult(
                        payload.shopId())) {
            screen.applyCostSnapshot(payload);
        }
    }

    private static void encode(
            RegistryFriendlyByteBuf buffer,
            ShopCostSnapshotPayload payload
    ) {
        buffer.writeUtf(payload.shopId(), 256);
        buffer.writeVarInt(Math.min(
                payload.rows().size(), MAX_ROWS));
        for (Row row : payload.rows().stream()
                .limit(MAX_ROWS).toList()) {
            buffer.writeVarInt(row.index());
            buffer.writeUtf(row.itemId(), 256);
            buffer.writeVarInt(Math.min(
                    row.costs().size(), MAX_COMPONENTS));
            for (CostView cost : row.costs().stream()
                    .limit(MAX_COMPONENTS).toList()) {
                buffer.writeEnum(cost.kind());
                buffer.writeResourceLocation(cost.id());
                buffer.writeVarLong(cost.amount());
                buffer.writeVarLong(cost.available());
                ComponentSerialization.TRUSTED_STREAM_CODEC
                        .encode(buffer, cost.displayName());
                ItemStack.OPTIONAL_STREAM_CODEC
                        .encode(buffer, cost.icon());
            }
        }
    }

    private static ShopCostSnapshotPayload decode(
            RegistryFriendlyByteBuf buffer
    ) {
        String shopId = buffer.readUtf(256);
        int rowCount = Math.min(
                buffer.readVarInt(), MAX_ROWS);
        ArrayList<Row> rows = new ArrayList<>(rowCount);
        for (int rowIndex = 0;
             rowIndex < rowCount; rowIndex++) {
            int index = buffer.readVarInt();
            String itemId = buffer.readUtf(256);
            int costCount = Math.min(
                    buffer.readVarInt(), MAX_COMPONENTS);
            ArrayList<CostView> costs =
                    new ArrayList<>(costCount);
            for (int costIndex = 0;
                 costIndex < costCount; costIndex++) {
                costs.add(new CostView(
                        buffer.readEnum(Kind.class),
                        buffer.readResourceLocation(),
                        buffer.readVarLong(),
                        buffer.readVarLong(),
                        ComponentSerialization
                                .TRUSTED_STREAM_CODEC
                                .decode(buffer),
                        ItemStack.OPTIONAL_STREAM_CODEC
                                .decode(buffer)));
            }
            rows.add(new Row(index, itemId, costs));
        }
        return new ShopCostSnapshotPayload(shopId, rows);
    }
}
