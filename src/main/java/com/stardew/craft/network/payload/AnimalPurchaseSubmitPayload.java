package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.service.AnimalShopService;
import com.stardew.craft.animal.service.AnimalAcquireService;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;

public record AnimalPurchaseSubmitPayload(String animalTypeId, String buildingId, String customName) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<AnimalPurchaseSubmitPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "animal_purchase_submit"));

    @SuppressWarnings("null")
    public static final StreamCodec<FriendlyByteBuf, AnimalPurchaseSubmitPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.animalTypeId, 64);
            buf.writeUtf(payload.buildingId, 128);
            buf.writeUtf(payload.customName, 128);
        },
        buf -> new AnimalPurchaseSubmitPayload(buf.readUtf(64), buf.readUtf(128), buf.readUtf(128))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @SuppressWarnings("null")
    public static void handle(AnimalPurchaseSubmitPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                return;
            }

            AnimalShopService.ShopAnimalRule rule = AnimalShopService.getRule(payload.animalTypeId);
            int price = rule == null ? -1 : rule.price();
            if (price <= 0) {
                fail(serverPlayer, "stardewcraft.animal.purchase.failed");
                return;
            }
            if (PlayerStardewDataAPI.getMoney(serverPlayer) < price) {
                fail(serverPlayer, "stardewcraft.animal.purchase.no_money");
                return;
            }

            String normalizedName = payload.customName == null ? "" : payload.customName.trim();
            AnimalWorldData data = AnimalWorldData.get(serverPlayer.serverLevel());
            var buildingOpt = data.getBuilding(payload.buildingId);
            if (buildingOpt.isEmpty()) {
                fail(serverPlayer, "stardewcraft.animal.purchase.failed");
                return;
            }

            String ownerUuid = serverPlayer.getUUID().toString();
            if (!com.stardew.craft.farm.FarmInstanceRegistry.get()
                    .canOperateBuilding(serverPlayer.getUUID(), buildingOpt.get().ownerPlayerUuid())) {
                fail(serverPlayer, "stardewcraft.animal.purchase.failed");
                return;
            }

            int ownerTier = AnimalShopService.getOwnerMaxTier(data, ownerUuid, rule.family());
            if (ownerTier < rule.requiredTier()) {
                fail(serverPlayer, rule.lockReasonKey());
                return;
            }

            if (!AnimalShopService.canPurchaseInBuilding(rule, buildingOpt.get())) {
                fail(serverPlayer, "stardewcraft.animal.purchase.no_building");
                return;
            }

            String finalName = normalizedName.isBlank() ? rule.defaultName() : normalizedName;
            if (data.hasAnyAnimalWithName(finalName)) {
                fail(serverPlayer, "stardewcraft.animal.purchase.name_duplicate");
                return;
            }

            if (!PlayerStardewDataAPI.removeMoney(serverPlayer, price)) {
                fail(serverPlayer, "stardewcraft.animal.purchase.no_money");
                return;
            }
            try {
                var record = AnimalAcquireService.purchase(serverPlayer.serverLevel(), payload.animalTypeId, finalName, payload.buildingId);
                PacketDistributor.sendToPlayer(serverPlayer,
                    AnimalPurchaseResultPayload.success(record.customName()));
            } catch (IllegalArgumentException | IllegalStateException ex) {
                PlayerStardewDataAPI.addMoney(serverPlayer, price);
                fail(serverPlayer, "stardewcraft.animal.purchase.failed");
            }
        });
    }

    private static void fail(ServerPlayer player, String messageKey) {
        PacketDistributor.sendToPlayer(player, AnimalPurchaseResultPayload.failure(messageKey));
    }
}
