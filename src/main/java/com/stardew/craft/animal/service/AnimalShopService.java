package com.stardew.craft.animal.service;

import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.animal.model.FarmAnimalDefinition;
import com.stardew.craft.animal.model.FarmAnimalDefinitions;
import com.stardew.craft.api.v1.agriculture.StardewAnimalShopEntries;
import com.stardew.craft.api.v1.agriculture.StardewAnimalShopEntry;
import com.stardew.craft.api.v1.agriculture.StardewAnimalPurchaseDisplay;
import com.stardew.craft.api.v1.agriculture.StardewAnimalPurchaseDisplays;
import com.stardew.craft.api.v1.agriculture.StardewAnimalTypes;
import com.stardew.craft.api.v1.condition.StardewCondition;
import com.stardew.craft.api.v1.condition.StardewConditionContext;
import com.stardew.craft.api.v1.condition.StardewConditions;
import com.stardew.craft.network.payload.OpenAnimalPurchaseScreenPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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
        String displayNameKey,
        String descriptionKey,
        String lockReasonKey,
        @javax.annotation.Nullable StardewCondition unlockCondition,
        @javax.annotation.Nullable net.minecraft.resources.ResourceLocation
                shopTextureId,
        int shopTextureWidth,
        int shopTextureHeight,
        @javax.annotation.Nullable net.minecraft.resources.ResourceLocation
                soundEventId
    ) {
        /**
         * Binary/source compatibility constructor for pre-registry animal addons.
         */
        public ShopAnimalRule(
                String animalTypeId,
                String family,
                int requiredTier,
                int price,
                String defaultName,
                String descriptionKey,
                String lockReasonKey
        ) {
            this(
                    animalTypeId,
                    family,
                    requiredTier,
                    price,
                    defaultName,
                    defaultName,
                    descriptionKey,
                    lockReasonKey,
                    null,
                    null,
                    32,
                    16,
                    null
            );
        }
    }

    /**
     * Legacy addon injection surfaces. New integrations should use the public
     * animal type, shop entry, and purchase display registries.
     */
    private static final Map<String, ShopAnimalRule> SHOP_RULES = Map.of();
    private static final List<String> SHOP_ORDER = List.of();

    private AnimalShopService() {
    }

    public static void openForPlayer(ServerPlayer player) {
        AnimalWorldData data = AnimalWorldData.get(player.serverLevel());
        String ownerUuid = player.getUUID().toString();

        List<OpenAnimalPurchaseScreenPayload.AnimalOption> animals = new ArrayList<>();
        for (String animalType : orderedAnimalTypeIds()) {
            ShopAnimalRule rule = getRule(animalType);
            if (rule == null) {
                continue;
            }
            String display = Component.translatable(rule.displayNameKey()).getString();
            int ownerTier = getOwnerMaxTier(data, ownerUuid, rule.family());
            boolean unlocked = ownerTier >= rule.requiredTier()
                    && isConditionUnlocked(rule, player);
            animals.add(new OpenAnimalPurchaseScreenPayload.AnimalOption(
                animalType,
                display,
                rule.family(),
                rule.requiredTier(),
                rule.price(),
                unlocked,
                rule.descriptionKey(),
                rule.lockReasonKey(),
                rule.shopTextureId() == null
                        ? ""
                        : rule.shopTextureId().toString(),
                rule.shopTextureWidth(),
                rule.shopTextureHeight(),
                rule.soundEventId() == null
                        ? ""
                        : rule.soundEventId().toString()
            ));
        }

        List<OpenAnimalPurchaseScreenPayload.BuildingOption> buildings = new ArrayList<>();
        for (var building : data.getBuildings()) {
            if (!building.isGameplayEnabled()) {
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
        String normalized = animalTypeId.toLowerCase(Locale.ROOT);
        FarmAnimalDefinition builtIn = FarmAnimalDefinitions.find(normalized);
        if (builtIn != null && builtIn.soldByAnimalShop()) {
            return new ShopAnimalRule(
                    builtIn.id(),
                    builtIn.family(),
                    builtIn.requiredBuildingTier(),
                    builtIn.purchasePrice(),
                    builtIn.defaultName(),
                    builtIn.displayNameKey(),
                    builtIn.shopDescriptionKey(),
                    builtIn.shopLockReasonKey(),
                    builtIn.unlockCondition(),
                    builtIn.shopTextureId(),
                    builtIn.shopTextureWidth(),
                    builtIn.shopTextureHeight(),
                    builtIn.soundEventId()
            );
        }
        StardewAnimalShopEntry addon = StardewAnimalShopEntries.entry(normalized);
        var animalType = StardewAnimalTypes.definition(normalized);
        if (addon != null
                && animalType != null
                && animalType.family().equals(addon.family())) {
            StardewAnimalPurchaseDisplay display =
                    StardewAnimalPurchaseDisplays.display(normalized);
            return new ShopAnimalRule(
                    addon.animalTypeId(),
                    addon.family(),
                    addon.requiredTier(),
                    addon.price(),
                    addon.defaultName(),
                    addon.displayNameKey(),
                    addon.descriptionKey(),
                    addon.lockReasonKey(),
                    null,
                    display == null ? null : display.texture(),
                    display == null ? 32 : display.textureWidth(),
                    display == null ? 16 : display.textureHeight(),
                    null
            );
        }
        return SHOP_RULES.get(normalized);
    }

    private static List<String> orderedAnimalTypeIds() {
        LinkedHashSet<String> ordered =
                new LinkedHashSet<>(FarmAnimalDefinitions.animalShopOrder());
        StardewAnimalShopEntries.entries().stream()
                .map(StardewAnimalShopEntry::animalTypeId)
                .forEach(ordered::add);
        ordered.addAll(SHOP_ORDER);
        return List.copyOf(ordered);
    }

    public static int getPurchasePrice(String animalTypeId) {
        ShopAnimalRule rule = getRule(animalTypeId);
        return rule == null ? -1 : rule.price();
    }

    public static boolean isConditionUnlocked(ShopAnimalRule rule, ServerPlayer player) {
        if (rule == null || rule.unlockCondition() == null) {
            return true;
        }
        return StardewConditions.test(
                rule.unlockCondition(),
                StardewConditionContext.forPlayer(player)
        ).result().orElse(false);
    }

    /**
     * Resolves Stardew's ordered {@code AlternatePurchaseTypes} at the purchase boundary.
     * Randomness and player conditions are evaluated on the server; the saved record receives
     * the selected concrete animal type.
     */
    public static String selectPurchasedAnimalType(
            String shopAnimalTypeId,
            ServerPlayer player
    ) {
        FarmAnimalDefinition definition = FarmAnimalDefinitions.find(shopAnimalTypeId);
        if (definition == null || definition.alternatePurchaseTypes().isEmpty()) {
            return shopAnimalTypeId;
        }
        StardewConditionContext context = StardewConditionContext.forPlayer(player);
        for (FarmAnimalDefinition.AlternatePurchaseType alternate
                : definition.alternatePurchaseTypes()) {
            if (alternate.condition() != null
                    && !StardewConditions.test(alternate.condition(), context)
                    .result().orElse(false)) {
                continue;
            }
            if (player.getRandom().nextDouble() >= alternate.chance()) {
                continue;
            }
            return alternate.animalTypeIds().get(
                    player.getRandom().nextInt(alternate.animalTypeIds().size()));
        }
        return shopAnimalTypeId;
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
            if (!building.isGameplayEnabled()) {
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
        if (!building.isGameplayEnabled()) {
            return false;
        }
        if (!rule.family().equalsIgnoreCase(building.buildingType().family())) {
            return false;
        }
        return building.memberAnimalIds().size() < building.capacity();
    }
}
