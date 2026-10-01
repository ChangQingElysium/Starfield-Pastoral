package com.stardew.craft.building.runtime;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.festival.FestivalService;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class BuildingRuntimeEvents {
    private static final java.util.Map<java.util.UUID, String> PROJECTED = new java.util.HashMap<>();
    private BuildingRuntimeEvents() {}
    @SubscribeEvent public static void started(ServerStartedEvent event) { BuildingWorldData.get(event.getServer()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { BuildingWorldData.unload(event.getServer()); BuildingProtection.clearMasks(); BuildingPreviewService.clear(); PROJECTED.clear(); BuildingLedgerService.clear(); }

    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        BuildingPreviewService.forget(event.getEntity().getUUID());
        if(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)BuildingMoveSession.restoreOwner(player);
    }

    public static void onNewDay(ServerLevel level) {
        var clock = StardewTimeManager.get();
        boolean work = !FestivalService.isFestivalDay(clock.getCurrentDay(), clock.getCurrentSeason())
                && !(clock.getCurrentYear() == 1 && "GreenRain".equals(WeatherManager.getCurrentWeather(level)));
        BuildingWorldData.get(level.getServer()).constructionThrough(clock.getAbsoluteDay(), work,
                date -> !FestivalService.isFestivalDay((date - 1) % 28 + 1, (date - 1) / 28 % 4));
        update(level, true);
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        for(var level:event.getServer().getAllLevels()) RisingConstruction.tick(level);
        if(event.getServer().getTickCount()%20==0) {BuildingRemovalJournal.get(event.getServer()).recover(event.getServer());BuildingMoveSession.recoverAbandoned(event.getServer());}
        if(event.getServer().getTickCount()%20==0) for(var player:event.getServer().getPlayerList().getPlayers()) BuildingDrafts.get(event.getServer()).synchronize(player);
        if (event.getServer().getTickCount() % 100 != 0) return;
        ServerLevel level = event.getServer().getLevel(ModDimensions.STARDEW_VALLEY);
        if (level != null) onNewDay(level);
        for (var player : event.getServer().getPlayerList().getPlayers()) {
            var farm = com.stardew.craft.farm.FarmInstanceRegistry.get(event.getServer()).getFarmForPlayer(player.getUUID());
            if (farm == null) continue;
            for (var home : BuildingWorldData.get(event.getServer()).all()) {
                if (!home.farmId().equals(farm.getInstanceId()) || home.phase()!=BuildingRecord.Phase.READY && home.phase()!=BuildingRecord.Phase.UPGRADING) continue;
                if (home.family().equals(PrefabDefinitions.COOP) || home.family().equals(PrefabDefinitions.BARN))
                    com.stardew.craft.quest.StardewQuestEvents.fireBuildingExists(player,home.family().equals(PrefabDefinitions.COOP)?"Coop":"Barn");
            }
        }
    }

    @SubscribeEvent
    public static void managerUse(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event){
        // Let the silo block consume held hay before the generic manager shortcut opens
        // the ledger. Cancelling here made RuntimeSiloManagerBlock.useItemOn unreachable.
        if (event.getItemStack().is(com.stardew.craft.item.ModItems.HAY.get())
                && event.getLevel().getBlockState(event.getPos()).is(ModBlocks.SILO_MANAGER.get())) return;
        if(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player && event.getHand()==net.minecraft.world.InteractionHand.MAIN_HAND
                && !(event.getItemStack().getItem() instanceof BuildingUpgradePermitItem) && !(event.getItemStack().getItem() instanceof BuildingBlueprintItem)
                && BuildingManagerInteraction.open(player,event.getPos())){
            event.setCanceled(true);event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }
    }

    private static void update(ServerLevel level, boolean daily) {
        var data = BuildingWorldData.get(level.getServer());
        for (var record : data.all()) {
            if (!record.dimension().equals(level.dimension().location()) || !UtilityBuildings.managed(record.family()) || !UtilityBuildings.supported(record.family())&&!PrefabDefinitions.available(record)) continue;
            if(data.moveLift(record.id())!=null){PROJECTED.remove(record.id());continue;}
            var transfer = data.transfer(record.id());
            if (transfer != null) {
                for (var bounds : java.util.List.of(transfer.before().claim(), transfer.after().claim())) {
                    for (int x = bounds.min().getX() >> 4; x <= bounds.maxInclusive().getX() >> 4; x++)
                        for (int z = bounds.min().getZ() >> 4; z <= bounds.maxInclusive().getZ() >> 4; z++) level.getChunk(x, z);
                }
                BuildingLifecycleService.completeTransfer(level, transfer); PROJECTED.remove(record.id()); continue;
            }
            if (record.mode() == BuildingRecord.Mode.SELF_BUILT) {
                if (!level.hasChunksAt(record.claim().min(), record.claim().maxInclusive())) continue;
                if (!level.getBlockState(record.manager()).is(PrefabDefinitions.managerBlock(record.family()))) data.detachSelf(record.id());
                else UtilityBuildings.refresh(level, record);
                continue;
            }
            var order = data.order(record.id());
            if (order == null) {
                if (record.phase() == BuildingRecord.Phase.READY) {
                    if (!level.hasChunksAt(record.claim().min(), record.claim().maxInclusive())) PROJECTED.remove(record.id());
                    else if (!PROJECTED.getOrDefault(record.id(), "").equals("ready:" + record.revision())) {
                        BuildingPlacementService.reconcileCompleted(level, record);
                        if (UtilityBuildings.supported(record.family())) {
                            UtilityBuildings.refresh(level, data.find(record.id()));
                        }
                        PROJECTED.put(record.id(), "ready:" + data.find(record.id()).revision());
                    }
                }
                continue;
            }
            if (daily && order.remainingDays() == 0) {
                for (int x = record.claim().min().getX() >> 4; x <= record.claim().maxInclusive().getX() >> 4; x++) {
                    for (int z = record.claim().min().getZ() >> 4; z <= record.claim().maxInclusive().getZ() >> 4; z++) level.getChunk(x, z);
                }
            }
            if (!level.hasChunksAt(record.claim().min(), record.claim().maxInclusive())) { PROJECTED.remove(record.id()); continue; }
            try {
                // A finished order needs no temporary fence/worker reconstruction before projection.
                if (order.remainingDays() == 0 && order.scaffoldReady()) {
                    if (record.phase() == BuildingRecord.Phase.UPGRADING) BuildingLifecycleService.finishUpgrade(level, record);
                    else BuildingPlacementService.finish(level, record);
                    PROJECTED.remove(record.id());
                    continue;
                }
                String projection = record.phase() + ":" + order.remainingDays();
                if (!order.scaffoldReady() || !projection.equals(PROJECTED.get(record.id()))) {
                    if (record.phase() == BuildingRecord.Phase.UPGRADING) BuildingLifecycleService.upgradeScaffold(level, record);
                    else BuildingPlacementService.scaffold(level, record);
                    PROJECTED.put(record.id(), projection);
                }
                if (order.remainingDays() == 0) {
                    if (record.phase() == BuildingRecord.Phase.UPGRADING) BuildingLifecycleService.finishUpgrade(level, record);
                    else BuildingPlacementService.finish(level, record);
                }
                else BuildingPlacementService.ensureWorker(level, record);
            } catch (BuildingTransfer.Collision collision) {
                // Expected player obstruction; keep the paid order pending. The manager reports its position.
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error("Building order {} could not project its world state", record.id(), exception);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void transferDrops(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel && BuildingProtection.transferring() && event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity) event.setCanceled(true);
    }
    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) event.getAffectedBlocks().removeIf(pos -> BuildingProtection.protects(level, pos));
    }
    @SubscribeEvent public static void piston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var helper = event.getStructureHelper();
        if (helper == null || !helper.resolve()) return;
        for (BlockPos pos : helper.getToPush()) {
            if (BuildingProtection.protects(level, pos) || BuildingProtection.protects(level, pos.relative(event.getDirection()))) {
                event.setCanceled(true); return;
            }
        }
        for (BlockPos pos : helper.getToDestroy()) if (BuildingProtection.protects(level, pos)) { event.setCanceled(true); return; }
    }
}
