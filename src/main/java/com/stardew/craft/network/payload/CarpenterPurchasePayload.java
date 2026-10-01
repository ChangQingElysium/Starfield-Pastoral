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
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import com.stardew.craft.building.runtime.BuildingPurchasePlan;

/**
 * Client request for a stable blueprint ID from an authorized catalog snapshot.
 */
@SuppressWarnings("null")
public record CarpenterPurchasePayload(
        String builder,
        int blueprintIndex,
        String blueprintId,
        long catalogRevision,
        java.util.UUID requestId
) implements CustomPacketPayload {
    public static final Type<CarpenterPurchasePayload> TYPE =
            new Type<>(new ResourceLocation(
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
                            buffer.readVarLong(), buffer.readUUID());
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
                    buffer.writeUUID(payload.requestId());
                }
            };

    public CarpenterPurchasePayload(String builder, int blueprintIndex, String blueprintId, long catalogRevision) {
        this(builder, blueprintIndex, blueprintId, catalogRevision, java.util.UUID.randomUUID());
    }

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
                fail(player, payload.blueprintIndex(), payload.requestId());
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
                fail(player, payload.blueprintIndex(), payload.requestId());
                return;
            }
            if (StardewBuildingBuilders.WIZARD.equals(builder)
                    && !WizardBuildingService.canUse(player)) {
                fail(player, payload.blueprintIndex(), payload.requestId());
                return;
            }
            if (com.stardew.craft.building.runtime.UtilityBuildings.managed(blueprintId)) {
                com.stardew.craft.building.runtime.BuildingPurchaseService.openChoices(player, payload.catalogRevision(), blueprintId, payload.requestId());
                return;
            }
            if (BuiltInRegistries.ITEM.get(blueprint.definition().resultItem()) instanceof com.stardew.craft.building.runtime.BuildingUpgradePermitItem permit) {
                com.stardew.craft.building.runtime.BuildingPurchaseService.purchaseUpgrade(player, blueprint, permit, payload.requestId());
                return;
            }
            purchase(player, blueprint, payload.blueprintIndex(), payload.requestId());
        });
    }

    private static void purchase(
            ServerPlayer player,
            StardewBuildingBlueprint blueprint,
            int clientIndex, java.util.UUID requestId
    ) {
        var definition = blueprint.definition();
        int currentMoney = PlayerStardewDataAPI.getMoney(player);
        if (currentMoney < definition.money()) {
            fail(player, clientIndex, requestId, "livestock.stardewcraft.money");
            return;
        }

        Item resultItem = BuiltInRegistries.ITEM.get(
                definition.resultItem());
        if (resultItem == null || resultItem == Items.AIR) {
            fail(player, clientIndex, requestId);
            return;
        }

        ItemStack resultStack = new ItemStack(resultItem, definition.resultCount());
        if (resultItem instanceof WizardBuildingItem) WizardBuildingItem.bindTo(resultStack, player);
        var materials = definition.materials().stream().map(material ->
                new BuildingPurchasePlan.Material(BuiltInRegistries.ITEM.get(material.item()), material.count())).toList();
        var plan = BuildingPurchasePlan.prepare(player.getInventory(), resultStack, materials);
        if (plan == null) {
            fail(player, clientIndex, requestId, BuildingPurchasePlan.hasMaterials(player.getInventory(), materials)
                    ? "livestock.stardewcraft.inventory_full" : "stardewcraft.workbench.need_materials");
            return;
        }
        if (definition.money() > 0 && !PlayerStardewDataAPI.removeMoney(player, definition.money())) {
            fail(player, clientIndex, requestId, "livestock.stardewcraft.money");
            return;
        }
        plan.apply(player.getInventory());
        sendResult(
                player, true,
                PlayerStardewDataAPI.getMoney(player),
                definition.resultItem().toString(),
                clientIndex, requestId);
    }

    private static void fail(
            ServerPlayer player,
            int blueprintIndex, java.util.UUID requestId
    ) {
        fail(player, blueprintIndex, requestId, "building.stardewcraft.work_stale");
    }

    private static void fail(ServerPlayer player, int blueprintIndex, java.util.UUID requestId, String key) {
        com.stardew.craft.network.GlobalHudMessagePayload.sendTo(player, net.minecraft.network.chat.Component.translatable(key));
        sendResult(
                player, false,
                PlayerStardewDataAPI.getMoney(player),
                "", blueprintIndex, requestId);
    }

    private static void sendResult(
            ServerPlayer player,
            boolean success,
            int newMoney,
            String resultItemId,
            int blueprintIndex, java.util.UUID requestId
    ) {
        PacketDistributor.sendToPlayer(player,
                new CarpenterPurchaseResultPayload(
                        success, newMoney, resultItemId,
                        blueprintIndex, requestId));
    }

}
