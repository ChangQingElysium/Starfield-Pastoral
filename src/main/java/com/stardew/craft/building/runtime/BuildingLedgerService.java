package com.stardew.craft.building.runtime;

import com.stardew.craft.animal.runtime.*;
import com.stardew.craft.network.payload.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

/** One manager session authorizes a concrete building revision, never client-supplied facility counts. */
public final class BuildingLedgerService {
    private record Session(UUID token, UUID building, long revision, long expires) {}
    private static final Map<UUID, Session> sessions = new HashMap<>();
    private BuildingLedgerService() {}
    public static void clear() { sessions.clear(); }
    public static void open(ServerPlayer player, BuildingRecord record) { open(player, record, null); }
    private static void open(ServerPlayer player, BuildingRecord record, UUID reply) {
        var data = BuildingWorldData.get(player.server); var level = player.serverLevel();
        BuildingResidence.Assessment scan = null;
        boolean silo = UtilityBuildings.supported(record.family());
        boolean greenhouse = com.stardew.craft.greenhouse.GreenhouseBuildings.isGreenhouse(record.family());
        if (silo) { UtilityBuildings.refresh(level,record); record=data.find(record.id()); }
        else if (record.mode() == BuildingRecord.Mode.SELF_BUILT) { scan = BuildingResidence.refresh(level, record); record = data.find(record.id()); }
        var session = new Session(UUID.randomUUID(), record.id(), record.revision(), player.server.getTickCount() + 6000L); sessions.put(player.getUUID(), session);
        var tag = record.save(); if(reply!=null)tag.putUUID("ReplySession",reply); tag.putUUID("Session",session.token); tag.putString("Title",record.title().getString());
        var order = data.order(record.id()); tag.putInt("Days",order == null ? 0 : order.remainingDays());
        if (scan != null) {
            tag.putBoolean("Loaded",scan.loaded()); tag.putInt("Eligible",scan.eligibleTier());
            tag.putIntArray("Counts",new int[]{scan.troughs()+scan.automaticTroughs(),scan.automaticTroughs(),scan.hoppers(),scan.incubators()});
            int target = record.phase() == BuildingRecord.Phase.WAITING ? 1 : Math.min(PrefabDefinitions.maxTier(record.family()),record.tier()+1);
            var needs = PrefabDefinitions.facilities(record.family(),target); tag.putInt("Target",target);
            tag.putIntArray("Needs",new int[]{needs.troughs(),needs.automaticTroughs(),needs.hoppers(),needs.incubators()});
            var facilities=new ListTag();
            var items=new net.minecraft.world.level.block.Block[]{com.stardew.craft.block.ModBlocks.FEED_TROUGH.get(),com.stardew.craft.block.ModBlocks.AUTOFEED_TROUGH.get(),com.stardew.craft.block.ModBlocks.HAY_HOPPER.get(),com.stardew.craft.block.ModBlocks.INCUBATOR.get()};
            int[] counts=tag.getIntArray("Counts"),requirements=tag.getIntArray("Needs");
            for(int i=0;i<items.length;i++)if(requirements[i]>0){var row=new CompoundTag();row.putString("Item",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(items[i].asItem()).toString());row.putInt("Have",counts[i]);row.putInt("Need",requirements[i]);facilities.add(row);}
            tag.put("Facilities",facilities);
            tag.putBoolean("CanAccept",scan.loaded() && scan.eligibleTier()>=(record.phase()==BuildingRecord.Phase.WAITING?1:record.tier()+1) && (record.phase()==BuildingRecord.Phase.WAITING || record.tier()<PrefabDefinitions.maxTier(record.family())));

        }
        if (silo) {
            tag.putInt("Hay",FarmFeed.amount(player.server,record.farmId()));
            tag.putInt("HayCapacity",FarmFeed.capacity(player.server,record.farmId()));
            if(record.mode()==BuildingRecord.Mode.SELF_BUILT) {
                var column=UtilityBuildings.scanSilo(level,record.manager(),record.claim());
                tag.putBoolean("Loaded",column.loaded());tag.putInt("Bricks",column.bricks());
                tag.putBoolean("CanAccept",record.phase()==BuildingRecord.Phase.WAITING && column.valid());
            }
        }
        if (FishPondPrefabs.isPond(record.family())) {
            var pond = com.stardew.craft.fishpond.data.FishPondWorldData.get(level)
                    .findPondByManagerAnyOwner(level.dimension().location().toString(),record.manager()).orElse(null);
            if(pond!=null){tag.putString("PondFish",pond.fishTypeId());tag.putInt("PondPopulation",pond.currentPopulation());tag.putInt("PondCapacity",pond.maxPopulation());}
        }
        var livestock = LivestockWorldData.get(player.server); var rows = new ListTag();
        for (var animal : livestock.all()) if (animal.home().equals(record.id())) {
            var row = new CompoundTag(); row.putUUID("Id",animal.id()); row.putString("Name",animal.name()); row.putString("Species",animal.species().id());
            LivestockUiData.care(row,animal); row.putInt("Age",animal.care().age()); row.putInt("Friendship",animal.care().friendship()); row.putInt("Mood",animal.care().happiness());
            row.putBoolean("Petted",animal.care().petted() || animal.care().autoPetted()); row.putInt("Fullness",animal.care().fullness()); rows.add(row);
        }
        tag.put("Animals",rows); tag.putInt("Capacity",record.phase()==BuildingRecord.Phase.WAITING || record.phase()==BuildingRecord.Phase.CONSTRUCTING ? 0 : LivestockHomes.capacity(level,record));
        tag.putBoolean("GreenhouseOnly", greenhouse);
        tag.putBoolean("CanMove",record.phase()==BuildingRecord.Phase.READY);
        tag.putBoolean("CanDemolish",!greenhouse && (record.phase()==BuildingRecord.Phase.READY || record.phase()==BuildingRecord.Phase.WAITING));
        tag.putBoolean("CanUpgrade",!greenhouse && record.mode()==BuildingRecord.Mode.PREFAB && record.phase()==BuildingRecord.Phase.READY && (record.phase()==BuildingRecord.Phase.WAITING || record.tier()<PrefabDefinitions.maxTier(record.family())));
        tag.putBoolean("Outdoors",livestock.outdoorsAllowed(record.id()));
        PacketDistributor.sendToPlayer(player,new BuildingLedgerPayload(tag));
    }
    private static void close(ServerPlayer player, UUID reply) {
        var current=sessions.get(player.getUUID());
        if(current!=null && current.token.equals(reply))sessions.remove(player.getUUID());
        var tag = new CompoundTag(); tag.putBoolean("Close", true); tag.putUUID("ReplySession",reply);
        PacketDistributor.sendToPlayer(player, new BuildingLedgerPayload(tag));
    }
    public static void action(ServerPlayer player, UUID token, String action, String name) {
        var session=sessions.get(player.getUUID()); if(session==null || !session.token.equals(token) || session.expires<player.server.getTickCount()) { BuildingPlacementService.message(player,"work_stale"); close(player,token); return; }
        var data=BuildingWorldData.get(player.server); var record=data.find(session.building);
        if(record==null || !record.dimension().equals(player.serverLevel().dimension().location()) || player.distanceToSqr(record.manager().getX()+.5,record.manager().getY()+.5,record.manager().getZ()+.5)>64
                || !BuildingService.canManage(player,record) || !player.serverLevel().getBlockState(record.manager()).is(PrefabDefinitions.managerBlock(record.family()))) { BuildingPlacementService.message(player,"permission"); close(player,token); return; }
        if(record.revision()!=session.revision || data.transfer(record.id())!=null || data.moveLift(record.id())!=null || BuildingRemovalJournal.get(player.server).contains(record.id())) { BuildingPlacementService.message(player,"work_stale"); open(player,record,token); return; }
        sessions.remove(player.getUUID());
        boolean ready=record.phase()==BuildingRecord.Phase.READY;
        boolean greenhouse=com.stardew.craft.greenhouse.GreenhouseBuildings.isGreenhouse(record.family());
        if(greenhouse && !action.equals("move") && !action.equals("refresh")) {
            BuildingPlacementService.message(player,"greenhouse_move_only");open(player,record,token);return;
        }
        switch(action) {
            case "rename" -> { if(name.isBlank() || data.rename(record.id(),record.revision(),name)!=BuildingWorldData.Result.SUCCESS) BuildingPlacementService.message(player,"name_invalid"); }
            case "accept" -> {
                if(record.mode()!=BuildingRecord.Mode.SELF_BUILT) { BuildingPlacementService.message(player,"upgrade_hint"); break; }
                if(UtilityBuildings.supported(record.family())) {
                    if(!UtilityBuildings.acceptSilo(player.serverLevel(),record)) BuildingPlacementService.message(player,"requirements_missing");
                    break;
                }
                var scan=BuildingResidence.scan(player.serverLevel(),record.claim(),record.family());
                if(!scan.loaded() || data.acceptSelf(record.id(),record.revision(),scan.eligibleTier())!=BuildingWorldData.Result.SUCCESS) BuildingPlacementService.message(player,"requirements_missing");
            }
            case "grazing" -> { if(!UtilityBuildings.supported(record.family()) && (ready || record.phase()==BuildingRecord.Phase.UPGRADING)) { var animals=LivestockWorldData.get(player.server); animals.outdoorsAllowed(record.id(),!animals.outdoorsAllowed(record.id())); } }
            case "animals" -> {
                if(UtilityBuildings.supported(record.family())) break;
                sessions.put(player.getUUID(),session);
                if(LivestockManagement.openFromLedger(player,name,token)==null) {
                    BuildingPlacementService.message(player,"farm");open(player,record,token);
                }
                return;
            }
            case "refresh" -> {}
            case "preview" -> {
                if(record.mode()==BuildingRecord.Mode.PREFAB && record.tier()<PrefabDefinitions.maxTier(record.family())) {
                    var preview=record.save(); var bounds=PrefabDefinitions.transform(PrefabDefinitions.get(record.family()).tier(record.tier()+1).bounds(),record.anchor(),PrefabDefinitions.rotation(record.facing()));
                    preview.putLong("TargetMin",bounds.min().asLong()); preview.putLong("TargetMax",bounds.maxExclusive().asLong());
                    var tag=new CompoundTag(); tag.put("Preview",preview); tag.putUUID("ReplySession",token); PacketDistributor.sendToPlayer(player,new BuildingWorkPayload(tag)); return;
                }
            }
            case "move" -> {
                if(!ready) break;
                var stack=new ItemStack(PrefabDefinitions.blueprintItem(record.family())); BuildingBlueprintItem.bindMove(stack,record);
                var plan=BuildingPurchasePlan.prepare(player.getInventory(),stack,List.of());
                if(plan==null) BuildingPlacementService.message(player,"purchase_space");
                else if(!BuildingMoveSession.begin(player,stack,record)) BuildingPlacementService.message(player,"work_stale");
                else { plan.apply(player.getInventory()); BuildingPlacementService.message(player,"move_hint"); close(player,token); return; }
            }
            case "demolish" -> { if(ready || record.phase()==BuildingRecord.Phase.WAITING) { if(BuildingDemolition.perform(player,record)) { close(player,token); return; } } }
            default -> { BuildingPlacementService.message(player,"work_stale"); }
        }
        record=data.find(record.id()); if(record!=null) open(player,record,token);
    }
}
