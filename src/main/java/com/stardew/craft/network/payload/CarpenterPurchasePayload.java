package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.building.StardewBuildingBlueprint;
import com.stardew.craft.api.v1.building.StardewBuildingBuilders;
import com.stardew.craft.building.BuildingBlueprintRegistry;
import com.stardew.craft.building.BuildingCatalogService;
import com.stardew.craft.item.WizardBuildingItem;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.shop.WizardBuildingService;
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
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Client request for a stable blueprint ID from an authorized catalog snapshot.
 */
@SuppressWarnings("null")
public record CarpenterPurchasePayload(
        String builder,
        int blueprintIndex,
        String blueprintId,
        long catalogRevision
) implements CustomPacketPayload {
    public static final Type<CarpenterPurchasePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID, "carpenter_purchase"));

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            CarpenterPurchasePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public CarpenterPurchasePayload decode(
                        RegistryFriendlyByteBuf buffer
                ) {
                    return new CarpenterPurchasePayload(
                            ByteBufCodecs.STRING_UTF8.decode(buffer),
                            buffer.readInt(),
                            ByteBufCodecs.STRING_UTF8.decode(buffer),
                            buffer.readVarLong());
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        CarpenterPurchasePayload payload
                ) {
                    ByteBufCodecs.STRING_UTF8.encode(
                            buffer, payload.builder());
                    buffer.writeInt(payload.blueprintIndex());
                    ByteBufCodecs.STRING_UTF8.encode(
                            buffer, payload.blueprintId());
                    buffer.writeVarLong(payload.catalogRevision());
                }
            };

    /**
     * Source-compatible constructor. Requests without the server-issued
     * blueprint ID and revision are intentionally not authorized.
     */
    public CarpenterPurchasePayload(
            String builder,
            int blueprintIndex
    ) {
        this(builder, blueprintIndex, "", -1L);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            CarpenterPurchasePayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ResourceLocation builder =
                    ResourceLocation.tryParse(payload.builder());
            ResourceLocation blueprintId =
                    ResourceLocation.tryParse(payload.blueprintId());
            if (builder == null || blueprintId == null
                    || !BuildingCatalogService.authorizes(
                            player, builder, blueprintId,
                            payload.catalogRevision())) {
                fail(player, payload.blueprintIndex());
                return;
            }
            StardewBuildingBlueprint blueprint =
                    BuildingBlueprintRegistry.find(blueprintId)
                            .orElse(null);
            if (blueprint == null
                    || !blueprint.definition().builder().equals(builder)
                    || BuildingBlueprintRegistry.availableFor(
                            player, builder).stream()
                            .noneMatch(candidate ->
                                    candidate.id().equals(blueprintId))) {
                fail(player, payload.blueprintIndex());
                return;
            }
            if (StardewBuildingBuilders.WIZARD.equals(builder)
                    && !WizardBuildingService.canUse(player)) {
                fail(player, payload.blueprintIndex());
                return;
            }
            purchase(player, blueprint, payload.blueprintIndex());
        });
    }

    private static void purchase(
            ServerPlayer player,
            StardewBuildingBlueprint blueprint,
            int clientIndex
    ) {
        var definition = blueprint.definition();
        int currentMoney = PlayerStardewDataAPI.getMoney(player);
        if (currentMoney < definition.money()) {
            fail(player, clientIndex);
            return;
        }

        Item resultItem = BuiltInRegistries.ITEM.get(
                definition.resultItem());
        if (resultItem == null || resultItem == Items.AIR) {
            fail(player, clientIndex);
            return;
        }

        ArrayList<Consumption> plan = new ArrayList<>();
        for (var material : definition.materials()) {
            Item item = BuiltInRegistries.ITEM.get(material.item());
            if (item == null || item == Items.AIR
                    || !planConsumption(
                            player, item, material.count(), plan)) {
                fail(player, clientIndex);
                return;
            }
        }

        if (definition.money() > 0
                && !PlayerStardewDataAPI.removeMoney(
                        player, definition.money())) {
            fail(player, clientIndex);
            return;
        }

        // The complete slot plan was validated on this server task before
        // payment; no partially consumed material loop can now fail.
        for (Consumption consumption : plan) {
            player.getInventory().getItem(consumption.slot())
                    .shrink(consumption.count());
        }

        ItemStack resultStack = new ItemStack(
                resultItem, definition.resultCount());
        if (resultItem instanceof WizardBuildingItem) {
            WizardBuildingItem.bindTo(resultStack, player);
        }
        if (!player.getInventory().add(resultStack)) {
            player.drop(resultStack, false);
        }
        sendResult(
                player, true,
                PlayerStardewDataAPI.getMoney(player),
                definition.resultItem().toString(),
                clientIndex);
    }

    private static boolean planConsumption(
            ServerPlayer player,
            Item item,
            int count,
            List<Consumption> plan
    ) {
        int remaining = count;
        for (int slotIndex = 0;
             slotIndex < player.getInventory().getContainerSize()
                     && remaining > 0;
             slotIndex++) {
            ItemStack stack = player.getInventory().getItem(slotIndex);
            if (!stack.isEmpty() && stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                plan.add(new Consumption(slotIndex, take));
                remaining -= take;
            }
        }
        return remaining == 0;
    }

    private static void fail(
            ServerPlayer player,
            int blueprintIndex
    ) {
        sendResult(
                player, false,
                PlayerStardewDataAPI.getMoney(player),
                "", blueprintIndex);
    }

    private static void sendResult(
            ServerPlayer player,
            boolean success,
            int newMoney,
            String resultItemId,
            int blueprintIndex
    ) {
        PacketDistributor.sendToPlayer(player,
                new CarpenterPurchaseResultPayload(
                        success, newMoney, resultItemId,
                        blueprintIndex));
    }

    private record Consumption(int slot, int count) {
    }
}
