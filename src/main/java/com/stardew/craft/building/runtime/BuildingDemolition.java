package com.stardew.craft.building.runtime;

import com.stardew.craft.animal.runtime.*;
import com.stardew.craft.floor.SurfaceFloorData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import java.util.List;

public final class BuildingDemolition {
    private BuildingDemolition() {}
    public static boolean perform(ServerPlayer player, BuildingRecord record) {
        if (com.stardew.craft.greenhouse.GreenhouseBuildings.isGreenhouse(record.family())) {
            BuildingPlacementService.message(player, "greenhouse_move_only");
            return false;
        }
        var level=player.serverLevel(); var data=BuildingWorldData.get(player.server); var animals=LivestockWorldData.get(player.server);
        if(data.moveLift(record.id())!=null){BuildingPlacementService.message(player,"work_stale");return false;}
        if (FishPondPrefabs.isPond(record.family()) && com.stardew.craft.fishpond.data.FishPondWorldData.get(level)
                .findPondByManagerAnyOwner(level.dimension().location().toString(),record.manager())
                .map(pond -> pond.currentPopulation()>0 || pond.outputCount()>0).orElse(false)) {
            BuildingPlacementService.message(player,"demolish_contents"); return false;
        }
        if(animals.occupancy(record.id())>0 || animals.eggs().stream().anyMatch(e->e.home().equals(record.id()))) { BuildingPlacementService.message(player,"demolish_animals"); return false; }
        for (var pos : BlockPos.betweenClosed(BuildingTransfer.contentBounds(record).min(),BuildingTransfer.contentBounds(record).maxInclusive())) {
            if (level.getBlockEntity(pos) instanceof com.stardew.craft.blockentity.IncubatorBlockEntity incubator && incubator.hasInput()) {
                BuildingPlacementService.message(player,"demolish_contents"); return false;
            }
        }
        if(record.mode()==BuildingRecord.Mode.SELF_BUILT) {
            var delivery=BuildingPurchasePlan.prepare(player.getInventory(),new ItemStack(PrefabDefinitions.managerItem(record.family())),List.of());
            if(delivery==null) { BuildingPlacementService.message(player,"purchase_space"); return false; }
            BuildingRemovalJournal.get(player.server).prepare(player,record,List.of(record.manager()));
        } else {
            var nativeCells=BuildingTransfer.nativeCells(level,record,record.tier()); var floors=SurfaceFloorData.get(level);
            for(var pos:nativeCells.keySet()) {
                var be=level.getBlockEntity(pos);
                if(be != null && BuildingRemovalChecks.containsItems(be.saveWithFullMetadata())) { BuildingPlacementService.message(player,"demolish_contents"); return false; }
                if(floors.at(pos)!=null) { BuildingPlacementService.message(player,"demolish_attachments"); return false; }
            }
            for(var entity:level.getEntitiesOfClass(HangingEntity.class,BuildingPlacementService.aabb(record.claim()).inflate(1))) {
                if(nativeCells.containsKey(entity.getPos().relative(entity.getDirection().getOpposite()))) { BuildingPlacementService.message(player,"demolish_attachments"); return false; }
            }
            if(BuildingRemovalChecks.unsupportedAddition(level,nativeCells.keySet())!=null){BuildingPlacementService.message(player,"demolish_attachments");return false;}
            BuildingRemovalJournal.get(player.server).prepare(player,record,nativeCells.keySet());
        }
        BuildingRemovalJournal.get(player.server).recover(player.server);
        BuildingPlacementService.message(player,"demolished");
        return true;
    }
}
