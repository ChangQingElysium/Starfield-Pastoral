package com.stardew.craft.building.runtime;

import com.stardew.craft.network.GlobalHudMessagePayload;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class BuildingUpgradeService {
    private BuildingUpgradeService() {}
    public static boolean use(ServerPlayer player, BlockPos pos, ItemStack stack, BuildingUpgradePermitItem item) {
        var level = player.serverLevel(); var data = BuildingWorldData.get(level.getServer());
        var id = data.occupying(level.dimension().location(), pos);
        var record = id == null ? null : data.find(id);
        if (record == null || !PrefabDefinitions.available(record) || !item.availableFor(player) || !record.manager().equals(pos) || record.mode() != BuildingRecord.Mode.PREFAB
                || !record.family().equals(item.family()) || record.tier() + 1 != item.targetTier()
                || record.phase() != BuildingRecord.Phase.READY || data.transfer(id) != null) {
            BuildingPlacementService.message(player, "upgrade_target"); return false;
        }
        if (!BuildingService.canManage(player, record)) { BuildingPlacementService.message(player, "permission"); return false; }
        var permit = BuildingBlueprintItem.permit(stack);
        if (!data.permitsUpgrade(permit, record.farmId(), record.family(), item.targetTier())) {
            BuildingPlacementService.message(player, "permit"); return false;
        }
        if (!level.hasChunksAt(record.claim().min(), record.claim().maxInclusive())) {
            BuildingPlacementService.message(player, "unloaded"); return false;
        }
        var current = PrefabDefinitions.transform(PrefabDefinitions.get(record.family()).tier(record.tier()).bounds(),
                record.anchor(), PrefabDefinitions.rotation(record.facing()));
        var nativeCells = BuildingTransfer.nativeCells(level, record, record.tier());
        var retainedGround = PrefabDefinitions.retainedGround(level, record);
        var obstacles=new java.util.LinkedHashMap<net.minecraft.resources.ResourceLocation,net.minecraft.nbt.CompoundTag>();
        for (BlockPos cell : BlockPos.betweenClosed(current.min(), current.maxInclusive())) {
            if (retainedGround.contains(cell) && BuildingTransfer.isGround(level, cell)) continue;
            if (!nativeCells.containsKey(cell) && !level.getBlockState(cell).isAir()) addObstacle(obstacles,level,cell);
        }
        if(!obstacles.isEmpty()){showObstacles(player,record,obstacles);return false;}
        if (BuildingLifecycleService.noticePosition(level, record) == null || BuildingLifecycleService.indoorWorkPosition(level, record) == null) {
            BuildingPlacementService.message(player, "asset_error"); return false;
        }
        try { BuildingTransfer.upgrade(level, record); }
        catch (BuildingTransfer.Collision collision) {
            addObstacle(obstacles,level,collision.pos);showObstacles(player,record,obstacles);return false;
        }
        if (data.beginPermittedUpgrade(id, record.revision(), permit, item.targetTier(),
                StardewTimeManager.get().getAbsoluteDay()) != BuildingWorldData.Result.SUCCESS) return false;
        stack.shrink(1); player.getInventory().setChanged();
        BuildingLifecycleService.upgradeScaffold(level, data.find(id));
        BuildingPlacementService.message(player, "started");
        return true;
    }
    private static void addObstacle(java.util.Map<net.minecraft.resources.ResourceLocation,net.minecraft.nbt.CompoundTag> rows,net.minecraft.server.level.ServerLevel level,BlockPos pos){
        var block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        var row=rows.computeIfAbsent(block,id->{var t=new net.minecraft.nbt.CompoundTag();t.putString("Block",id.toString());t.putInt("X",pos.getX());t.putInt("Y",pos.getY());t.putInt("Z",pos.getZ());return t;});
        row.putInt("Count",row.getInt("Count")+1);
    }
    private static void showObstacles(ServerPlayer player,BuildingRecord record,java.util.Map<net.minecraft.resources.ResourceLocation,net.minecraft.nbt.CompoundTag> obstacles){
        var tag=new net.minecraft.nbt.CompoundTag();var rows=new net.minecraft.nbt.ListTag();rows.addAll(obstacles.values());tag.put("Obstacles",rows);
        var building=record.save();var bounds=PrefabDefinitions.transform(PrefabDefinitions.get(record.family()).tier(record.tier()+1).bounds(),record.anchor(),PrefabDefinitions.rotation(record.facing()));
        building.putLong("TargetMin",bounds.min().asLong());building.putLong("TargetMax",bounds.maxExclusive().asLong());tag.put("Building",building);
        net.minecraftforge.network.PacketDistributor.sendToPlayer(player,new com.stardew.craft.network.payload.BuildingWorkPayload(tag));
    }

}
