package com.stardew.craft.animal.runtime;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.AutoFeedTroughBlockEntity;
import com.stardew.craft.blockentity.FeedTroughBlockEntity;
import com.stardew.craft.building.runtime.*;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.festival.FestivalService;
import com.stardew.craft.player.*;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.util.StardewDeterministicRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import java.nio.charset.StandardCharsets;
import java.util.*;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class LivestockService {
    private LivestockService() {}
    public static ServerLevel level(MinecraftServer server, BuildingRecord home) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, home.dimension()));
    }
    public static boolean hasHay(ServerLevel level, BlockPos pos, boolean consume) {
        var facility=com.stardew.craft.api.v1.agriculture.StardewAnimalFacilities.resolve(level,pos);int hay=facility.access().hay();
        if(hay<=0)return false;
        if(consume)facility.access().apply(facility.access().withHay(facility.access().snapshot(),hay-1));return true;
    }
    public static void recover(MinecraftServer server) {
        var data = LivestockWorldData.get(server); var pending = data.pending();
        if (pending == null) { LegacyLivestockMigration.ensure(server); return; }
        var home = BuildingWorldData.get(server).find(pending.home());
        if (home != null) {
            var level = level(server, home);
            if (level == null) throw new IllegalStateException("Missing livestock dimension");
            LivestockHomes.load(level, LivestockHomes.bounds(home));
            pending.collectors().forEach((pos,state)->com.stardew.craft.api.v1.agriculture.StardewAnimalFacilities.apply(level,pos,state));
            level.getChunkSource().save(true);
        }
        LivestockStats.apply(pending.statTargets());server.overworld().getDataStorage().save();
        data.finish(); server.overworld().getDataStorage().save();
        LegacyLivestockMigration.ensure(server);
    }
    public static void onNewDay(ServerLevel clockLevel) { onNewDay(clockLevel, 1560); }
    public static void onNewDay(ServerLevel clockLevel, int sleptAtMinutes) {
        var server = clockLevel.getServer(); recover(server);
        var data = LivestockWorldData.get(server); var buildings = BuildingWorldData.get(server);
        int today = StardewTimeManager.get().getAbsoluteDay();
        var homes = new LinkedHashSet<UUID>(); data.all().forEach(a -> homes.add(a.home()));
        buildings.all().stream().filter(b -> PrefabDefinitions.supported(b.family()) && LivestockHomes.automaticFeed(b)).forEach(b -> homes.add(b.id()));
        for (var id : homes) {
            var home = buildings.find(id);
            // Missing homes retain records and occupancy; they aren't silently sold or deleted.
            if (home == null || home.phase() == BuildingRecord.Phase.MISSING || !PrefabDefinitions.available(home) || buildings.transfer(id) != null) continue;
            var level = level(server, home); if (level == null) continue;
            var residents = data.all().stream().filter(a -> a.home().equals(id) && a.farm().equals(home.farmId())).toList();
            int first = residents.stream().mapToInt(LivestockRecord::settledDay).min().orElse(data.feedDay(id) < today ? today - 1 : today) + 1;
            if (first > today) continue;
            var bounds = LivestockHomes.bounds(home); LivestockHomes.load(level, bounds);
            boolean autoPetter = false;
            for (var pos : BlockPos.betweenClosed(bounds.min(), bounds.maxInclusive()))
                if (com.stardew.craft.api.v1.agriculture.StardewAnimalFacilities.count(level,pos,com.stardew.craft.api.v1.agriculture.StardewAnimalFacilities.Role.AUTO_PETTER)>0) { autoPetter = true; break; }
            for (int day = first; day <= today; day++) {
                var hay = new ArrayDeque<BlockPos>();
                for (var pos : BlockPos.betweenClosed(bounds.min(), bounds.maxInclusive())) {
                    var access=com.stardew.craft.api.v1.agriculture.StardewAnimalFacilities.resolve(level,pos).access();
                    if(access.units(com.stardew.craft.api.v1.agriculture.StardewAnimalFacilities.Role.TROUGH)+access.units(com.stardew.craft.api.v1.agriculture.StardewAnimalFacilities.Role.AUTOMATIC_TROUGH)>0)
                        for(int serving=0;serving<Math.min(Math.max(0,access.hay()),residents.size());serving++)hay.add(pos.immutable());
                }
                var next = new ArrayList<LivestockRecord>(); var eggs = new ArrayList<LivestockWorldData.Product>(); var consumed = new ArrayList<BlockPos>();
                boolean festival = FestivalService.isFestivalDay((day - 1) % 28 + 1, (day - 1) / 28 % 4);
                for (var old : residents) {
                    var animal = data.find(old.id()); if (animal.settledDay() >= day || !animal.species().known()) continue;
                    if (data.newborn(animal.id())) { next.add(animal.withCare(day, animal.care())); continue; }
                    boolean outside = animal.location() != null && animal.location().outside();
                    if (outside && data.outdoorsAllowed(id)) {
                        var inside = LivestockHomes.spawn(level, home, animal.species(), animal.baby());
                        var projection = level.getEntity(animal.id());
                        boolean resting = !(projection instanceof net.minecraft.world.entity.PathfinderMob entity) || entity.getNavigation().isDone();
                        var returned = animal.withCare(day, sleptAtMinutes > 1080 && resting ? animal.care().mood(animal.care().happiness() / 2) : animal.care());
                        if (inside != null) returned = returned.at(new LivestockLocation(inside, home.anchor(), false, day * 144 + 36, false));
                        animal = returned; outside = returned.location()!=null && returned.location().outside();
                    }
                    boolean feed = animal.species().hasDefaultBehavior() && !outside && animal.care().fullness() < 200 && !hay.isEmpty();
                    if (feed) consumed.add(hay.removeFirst());
                    var random = StardewDeterministicRandom.create(animal.randomId() / 2, day, 0);
                    var settled=LivestockDayState.settle(level,home,animal,day,feed,festival,outside,!outside&&!data.outdoorsAllowed(id)&&home.residence()==BuildingRecord.Residence.VALID,random,eggs,LivestockProducts.dailyLuck(server,home,day));
                    if (animal.location() != null) settled = settled.at(new LivestockLocation(animal.location().position(), home.anchor(), outside, day * 144 + 36, outside));
                    if (autoPetter && !outside && settled.species().hasDefaultBehavior()) settled = settled.withCare(day, settled.care().pet(settled.species(), false, true));
                    next.add(settled);
                }
                var filled = new ArrayList<BlockPos>(); int stored = data.hay(home.farmId());
                // AnimalHouse.DayUpdate calls base (animal settlement) before feedAllAnimals.
                if (LivestockHomes.automaticFeed(home) && home.residence() == BuildingRecord.Residence.VALID && data.feedDay(id) < day) {
                    int places = 0;
                    for (var pos : BlockPos.betweenClosed(bounds.min(), bounds.maxInclusive())) {
                        if (stored == 0 || places >= LivestockHomes.capacity(level,home)) break;
                        var access=com.stardew.craft.api.v1.agriculture.StardewAnimalFacilities.resolve(level,pos).access();
                        int units=Math.min(Math.max(0,access.units(com.stardew.craft.api.v1.agriculture.StardewAnimalFacilities.Role.AUTOMATIC_TROUGH)),LivestockHomes.capacity(level,home)-places);
                        if(units>0){
                            places+=units;int remaining=Math.max(0,access.hay()-(int)consumed.stream().filter(pos::equals).count());
                            int amount=Math.min(stored,Math.max(0,units-remaining));stored-=amount;
                            for(int serving=0;serving<amount;serving++)filled.add(pos.immutable());
                        }
                    }
                }
                if (next.isEmpty() && data.feedDay(id) >= day) continue;
                var collectors = new LinkedHashMap<BlockPos,net.minecraft.nbt.CompoundTag>(LivestockCollectors.collect(level, home, next, eggs));
                var changedFeed=new LinkedHashSet<BlockPos>(consumed);changedFeed.addAll(filled);
                for(var pos:changedFeed){
                    var facility=com.stardew.craft.api.v1.agriculture.StardewAnimalFacilities.resolve(level,pos);
                    var state=collectors.containsKey(pos)?collectors.get(pos).getCompound("State"):facility.access().snapshot();
                    int count=Math.max(0,facility.access().hay()-(int)consumed.stream().filter(pos::equals).count())+(int)filled.stream().filter(pos::equals).count();
                    collectors.put(pos,facility.plan(facility.access().withHay(state,count)));
                }
                var stats=LivestockStats.plan(next);
                data.prepare(new LivestockWorldData.Batch(id, next, eggs, consumed, filled, home.farmId(), stored, day, collectors,stats));
                server.overworld().getDataStorage().save(); recover(server);
            }
        }
        LivestockBirths.onNewDay(server);
    }
    public static void pet(ServerPlayer player, net.minecraft.world.entity.PathfinderMob entity) {
        var server = player.serverLevel().getServer(); recover(server);
        var data = LivestockWorldData.get(server); var animal = data.find(entity.getUUID());
        var home = animal == null ? null : BuildingWorldData.get(server).find(animal.home());
        if (home == null || !BuildingService.canManage(player, home)) { message(player, "permission"); return; }
        if (StardewTimeManager.get().getCurrentTime() >= 1140) { message(player, "sleeping"); return; }
        if (!animal.care().petted() && animal.species().hasDefaultBehavior()) {
            data.put(animal.withCare(animal.settledDay(), animal.care().pet(animal.species(), animal.species().profession()!=null && PlayerStardewDataAPI.hasProfession(player, animal.species().profession()), false)));
            var definition=animal.species().definition();
            if(definition!=null&&definition.soundEventId()!=null)net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getOptional(definition.soundEventId()).ifPresent(sound->entity.playSound(sound,1,1));
            PlayerStardewDataAPI.addExperience(player, SkillType.FARMING, 5);
            player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, entity.getX(), entity.getY() + .8, entity.getZ(), 3, .15, .1, .15, 0);
        }
        var care = data.find(animal.id()).care();
        com.stardew.craft.network.GlobalHudMessagePayload.sendTo(player, Component.translatable("livestock.stardewcraft.care", animal.name(), care.age(), care.friendship(), care.happiness()));
    }
    public static void message(ServerPlayer player, String key) { com.stardew.craft.network.GlobalHudMessagePayload.sendTo(player, Component.translatable("livestock.stardewcraft." + key)); }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 100 != 0) return;
        var clock = event.getServer().getLevel(ModDimensions.STARDEW_VALLEY);
        if (clock != null) onNewDay(clock);
        LivestockBirths.promptOnline(event.getServer());
        LivestockOutdoors.simulateUnloaded(event.getServer());
        project(event.getServer());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { LivestockShop.clear(); LivestockManagement.clear(); LivestockBirths.clear(); }
    public static void project(MinecraftServer server) {
        recover(server);
        var data = LivestockWorldData.get(server); if (data.pending() != null) return;
        var buildings = BuildingWorldData.get(server);
        var spawns = new HashMap<UUID, BlockPos>();
        for (var animal : data.all()) {
            if (data.newborn(animal.id()) || !animal.species().known()) continue;
            var home = buildings.find(animal.home());
            if (home == null || !PrefabDefinitions.available(home) || home.phase() == BuildingRecord.Phase.MISSING || !home.farmId().equals(animal.farm()) || buildings.transfer(home.id()) != null) continue;
            var level = level(server, home); var bounds = LivestockHomes.bounds(home);
            if (level == null || !level.hasChunksAt(bounds.min(), bounds.maxInclusive())) continue;
            var existing = level.getEntity(animal.id());
            if (existing instanceof net.minecraft.world.entity.PathfinderMob chicken) {
                if(LivestockProjection.matches(chicken,animal)){LivestockProjection.refresh(chicken,animal);continue;}
                chicken.discard();existing=null;
            }
            if (existing != null) continue;
            if (animal.location() != null && !level.hasChunkAt(animal.location().position())) continue;
            var spawn = animal.location() != null ? animal.location().position() : LivestockHomes.spawn(level, home, animal.species(), animal.baby());
            if (spawn == null) continue;
            var chicken = LivestockProjection.create(level,animal); if (chicken == null) continue;
            chicken.setUUID(animal.id()); chicken.moveTo(spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5, 0, 0);
            LivestockProjection.refresh(chicken,animal); level.addFreshEntity(chicken);
        }
        for (var egg : data.eggs()) {
            var home = buildings.find(egg.home()); if (home == null || home.phase() == BuildingRecord.Phase.MISSING || !PrefabDefinitions.available(home) || buildings.transfer(home.id()) != null) continue;
            var level = level(server, home); var bounds = LivestockHomes.bounds(home);
            if (level == null || !level.hasChunksAt(bounds.min(), bounds.maxInclusive()) || level.getEntity(egg.id()) != null) continue;
            var spawn = egg.position() == null ? spawns.computeIfAbsent(home.id(), ignored -> LivestockHomes.spawn(level, home)) : egg.position(); if (spawn == null) continue;
            var projection = new LivestockProductEntity(ModEntities.LIVESTOCK_PRODUCT.get(), level);
            projection.setUUID(egg.id()); projection.setItem(LivestockProductEntity.stack(egg,level));
            // Stable small offsets keep a few eggs readable without occupying navigation cells.
            int offset = Math.floorMod(egg.id().hashCode(), 9);
            projection.setPos(spawn.getX() + .25 + offset % 3 * .25, spawn.getY() + .05, spawn.getZ() + .25 + offset / 3 * .25);
            level.addFreshEntity(projection);
        }
    }
}
