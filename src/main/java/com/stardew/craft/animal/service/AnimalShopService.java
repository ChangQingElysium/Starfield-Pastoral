package com.stardew.craft.animal.service;

import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.network.payload.OpenAnimalPurchaseScreenPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("null")
public final class AnimalShopService {
    public record ShopAnimalRule(
        String animalTypeId,
        String family,
        int requiredTier,
        int price,
        String defaultName,
        String descriptionKey,
        String lockReasonKey
    ) {
    }

    private static final Map<String, ShopAnimalRule> SHOP_RULES = Map.of(
        "white_chicken", new ShopAnimalRule("white_chicken", "coop", 1, 800, "Chicken", "stardewcraft.animal.shop.desc.white_chicken", "stardewcraft.animal.shop.lock.coop_t1"),
        "duck", new ShopAnimalRule("duck", "coop", 2, 1200, "Duck", "stardewcraft.animal.shop.desc.duck", "stardewcraft.animal.shop.lock.coop_t2"),
        "rabbit", new ShopAnimalRule("rabbit", "coop", 3, 8000, "Rabbit", "stardewcraft.animal.shop.desc.rabbit", "stardewcraft.animal.shop.lock.coop_t3"),
        "cow", new ShopAnimalRule("cow", "barn", 1, 1500, "Cow", "stardewcraft.animal.shop.desc.cow", "stardewcraft.animal.shop.lock.barn_t1"),
        "goat", new ShopAnimalRule("goat", "barn", 2, 4000, "Goat", "stardewcraft.animal.shop.desc.goat", "stardewcraft.animal.shop.lock.barn_t2"),
        "sheep", new ShopAnimalRule("sheep", "barn", 3, 8000, "Sheep", "stardewcraft.animal.shop.desc.sheep", "stardewcraft.animal.shop.lock.barn_t3"),
        "pig", new ShopAnimalRule("pig", "barn", 3, 16000, "Pig", "stardewcraft.animal.shop.desc.pig", "stardewcraft.animal.shop.lock.barn_t3")
    );

    private static final List<String> SHOP_ORDER = List.of(
        "white_chicken",
        "duck",
        "rabbit",
        "cow",
        "goat",
        "sheep",
        "pig"
    );

    private AnimalShopService() {
    }

    public static void openForPlayer(ServerPlayer player) {
        AnimalWorldData data = AnimalWorldData.get(player.serverLevel());
        String ownerUuid = player.getUUID().toString();

        List<OpenAnimalPurchaseScreenPayload.AnimalOption> animals = new ArrayList<>();
        for (String animalType : SHOP_ORDER) {
            ShopAnimalRule rule = getRule(animalType);
            if (rule == null) {
                continue;
            }
            String display = Component.translatable("entity.stardewcraft." + animalType).getString();
            int ownerTier = getOwnerMaxTier(data, ownerUuid, rule.family());
            boolean unlocked = ownerTier >= rule.requiredTier();
            animals.add(new OpenAnimalPurchaseScreenPayload.AnimalOption(
                animalType,
                display,
                rule.family(),
                rule.requiredTier(),
                rule.price(),
                unlocked,
                rule.descriptionKey(),
                rule.lockReasonKey()
            ));
        }

        List<OpenAnimalPurchaseScreenPayload.BuildingOption> buildings = new ArrayList<>();
        for (var building : data.getBuildings()) {
            if (!building.active()) {
                continue;
            }
            if (!com.stardew.craft.farm.FarmInstanceRegistry.get()
                    .canOperateBuilding(player.getUUID(), building.ownerPlayerUuid())) {
                continue;
            }
            String displayName = (building.customName() == null || building.customName().isBlank())
                ? building.buildingId()
                : building.customName();
            buildings.add(new OpenAnimalPurchaseScreenPayload.BuildingOption(
                building.buildingId(),
                displayName,
                building.buildingType().family(),
                building.buildingType().tier(),
                building.memberAnimalIds().size(),
                building.capacity()
            ));
        }

        @SuppressWarnings("null")
        OpenAnimalPurchaseScreenPayload payload = OpenAnimalPurchaseScreenPayload.normal(PlayerStardewDataAPI.getMoney(player), animals, buildings);
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static ShopAnimalRule getRule(String animalTypeId) {
        if (animalTypeId == null) {
            return null;
        }
        return SHOP_RULES.get(animalTypeId.toLowerCase(Locale.ROOT));
    }

    public static int getPurchasePrice(String animalTypeId) {
        ShopAnimalRule rule = getRule(animalTypeId);
        return rule == null ? -1 : rule.price();
    }

    public static int getOwnerMaxTier(AnimalWorldData data, String ownerUuid, String family) {
        int maxTier = 0;
        for (AnimalBuildingRecord building : data.getBuildings()) {
            if (!com.stardew.craft.farm.FarmInstanceRegistry.get()
                    .canOperateBuilding(
                        java.util.UUID.fromString(ownerUuid),
                        building.ownerPlayerUuid())) {
                continue;
            }
            if (!building.active()) {
                continue;
            }
            if (!family.equalsIgnoreCase(building.buildingType().family())) {
                continue;
            }
            maxTier = Math.max(maxTier, building.buildingType().tier());
        }
        return maxTier;
    }

    public static boolean canPurchaseInBuilding(ShopAnimalRule rule, AnimalBuildingRecord building) {
        if (rule == null || building == null) {
            return false;
        }
        if (!building.active()) {
            return false;
        }
        if (!rule.family().equalsIgnoreCase(building.buildingType().family())) {
            return false;
        }
        if (building.buildingType().tier() < rule.requiredTier()) {
            return false;
        }
        return building.memberAnimalIds().size() < building.capacity();
    }
}
