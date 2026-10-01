package com.stardew.craft.building.runtime;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.item.StardewBlockItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;

public final class BuildingManagerItem extends StardewBlockItem {
    private final net.minecraft.resources.ResourceLocation family;
    public net.minecraft.resources.ResourceLocation family() { return family; }
    public BuildingManagerItem(net.minecraft.resources.ResourceLocation family, Properties properties) {
        super(PrefabDefinitions.managerBlock(family), "stardewcraft.type.utility", -1, properties); this.family = family;
    }
    @Override public InteractionResult place(BlockPlaceContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) return super.place(context);
        if (com.stardew.craft.greenhouse.GreenhouseBuildings.isGreenhouse(family)) {
            BuildingPlacementService.message(player,"greenhouse_not_built");
            return InteractionResult.FAIL;
        }
        if (FishPondPrefabs.isPond(family)) { BuildingPlacementService.message(player,"prefab_only"); return InteractionResult.FAIL; }
        var pos = context.getClickedPos();
        var facing = context.getHorizontalDirection().getOpposite();
        var probe = BuildingPlacementService.probe(player.serverLevel(), player, pos, facing, true, family);
        if (!probe.valid()) { BuildingPlacementService.message(player, probe.issue()); return InteractionResult.FAIL; }
        var identity = context.getItemInHand().getOrDefault(com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_DATA, com.stardew.craft.port.net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        if (identity.hasUUID("ResidenceIdentity")) {
            var data = BuildingWorldData.get(player.server); var old = data.find(identity.getUUID("ResidenceIdentity"));
            if (old == null || old.phase() != BuildingRecord.Phase.MISSING || !old.family().equals(family) || !old.farmId().equals(probe.farm().getInstanceId()) || !BuildingService.canManage(player, old)) {
                BuildingPlacementService.message(player, "work_stale"); return InteractionResult.FAIL;
            }
            if (data.restoreSelf(old,pos,facing,probe.claim()) != BuildingWorldData.Result.SUCCESS) return InteractionResult.FAIL;
            var result = super.place(context);
            if (!result.consumesAction()) data.detachSelf(old.id()); else BuildingManagerInteraction.open(player,pos);
            return result;
        }
        var admission = BuildingService.register(player, family, BuildingRecord.Mode.SELF_BUILT,
                pos, pos, facing, probe.claim());
        if (admission.failure() != BuildingService.Failure.NONE) return InteractionResult.FAIL;
        var data = BuildingWorldData.get(player.serverLevel().getServer());
        InteractionResult result = super.place(context);
        if (!result.consumesAction()) data.removeSelfBuilt(admission.buildingId());
        else {
            UtilityBuildings.refresh(player.serverLevel(), data.find(admission.buildingId()));
            BuildingManagerInteraction.open(player, pos);
        }
        return result;
    }
}
