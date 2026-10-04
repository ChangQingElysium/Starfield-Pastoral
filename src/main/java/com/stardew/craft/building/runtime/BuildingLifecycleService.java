package com.stardew.craft.building.runtime;

import com.stardew.craft.api.v1.building.StardewBuildingBuilders;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.building.BuildingCatalogService;
import com.stardew.craft.network.payload.BuildingWorkPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public final class BuildingLifecycleService {
    private BuildingLifecycleService() {}
    public static void request(ServerPlayer player, ResourceLocation family, long catalog, UUID id, long revision, String action) {
        request(player, family, catalog, id, revision, action, null);
    }
    public static void request(ServerPlayer player, ResourceLocation family, long catalog, UUID id, long revision, String action, UUID requestId) {
        if (!PrefabDefinitions.available(family) || BuildingCatalogService.authorizedBuilder(player,family,catalog).isEmpty()) {
            rejectWork(player, requestId, "work_stale"); return;
        }
        var data = BuildingWorldData.get(player.serverLevel().getServer());
        if (action.equals("list")) { sendWork(player, listData(player, family, catalog), requestId); return; }
        var record = data.find(id);
        if (record == null || !PrefabDefinitions.available(record) || !record.family().equals(family) || record.revision() != revision || record.mode() != BuildingRecord.Mode.PREFAB
                || record.phase() != BuildingRecord.Phase.READY || data.transfer(id) != null || !BuildingService.canManage(player, record)) {
            BuildingPlacementService.message(player, "work_stale"); sendWork(player, listData(player, family, catalog), requestId); return;
        }
        ServerLevel level = player.server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, record.dimension()));
        if (level == null) { rejectWork(player, requestId, "unloaded"); return; }
        if (action.equals("move")) {
            ItemStack blueprint = new ItemStack(PrefabDefinitions.blueprintItem(family));
            BuildingBlueprintItem.bindMove(blueprint, record);
            var plan=BuildingPurchasePlan.prepare(player.getInventory(),blueprint,java.util.List.of());
            if(plan==null){BuildingPlacementService.message(player,"purchase_space");return;}
            if(!BuildingMoveSession.begin(player,blueprint,record)){BuildingPlacementService.message(player,"work_stale");return;}
            plan.apply(player.getInventory());
            BuildingPlacementService.message(player, "move_hint");sendWork(player,listData(player,family,catalog),requestId);return;
        }
        if (record.tier() >= PrefabDefinitions.maxTier(record.family())) {
            BuildingPlacementService.message(player, "work_stale"); sendWork(player, listData(player, family, catalog), requestId); return;
        }
        if (action.equals("preview")) {
            CompoundTag tag = new CompoundTag(); tag.put("Preview", record.save());
            var bounds = PrefabDefinitions.transform(PrefabDefinitions.get(family).tier(record.tier() + 1).bounds(), record.anchor(), PrefabDefinitions.rotation(record.facing()));
            tag.getCompound("Preview").putLong("TargetMin", bounds.min().asLong()); tag.getCompound("Preview").putLong("TargetMax", bounds.maxExclusive().asLong());
            sendWork(player, tag, requestId); return;
        }
        BuildingPlacementService.message(player, action.equals("upgrade") ? "upgrade_hint" : "work_stale");
        sendWork(player, listData(player, family, catalog), requestId);
    }
    private static void rejectWork(ServerPlayer player, UUID requestId, String issue) {
        BuildingPlacementService.message(player, issue);
        var reply = new CompoundTag(); reply.putBoolean("Close", true);
        sendWork(player, reply, requestId);
    }
    private static void sendWork(ServerPlayer player, CompoundTag reply, UUID requestId) {
        if (requestId == null && reply.getBoolean("Close")) return;
        if (requestId != null) reply.putUUID("ReplyRequest", requestId);
        PacketDistributor.sendToPlayer(player, new BuildingWorkPayload(reply));
    }
    public static void list(ServerPlayer player, ResourceLocation family, long catalog) {
        sendWork(player, listData(player, family, catalog), null);
    }
    private static CompoundTag listData(ServerPlayer player, ResourceLocation family, long catalog) {
        CompoundTag tag = new CompoundTag(); tag.putString("Family", family.toString()); tag.putLong("Catalog", catalog);
        ListTag entries = new ListTag(); var data = BuildingWorldData.get(player.serverLevel().getServer());
        for (var record : data.all()) if (PrefabDefinitions.available(record) && record.family().equals(family) && record.mode() == BuildingRecord.Mode.PREFAB && BuildingService.canManage(player, record)) {
            CompoundTag row = record.save(); row.putBoolean("CanUpgrade",record.tier()<PrefabDefinitions.maxTier(record.family()) && record.phase()==BuildingRecord.Phase.READY);
            var order = data.order(record.id()); row.putInt("Days", order == null ? 0 : order.remainingDays());
            if (record.tier() < PrefabDefinitions.maxTier(record.family())) {
                var price = PrefabDefinitions.get(family).tier(record.tier() + 1).upgrade();
                row.putInt("Money", price.money()); row.putInt("Wood", price.wood()); row.putInt("Stone", price.stone()); row.putInt("WorkDays", price.days());
            }
            entries.add(row);
        }
        tag.put("Entries", entries); return tag;
    }

    /** Find a real front wall with two clear cells in front; never hammer empty fencing during upgrades. */
    public static BlockPos noticePosition(ServerLevel level, BuildingRecord record) {
        var nativeCells = BuildingTransfer.nativeCells(level, record, record.tier());
        return nativeCells.keySet().stream().filter(pos -> pos.getY() >= record.anchor().getY() + 1 && pos.getY() <= record.anchor().getY() + 2)
                .filter(pos -> level.getBlockState(pos).isFaceSturdy(level, pos, record.facing()))
                .map(pos -> pos.relative(record.facing()))
                .filter(pos -> record.claim().contains(pos) && record.claim().contains(pos.relative(record.facing())))
                .filter(pos -> (level.getBlockState(pos).isAir() || level.getBlockState(pos).is(ModBlocks.UPGRADE_NOTICE.get()))
                        && level.getBlockState(pos.relative(record.facing())).isAir()
                        && level.getBlockState(pos.relative(record.facing()).below()).isAir()
                        && level.getBlockState(pos.relative(record.facing()).below(2)).isFaceSturdy(level, pos.relative(record.facing()).below(2), Direction.UP))
                .min(java.util.Comparator.comparingDouble(pos -> pos.distSqr(record.anchor()))).orElse(null);
    }
    /** Stand inside a real front wall, facing outward; the exterior notice is a separate prop. */
    public static BlockPos indoorWorkPosition(ServerLevel level, BuildingRecord record) {
        var cells = BuildingTransfer.nativeCells(level, record, record.tier());
        return cells.keySet().stream().filter(pos -> pos.getY() == record.anchor().getY() + 2)
                .filter(pos -> level.getBlockState(pos).isFaceSturdy(level, pos, record.facing().getOpposite()))
                .map(pos -> pos.relative(record.facing().getOpposite()).below())
                .filter(pos -> record.claim().contains(pos) && level.getBlockState(pos).isAir()
                        && level.getBlockState(pos.above()).isAir()
                        && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP))
                .min(java.util.Comparator.comparingDouble(pos -> pos.distSqr(record.manager()))).orElse(null);
    }
    public static void upgradeScaffold(ServerLevel level, BuildingRecord record) {
        BlockPos sign = noticePosition(level, record);
        if (sign != null) BuildingProtection.internal(() -> RisingConstruction.place(level,record,sign, ModBlocks.UPGRADE_NOTICE.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, record.facing()), !BuildingWorldData.get(level.getServer()).order(record.id()).scaffoldReady()));
        BuildingWorldData.get(level.getServer()).markScaffold(record.id());
        BuildingPlacementService.ensureWorker(level, record);
    }
    public static void finishUpgrade(ServerLevel level, BuildingRecord record) {
        var data = BuildingWorldData.get(level.getServer());
        if (data.transfer(record.id()) == null) {
            BuildingTransfer transfer = BuildingTransfer.upgrade(level, record);
            if (data.beginTransfer(transfer) != BuildingWorldData.Result.SUCCESS) return;
            level.getServer().overworld().getDataStorage().save();
        }
        completeTransfer(level, data.transfer(record.id()));
        mailIncubatorOnCoopUpgrade(level, data.find(record.id()));
    }

    /** Vanilla Buildings.json: Big Coop / Deluxe Coop carry AddMailOnBuild ["incubator"], sent to every farmer. */
    private static void mailIncubatorOnCoopUpgrade(ServerLevel level, BuildingRecord upgraded) {
        if (upgraded == null || upgraded.tier() < 2 || !PrefabDefinitions.COOP.equals(upgraded.family())) return;
        var farm = com.stardew.craft.farm.FarmInstanceRegistry.get(level.getServer()).getFarmByInstanceId(upgraded.farmId());
        if (farm == null) return;
        for (var farmer : farm.getAllFarmers()) {
            var player = level.getServer().getPlayerList().getPlayer(farmer);
            if (player != null) com.stardew.craft.mail.MailService.addMail(player, "incubator");
        }
    }
    public static boolean move(ServerPlayer player, BuildingRecord record, BlockPos anchor) {
        return record != null && move(player, record, anchor, record.facing());
    }
    public static boolean move(ServerPlayer player, BuildingRecord record, BlockPos anchor, Direction facing) {
        if (record == null || record.phase() != BuildingRecord.Phase.READY
                || !BuildingService.canManage(player, record) || !record.dimension().equals(player.serverLevel().dimension().location())) return false;
        var level = player.serverLevel(); var data = BuildingWorldData.get(level.getServer());
        var probe = BuildingPlacementService.probe(level, player, anchor, facing, record.mode() == BuildingRecord.Mode.SELF_BUILT, record.family(), record);
        if (!probe.valid()) {
            BuildingPlacementService.flashProblems(player, probe.problems());
            BuildingPlacementService.message(player, probe.issue());
            return false;
        }
        if (!level.hasChunksAt(record.claim().min(), record.claim().maxInclusive())) { BuildingPlacementService.message(player, "unloaded"); return false; }
        if (anchor.equals(record.anchor()) && facing == record.facing()) return false;
        var lift=data.moveLift(record.id());
        if(lift!=null && !lift.owner().equals(player.getUUID()))return false;
        if(lift!=null)BuildingMoveSession.restoreForAttempt(level,lift);
        BuildingTransfer transfer;
        try {transfer=BuildingTransfer.move(level,record,anchor,facing);}
        catch(BuildingTransfer.Collision collision){if(lift!=null)BuildingMoveSession.hideAfterFailedAttempt(level,lift);com.stardew.craft.network.GlobalHudMessagePayload.sendTo(player,net.minecraft.network.chat.Component.translatable("building.stardewcraft.work_collision",collision.pos.getX(),collision.pos.getY(),collision.pos.getZ()));return false;}
        var result=lift==null?data.beginTransfer(transfer):data.promoteMoveLift(record.id(),player.getUUID(),transfer);
        if(result!=BuildingWorldData.Result.SUCCESS){if(lift!=null)BuildingMoveSession.hideAfterFailedAttempt(level,lift);return false;}
        level.getServer().overworld().getDataStorage().save();
        completeTransfer(level, transfer); return true;
    }
    public static void completeTransfer(ServerLevel level, BuildingTransfer transfer) {
        if (transfer == null) return;
        for (var player : level.players()) {
            if (BuildingPlacementService.aabb(transfer.before().claim()).inflate(8).contains(player.position()) && player.containerMenu != player.inventoryMenu) player.closeContainer();
        }
        var after = transfer.after();
        RisingConstruction.clear(level,after.id());
        boolean greenhouse=com.stardew.craft.greenhouse.GreenhouseBuildings.isGreenhouse(after.family());
        if(greenhouse)com.stardew.craft.interior.InteriorSubspaceManager.removeGreenhouseOutdoorPortalAt(
                level,com.stardew.craft.greenhouse.GreenhouseBuildings.portal(transfer.before()));
        transfer.project(level);
        if (FishPondPrefabs.isPond(after.family())) {
            com.stardew.craft.fishpond.data.FishPondWorldData.get(level).movePrefab(transfer.before(),after);
            com.stardew.craft.fishpond.service.FishPondColorSyncService.broadcastSnapshot(level);
        }
        // Keep the durable snapshot until both source and destination chunks have been flushed.
        // On a crash before metadata commit, replaying the pending snapshot is still safe.
        level.getChunkSource().save(true);
        BuildingWorldData.get(level.getServer()).finishTransfer(after.id());
        BuildingPlacementService.publishManager(level, after);
        if(greenhouse)com.stardew.craft.greenhouse.GreenhouseBuildings.ensurePortal(level,after);
        level.getServer().overworld().getDataStorage().save();
        level.getEntitiesOfClass(RobinConstructionEntity.class, BuildingPlacementService.aabb(transfer.before().claim()), worker -> after.id().equals(worker.buildingId())).forEach(RobinConstructionEntity::discard);
    }
}
