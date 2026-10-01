package com.stardew.craft.building.runtime;

import com.stardew.craft.api.v1.building.StardewBuildingBuilders;
import com.stardew.craft.building.BuildingBlueprintRegistry;
import com.stardew.craft.building.BuildingCatalogService;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.network.payload.CarpenterPurchaseResultPayload;
import com.stardew.craft.network.payload.OpenBuildingRoutesPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;

public final class BuildingPurchaseService {
    private BuildingPurchaseService() {}
    private static boolean authorized(ServerPlayer player, long revision, net.minecraft.resources.ResourceLocation familyId) {
        var builder=BuildingCatalogService.authorizedBuilder(player,familyId,revision);
        return UtilityBuildings.managed(familyId) && PrefabDefinitions.available(familyId) && builder.isPresent() && BuildingBlueprintRegistry.availableFor(player,builder.get()).stream().anyMatch(blueprint->blueprint.id().equals(familyId));
    }
    public static void openChoices(ServerPlayer player, long revision, net.minecraft.resources.ResourceLocation familyId) {
        openChoices(player, revision, familyId, new UUID(0, 0));
    }
    public static void purchaseUpgrade(ServerPlayer player, com.stardew.craft.api.v1.building.StardewBuildingBlueprint offer, BuildingUpgradePermitItem item) {
        purchaseUpgrade(player, offer, item, new UUID(0, 0));
    }
    public static void openChoices(ServerPlayer player, long revision, net.minecraft.resources.ResourceLocation familyId, UUID requestId) {
        if (!authorized(player, revision, familyId)) { BuildingPlacementService.message(player, "work_stale"); result(player, requestId, false, ""); return; }
        try{PrefabDefinitions.validateAssets(player.serverLevel(),familyId);}catch(RuntimeException invalid){com.stardew.craft.StardewCraft.LOGGER.error("Unavailable building assets {}",familyId,invalid);fail(player, requestId,"gui.stardewcraft.farm_ui.prefab_unavailable");return;}
        if (FishPondPrefabs.isPond(familyId)) { purchase(player, revision, false, requestId, familyId); return; }
        var family = PrefabDefinitions.get(familyId);
        var blueprint = BuildingBlueprintRegistry.find(familyId).orElseThrow().definition();
        var farm = FarmInstanceRegistry.get(player.serverLevel().getServer()).getFarmForPlayer(player.getUUID());
        boolean robinBusy = farm != null && BuildingWorldData.get(player.serverLevel().getServer())
                .hasActiveConstruction(farm.getInstanceId());
        var materials = net.minecraft.network.chat.Component.empty();
        for (var material : blueprint.materials()) {
            if (!materials.getString().isEmpty()) materials.append("\n");
            materials.append(material.count() + " × ").append(BuiltInRegistries.ITEM.get(material.item()).getDescription());
        }
        PacketDistributor.sendToPlayer(player, new OpenBuildingRoutesPayload(PlayerStardewDataAPI.getMoney(player),
                family.managerPrice(), family.selfRadius() * 2 + 1, family.selfHeight(), blueprint.money(), materials,
                revision, familyId, requestId, robinBusy));
    }
    public static void purchase(ServerPlayer player, long revision, boolean self, UUID requestId, net.minecraft.resources.ResourceLocation familyId) {
        if (self && FishPondPrefabs.isPond(familyId)) { fail(player,requestId,"building.stardewcraft.prefab_only"); return; }
        var data = BuildingWorldData.get(player.serverLevel().getServer());
        if (!authorized(player, revision, familyId) || data.hasPurchase(requestId)) { fail(player, requestId, "building.stardewcraft.work_stale"); return; }
        if(!self)try{PrefabDefinitions.validateAssets(player.serverLevel(),familyId);}catch(RuntimeException invalid){fail(player, requestId,"gui.stardewcraft.farm_ui.prefab_unavailable");return;}
        var farm = FarmInstanceRegistry.get(player.serverLevel().getServer()).getFarmForPlayer(player.getUUID());
        if (farm == null) { BuildingPlacementService.message(player, "farm"); result(player, requestId, false, ""); return; }
        if (!self && data.hasActiveConstruction(farm.getInstanceId())) {
            fail(player, requestId, "building.stardewcraft.robin_busy");
            return;
        }
        var definition = BuildingBlueprintRegistry.find(familyId).orElseThrow().definition();
        int price = self ? PrefabDefinitions.get(familyId).managerPrice() : definition.money();
        if (PlayerStardewDataAPI.getMoney(player) < price) { fail(player, requestId, "livestock.stardewcraft.money"); return; }
        ItemStack stack = new ItemStack(self ? PrefabDefinitions.managerItem(familyId) : PrefabDefinitions.blueprintItem(familyId));
        if (!self) BuildingBlueprintItem.bind(stack, requestId);
        var materials = self ? java.util.List.<BuildingPurchasePlan.Material>of() : definition.materials().stream()
                .map(material -> new BuildingPurchasePlan.Material(BuiltInRegistries.ITEM.get(material.item()), material.count())).toList();
        var plan = BuildingPurchasePlan.prepare(player.getInventory(), stack, materials);
        if (plan == null) { fail(player, requestId, BuildingPurchasePlan.hasMaterials(player.getInventory(), materials) ? "livestock.stardewcraft.inventory_full" : "stardewcraft.workbench.need_materials"); return; }
        if (price > 0 && !PlayerStardewDataAPI.removeMoney(player, price)) { fail(player, requestId, "livestock.stardewcraft.money"); return; }
        plan.apply(player.getInventory());
        data.recordPurchase(requestId, farm.getInstanceId(), !self, familyId);
        result(player, requestId, true, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        BuildingPlacementService.message(player, self ? "manager_hint" : "blueprint_hint");
    }
    public static void purchaseUpgrade(ServerPlayer player, com.stardew.craft.api.v1.building.StardewBuildingBlueprint offer, BuildingUpgradePermitItem item, UUID requestId) {
        var farm = FarmInstanceRegistry.get(player.serverLevel().getServer()).getFarmForPlayer(player.getUUID());
        var data = BuildingWorldData.get(player.serverLevel().getServer());
        if (farm == null || !item.availableFor(player)) { fail(player, requestId, "building.stardewcraft.work_stale"); return; }
        if (data.hasActiveConstruction(farm.getInstanceId())) {
            fail(player, requestId, "building.stardewcraft.robin_busy");
            return;
        }
        if (data.hasUpgradePermit(farm.getInstanceId(), item.family(), item.targetTier())) {
            BuildingPlacementService.message(player, "upgrade_owned"); result(player, requestId, false, ""); return;
        }
        var definition = offer.definition();
        UUID id = UUID.randomUUID(); ItemStack stack = new ItemStack(item); BuildingBlueprintItem.bind(stack, id);
        var materials = definition.materials().stream()
                .map(material -> new BuildingPurchasePlan.Material(BuiltInRegistries.ITEM.get(material.item()), material.count())).toList();
        var plan = BuildingPurchasePlan.prepare(player.getInventory(), stack, materials);
        if (plan == null) { fail(player, requestId, BuildingPurchasePlan.hasMaterials(player.getInventory(), materials) ? "livestock.stardewcraft.inventory_full" : "stardewcraft.workbench.need_materials"); return; }
        if (PlayerStardewDataAPI.getMoney(player) < definition.money()
                || definition.money() > 0 && !PlayerStardewDataAPI.removeMoney(player, definition.money())) {
            fail(player, requestId, "livestock.stardewcraft.money"); return;
        }
        plan.apply(player.getInventory());
        data.recordUpgradePurchase(id, farm.getInstanceId(), item.family(), item.targetTier());
        result(player, requestId, true, BuiltInRegistries.ITEM.getKey(item).toString());
        BuildingPlacementService.message(player, "upgrade_hint");
    }
    private static void fail(ServerPlayer player, UUID requestId, String key) {
        com.stardew.craft.network.GlobalHudMessagePayload.sendTo(player, net.minecraft.network.chat.Component.translatable(key));
        result(player, requestId, false, "");
    }
    private static void result(ServerPlayer player, UUID requestId, boolean success, String item) {
        PacketDistributor.sendToPlayer(player, new CarpenterPurchaseResultPayload(success, PlayerStardewDataAPI.getMoney(player), item, 0, requestId));
    }
}
