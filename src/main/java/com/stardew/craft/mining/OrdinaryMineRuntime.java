package com.stardew.craft.mining;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.mine.*;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.interior.InteriorSubspaceManager;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.util.StardewDeterministicRandom;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.*;

/** The approved ordinary and Skull Cavern layout runtime.  */
@EventBusSubscriber(modid=StardewCraft.MODID)
public final class OrdinaryMineRuntime {
    public static final int VERSION=1004;
    public static final String MOB_TAG="stardewcraft_ordinary_mine_mob";
    private static final ThreadLocal<Boolean> REBUILDING=ThreadLocal.withInitial(()->false);
    private static final Map<ServerLevel,Map<Integer,Long>> active=new WeakHashMap<>();
    public static boolean isRebuilding() { return REBUILDING.get(); }
    public static boolean handles(int floor) { return floor>=SkullCavernRuntime.LOBBY; }
    public static int floorAt(BlockPos pos) { return Math.max(SkullCavernRuntime.LOBBY,Math.round((pos.getZ()-14)/(float)MiningCoordinates.FLOOR_SPACING)); }
    public static MineBuildingTheme theme(int floor) {
        if(floor>120 || floor==SkullCavernRuntime.LOBBY) return MineBuildingTheme.DESERT;
        boolean dark=OrdinaryMinePopulation.dark(floor);
        return floor<40 ? (dark?MineBuildingTheme.EARTH_DARK:MineBuildingTheme.EARTH)
                : floor<80 ? (dark?MineBuildingTheme.FROST_DARK:MineBuildingTheme.FROST)
                : (dark?MineBuildingTheme.LAVA_DARK:MineBuildingTheme.LAVA);
    }
    public static MineBuildingTheme themeForLayout(ServerLevel level,int floor) {
        var layout=OrdinaryMineLayout.load(level,floor);
        return layout.metadata.has("building_theme")
                ? MineBuildingTheme.valueOf(layout.metadata.get("building_theme").getAsString().toUpperCase(Locale.ROOT)) : theme(floor);
    }
    public static String chooseLayout(ServerLevel level,int floor) {
        if(floor==SkullCavernRuntime.LOBBY) return "skull_lobby";
        if(floor>120) return SkullCavernRuntime.chooseLayout(level,floor);
        String base=OrdinaryMineLayout.nameForFloor(floor);
        if(floor<=1 || floor%5==0 || level.getServer().getPlayerList().getPlayers().stream()
                .noneMatch(p->MiningDataManager.getPlayerData(p).getMaxFloorReached()>=120)) return base;
        var r=StardewDeterministicRandom.createFromDoubles(StardewTimeManager.get().getAbsoluteDay(),level.getSeed()/2L,1293857+floor*400,0,0);
        if(r.nextDouble()>=.06) return base;
        int map=40+r.nextInt(21);
        if(Set.of(40,47,50,51).contains(map) && r.nextDouble()<.75) map=40+r.nextInt(21);
        if(map==53) map=52+r.nextInt(9);
        if(map==40 && floor>=40 && floor<80) map=52+r.nextInt(9);
        String theme=theme(floor).id();
        if(map==45 && !theme.endsWith("dark")) theme+="_dark";
        String candidate="extra_"+theme+"_"+map;
        // Layouts lacking approved ordinary architecture (e.g. prehistoric 48/53) stay on
        // the normal layout. Never synthesize a special floor or substitute another roll.
        var resource=new ResourceLocation("stardewcraft","mine_layouts/"+candidate+".json");
        return level.getServer().getResourceManager().getResource(resource).isPresent()?candidate:base;
    }
    @SubscribeEvent public static void dataReload(net.minecraftforge.event.OnDatapackSyncEvent event) {
        if(event.getPlayer()==null) OrdinaryMineLayout.clearCache();
    }
    @SubscribeEvent public static void login(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer p))return;
        var mine=p.getServer().getLevel(ModMiningDimensions.STARDEW_MINING);
        if(mine==null)return;
        OrdinaryMineProgress.get(mine).reconnect(p.getUUID());
        if(p.serverLevel()==mine) {
            int floor=MiningDataManager.getPlayerData(p).getCurrentFloor();
            if(handles(floor)) {
                var data=MineFloorDataManager.get(mine).getFloorData(floor);
                boolean rebuilt=data==null || data.getGenerationVersion()!=VERSION;
                ensure(mine,floor);
                if(rebuilt) MiningCoordinates.teleportPlayerToFloor(p,mine,floor);
            }
        }
        if(p.serverLevel()==mine && MiningDataManager.getPlayerData(p).getCurrentFloor()>120)SkullCavernSessionManager.onPlayerEnter(p);
        MineRewardClaimManager.get(mine).sync(p);
    }
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity() instanceof ServerPlayer p && p.level().dimension()==ModMiningDimensions.STARDEW_MINING) {
            int floor=floorAt(p.blockPosition());
            if(handles(floor)) OrdinaryMineProgress.get(p.serverLevel()).disconnect(p.getUUID(),floor,StardewTimeManager.get().getAbsoluteDay());
        }
    }
    public static void ensure(ServerLevel level,int floor) {
        if(!handles(floor)) throw new IllegalArgumentException("Ordinary floor " + floor);
        var manager=MineFloorDataManager.get(level); var previous=manager.getFloorData(floor);
        if(previous!=null && previous.getGenerationVersion()==VERSION
                && (floor<=0 || !manager.needsGeneration(floor) || occupied(level,floor))) {
            active.computeIfAbsent(level,k->new HashMap<>()).putIfAbsent(floor,level.getGameTime()); return;
        }
        var layout=OrdinaryMineLayout.loadNamed(level,chooseLayout(level,floor));
        // Preflight both resources before touching a world.
        var template=level.getStructureManager().get(new ResourceLocation("stardewcraft","mine_layouts/"+layout.name))
                .orElseThrow(()->new IllegalStateException("Missing mine architecture " + layout.name));
        if(!template.getSize().equals(layout.size)) throw new IllegalStateException("Mine layout/template size mismatch " + layout.name);
        BlockPos origin=layout.origin(floor);
        for(int x=origin.getX()>>4;x<=(origin.getX()+layout.size.getX())>>4;x++)
            for(int z=origin.getZ()>>4;z<=(origin.getZ()+layout.size.getZ())>>4;z++) {
                var chunk=level.getChunk(x,z);
                com.stardew.craft.dimension.MineBiomePatcher.patchChunk(level,chunk,floor);
            }
        var templateBounds=new AABB(net.minecraft.world.phys.Vec3.atLowerCornerOf(origin),net.minecraft.world.phys.Vec3.atLowerCornerOf(origin.offset(layout.size)));
        AABB previousBounds=null;
        if(previous!=null && !previous.getLayoutName().isEmpty()) {
            var old=OrdinaryMineLayout.loadNamed(level,previous.getLayoutName());var oldOrigin=old.origin(floor);
            previousBounds=new AABB(net.minecraft.world.phys.Vec3.atLowerCornerOf(oldOrigin),net.minecraft.world.phys.Vec3.atLowerCornerOf(oldOrigin.offset(old.size)));
        }
        var bounds=(previousBounds==null?templateBounds:templateBounds.minmax(previousBounds)).inflate(2);
        var data=new MineFloorData();data.setLayoutName(layout.name);
        data.setTreasureRoom(floor>120 && (layout.name.equals("desert_10") || layout.name.startsWith("desert_reward_")));
        REBUILDING.set(true);
        try {
            if(previousBounds!=null) {
                for(int x=(int)Math.floor(previousBounds.minX)>>4;x<=((int)Math.floor(previousBounds.maxX)-1)>>4;x++)
                    for(int z=(int)Math.floor(previousBounds.minZ)>>4;z<=((int)Math.floor(previousBounds.maxZ)-1)>>4;z++)level.getChunk(x,z);
                // A smaller alternate layout must not leave pieces of the previous cave outside it.
                for(BlockPos p:BlockPos.betweenClosed(BlockPos.containing(previousBounds.minX,previousBounds.minY,previousBounds.minZ),
                        BlockPos.containing(previousBounds.maxX-1,previousBounds.maxY-1,previousBounds.maxZ-1))) {
                    if(!templateBounds.contains(net.minecraft.world.phys.Vec3.atCenterOf(p)) && !level.getBlockState(p).isAir())
                        level.setBlock(p,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS);
                }
            }
            for(Entity entity:level.getEntities((Entity)null,bounds,e->!(e instanceof net.minecraft.world.entity.player.Player))) entity.discard();
            // All approved files include air. Known-shape avoids dropping wall decorations while the
            // structure is incomplete; light is still queued by ServerLevel.setBlock.
            if(!template.placeInWorld(level,origin,origin,new StructurePlaceSettings().setIgnoreEntities(false),level.random,
                    Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS))
                throw new IllegalStateException("Failed placing " + layout.name);
            for(BlockPos p:BlockPos.betweenClosed(origin,origin.offset(layout.size).offset(-1,-1,-1))) {
                // Actual floor 30 reuses map 10, but loads the dark earth tilesheet.
                if(floor==30) level.setBlock(p,darkEarthRewardState(level.getBlockState(p)),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
                if(!level.getBlockState(p).isAir()) data.markArchitecture(architectureIndex(layout,origin,p));
            }
            if(previous!=null)MineRewardClaimManager.get(level).forgetTemporaryKeys(previous.treasureKeys());
            manager.setFloorData(floor,data);
            if(floor==0) {
                var exit=layout.metadata.getAsJsonArray("surface_exit");
                InteriorSubspaceManager.placePortalTriggerArea(level,layout.position(0,exit.get(0).getAsInt(),exit.get(1).getAsInt()),2,1,1,
                        "sdv_portal_marker:mine_exit","sdv_portal_target:mine_exit");
            } else if(floor==SkullCavernRuntime.LOBBY) {
                var exit=layout.metadata.getAsJsonArray("surface_exit");
                InteriorSubspaceManager.placePortalTriggerArea(level,layout.position(floor,exit.get(0).getAsInt(),exit.get(1).getAsInt()),2,1,1,
                        "sdv_portal_marker:skull_cavern_exit","sdv_portal_target:skull_cavern_exit");
            } else {
                new OrdinaryMinePopulation(level,floor,layout,data).populate();
                if(floor>120) SkullCavernRuntime.placeTreasureChests(level,floor,layout,data);
                else if(MineChestLootTable.isChestFloor(floor)) {
                    BlockPos chest=layout.position(floor,9,floor%20==0 && floor%40!=0 ? 13:9);
                    level.setBlock(chest,ModBlocks.MINE_CHEST.get().defaultBlockState().setValue(MineChestBlock.FACING,Direction.SOUTH),2);
                }
                if(layout.metadata.has("downward_entries")) for(var entry:layout.metadata.getAsJsonArray("downward_entries")) {
                    var p=entry.getAsJsonObject().getAsJsonArray("relative");
                    BlockPos ladder=origin.offset(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt());
                    if(level.getBlockState(ladder).is(ModBlocks.MINE_LADDER.get())) {data.setLadderFound(true);data.setLadderPos(ladder);}
                }
            }
            restoreCoalCaches(level,floor,layout);
            data.setGenerationVersion(VERSION);manager.setFloorData(floor,data);manager.markGenerated(floor);
            active.computeIfAbsent(level,k->new HashMap<>()).put(floor,level.getGameTime());
        } finally { REBUILDING.remove(); }
        refreshLights(level,layout,floor);
        StardewCraft.LOGGER.info("[MINE] Approved {} floor {}: {} stones, {} monsters",layout.name,floor,data.getStonesLeft(),data.getEnemyCount());
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static net.minecraft.world.level.block.state.BlockState darkEarthRewardState(net.minecraft.world.level.block.state.BlockState state) {
        var from=MineBuildingTheme.EARTH;var to=MineBuildingTheme.EARTH_DARK;
        if(state.is(from.soil()))state=to.soil().withPropertiesOf(state);
        else if(state.is(from.looseSoil()))state=to.looseSoil().withPropertiesOf(state);
        else if(state.is(from.wall()))state=to.wall().withPropertiesOf(state);
        var property=state.getBlock().getStateDefinition().getProperty("theme");
        if(property!=null) {
            var value=property.getValue(to.id());
            if(value.isPresent())state=state.setValue((net.minecraft.world.level.block.state.properties.Property)property,(Comparable)value.get());
        }
        return state;
    }
    private static boolean occupied(ServerLevel level,int floor) {
        return level.players().stream().anyMatch(p->floorAt(p.blockPosition())==floor);
    }
    public static java.util.concurrent.CompletableFuture<Void> refreshLights(ServerLevel level,int floor) {
        return refreshLights(level,OrdinaryMineLayout.load(level,floor),floor);
    }
    public static MineLadderBlock.Theme entranceTheme(net.minecraft.world.level.BlockGetter level,BlockPos pos,int floor) {
        for(BlockPos sample:new BlockPos[]{pos,pos.below(),pos.north(),pos.south(),pos.east(),pos.west()})
            for(var theme:MineBuildingTheme.values())
                if(theme.rank(level.getBlockState(sample))>=0) return MineLadderBlock.Theme.valueOf(theme.name());
        return MineLadderBlock.Theme.valueOf(theme(floor).name());
    }
    public static java.util.concurrent.CompletableFuture<Void> refreshLights(ServerLevel level,OrdinaryMineLayout layout,int floor) {
        BlockPos origin=layout.origin(floor);
        var engine=level.getChunkSource().getLightEngine();
        for(BlockPos p:BlockPos.betweenClosed(origin,origin.offset(layout.size).offset(-1,-1,-1)))
            engine.checkBlock(p); // Include removed emitters and occlusion changes.
        var chunks=new ArrayList<net.minecraft.world.level.chunk.ChunkAccess>();
        var pending=new ArrayList<java.util.concurrent.CompletableFuture<?>>();
        for(int x=origin.getX()>>4;x<=(origin.getX()+layout.size.getX()-1)>>4;x++)
            for(int z=origin.getZ()>>4;z<=(origin.getZ()+layout.size.getZ()-1)>>4;z++) {
                chunks.add(level.getChunk(x,z));pending.add(com.stardew.craft.port.PortLevels.waitForPendingTasks(engine,x,z));
            }
        engine.tryScheduleUpdate();
        return java.util.concurrent.CompletableFuture.allOf(pending.toArray(java.util.concurrent.CompletableFuture[]::new)).thenRunAsync(()->{
            var map=level.getChunkSource().chunkMap;
            map.resendBiomesForChunks(chunks);
            for(var chunk:chunks) {
                var packet=new net.minecraft.network.protocol.game.ClientboundLightUpdatePacket(chunk.getPos(),engine,null,null);
                for(var player:map.getPlayers(chunk.getPos(),false)) player.connection.send(packet);
            }
        },level.getServer());
    }
    private static int architectureIndex(OrdinaryMineLayout layout,BlockPos origin,BlockPos p) {
        int x=p.getX()-origin.getX(),y=p.getY()-origin.getY(),z=p.getZ()-origin.getZ();
        if(x<0 || y<0 || z<0 || x>=layout.size.getX() || y>=layout.size.getY() || z>=layout.size.getZ()) return -1;
        return x+layout.size.getX()*(z+layout.size.getZ()*y);
    }
    public static boolean isArchitecture(ServerLevel level,BlockPos p) {
        if(level.dimension()!=ModMiningDimensions.STARDEW_MINING) return false;
        int floor=floorAt(p);if(!handles(floor))return false;
        var data=MineFloorDataManager.get(level).getFloorData(floor);if(data==null || data.getLayoutName().isEmpty())return false;
        var layout=OrdinaryMineLayout.load(level,floor);
        return data.isArchitecture(architectureIndex(layout,layout.origin(floor),p));
    }
    @SubscribeEvent public static void protectArchitecture(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        if(event.getLevel() instanceof ServerLevel level && !event.getPlayer().isCreative() && isArchitecture(level,event.getPos())) event.setCanceled(true);
    }
    @SubscribeEvent public static void protectArchitectureClick(net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
        if(event.getLevel() instanceof ServerLevel level && !event.getEntity().isCreative() && isArchitecture(level,event.getPos())) event.setCanceled(true);
    }
    @SubscribeEvent public static void protectArchitectureExplosion(net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
        if(event.getLevel() instanceof ServerLevel level && level.dimension()==ModMiningDimensions.STARDEW_MINING)
            event.getAffectedBlocks().removeIf(p->isArchitecture(level,p));
    }
    public static boolean placeLadder(ServerLevel level,int floor,BlockPos standing,MineFloorData data) {
        return placeLadder(level,floor,standing,data,true);
    }
    private static boolean placeLadder(ServerLevel level,int floor,BlockPos standing,MineFloorData data,boolean fromStone) {
        if(floor<=0 || floor==120 || !level.getBlockState(standing).isAir() || !level.getBlockState(standing.above()).isAir()) return false;
        BlockPos support=standing.below();
        var floorState=level.getBlockState(support);
        var theme=themeForLayout(level,floor);
        if(theme.rank(floorState)<0 || floorState.is(theme.wall())) return false;
        var state=ModBlocks.MINE_LADDER.get().defaultBlockState().setValue(MineLadderBlock.THEME,MineLadderBlock.Theme.valueOf(theme.name()));
        if(floor>120 && !data.isMonsterArea()) state=state.setValue(MineLadderBlock.SHAFT,level.random.nextDouble()<.2);
        if(!level.setBlock(support,state,3)) return false;
        if(fromStone) data.setStoneLadderSpawned(true);
        data.setLadderFound(true);data.setLadderPos(support);MineFloorDataManager.get(level).setFloorData(floor,data);
        level.playSound(null,support,com.stardew.craft.sound.ModSounds.HOE_HIT.get(),net.minecraft.sounds.SoundSource.BLOCKS,1,1);
        return true;
    }

    /** checkStoneForItems: skip one draw, decrement once, roll ladder, then share RNG with loot. */
    public static net.minecraft.util.RandomSource nodeDropRandom(ServerLevel level,ServerPlayer player,BlockPos pos) {
        if(level.dimension()!=ModMiningDimensions.STARDEW_MINING) return level.random;
        int floor=floorAt(pos);var manager=MineFloorDataManager.get(level);var data=manager.getFloorData(floor);
        if(!handles(floor) || data==null || data.getGenerationVersion()!=VERSION || !data.removeGeneratedStone(pos)) return level.random;
        manager.setFloorData(floor,data);
        var layout=OrdinaryMineLayout.load(level,floor);var o=layout.origin(floor);
        int x=pos.getX()-o.getX()-layout.tileX,z=pos.getZ()-o.getZ()-layout.tileZ;
        var r=StardewDeterministicRandom.createFromDoubles(StardewTimeManager.get().getAbsoluteDay(),level.getSeed()/2L,x*1000,z,floor).asMinecraftSource();
        r.nextDouble();
        // Source checkStoneForItems uses the observing farmer's DailyLuck even when DustSpirit
        // destroys a stone with who=null. MC stores luck per player; use the nearest floor observer.
        ServerPlayer luckOwner=player;
        if(luckOwner==null) luckOwner=level.players().stream().filter(p->!p.isSpectator()&&floorAt(p.blockPosition())==floor)
                .min(java.util.Comparator.comparingDouble(p->p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)))).orElse(null);
        double chance=LadderProbabilityCalculator.calculateProbability(data.getStonesLeft(),player==null?0:PlayerStardewDataAPI.getLuckBuffLevel(player),
                luckOwner==null?0:PlayerStardewDataAPI.getDailyLuck(luckOwner),data.getEnemyCount(),player!=null && player.hasEffect(com.stardew.craft.effect.ModMobEffects.DWARF_STATUE_1.get()));
        if(!data.hasStoneLadderSpawned() && floor!=120 && !data.isMonsterArea() && (player==null || !player.isCreative()) && (data.getStonesLeft()==0 || r.nextDouble()<chance)) {
            level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount()+1,()->{
                if(manager.getFloorData(floor)==data && !data.hasStoneLadderSpawned()) placeLadder(level,floor,pos,data);
            }));
        }
        return r;
    }
    public static boolean monsterKilled(ServerLevel level,ServerPlayer player,BlockPos pos) {
        if(level.dimension()!=ModMiningDimensions.STARDEW_MINING) return false;
        int floor=floorAt(pos);var data=MineFloorDataManager.get(level).getFloorData(floor);
        if(!handles(floor) || data==null || data.getGenerationVersion()!=VERSION) return false;
        if(floor==120) return true;
        double chance=.15+(player.hasEffect(com.stardew.craft.effect.ModMobEffects.DWARF_STATUE_1.get())?.07:0);
        if(level.random.nextDouble()<chance) {
            var layout=OrdinaryMineLayout.load(level,floor);var o=layout.origin(floor);
            // Original normal floors use the kill tile, not a random nearby room or a ceiling.
            BlockPos p=new BlockPos(pos.getX(),o.getY()+layout.tileY,pos.getZ());
            var cell=layout.cell(p.getX()-o.getX()-layout.tileX,p.getZ()-o.getZ()-layout.tileZ);
            if(cell!=null && cell.type().equals("Stone")) placeLadder(level,floor,p,data,false);
        }
        return true;
    }
    public static void barrelBroken(ServerLevel level,BlockPos pos) {
        if(isRebuilding() || level.dimension()!=ModMiningDimensions.STARDEW_MINING) return;
        int floor=floorAt(pos);if(floor<=0) return;
        var layout=OrdinaryMineLayout.load(level,floor);var o=layout.origin(floor);
        var cell=layout.cell(pos.getX()-o.getX()-layout.tileX,pos.getZ()-o.getZ()-layout.tileZ);
        if(cell!=null && cell.back()==257) OrdinaryMineProgress.get(level).breakPlatform(floor);
    }
    public static String cacheKey(int floor,BlockPos p) { return floor+":"+p.getX()+","+p.getY()+","+p.getZ(); }
    public static void coalCacheOpened(ServerLevel level,BlockPos pos) {
        int floor=floorAt(pos);
        if(level.dimension()==ModMiningDimensions.STARDEW_MINING && floor>0)
            OrdinaryMineProgress.get(level).emptyCache(cacheKey(floor,pos));
    }
    private static void restoreCoalCaches(ServerLevel level,int floor,OrdinaryMineLayout layout) {
        if(floor<=0) return;
        var progress=OrdinaryMineProgress.get(level);var origin=layout.origin(floor);
        for(BlockPos p:BlockPos.betweenClosed(origin,origin.offset(layout.size).offset(-1,-1,-1))) {
            var s=level.getBlockState(p);
            if(s.getBlock() instanceof MineCoalBackpackBlock block) {
                BlockPos main=block.findMainPos(level,p,s);
                if(main!=null && progress.cacheEmpty(cacheKey(floor,main))) level.setBlock(p,s.setValue(MineCoalBackpackBlock.OPEN,true),2);
            }
        }
        for(var cart:level.getEntitiesOfClass(com.stardew.craft.entity.minecart.CoalMinecartEntity.class,new AABB(net.minecraft.world.phys.Vec3.atLowerCornerOf(origin),net.minecraft.world.phys.Vec3.atLowerCornerOf(origin.offset(layout.size)))))
            if(progress.cacheEmpty(cacheKey(floor,cart.blockPosition()))) cart.setLoaded(false);
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level) || level.dimension()!=ModMiningDimensions.STARDEW_MINING || level.getGameTime()%20!=0) return;
        var floors=active.computeIfAbsent(level,k->new HashMap<>());
        var manager=MineFloorDataManager.get(level);
        // Register saved floors too, so a restart cannot leave unvisited floors immune to refresh.
        for(int f:manager.floorNumbers()) {
            if(f<=0)continue;
            var data=manager.getFloorData(f);
            if(data!=null && data.getGenerationVersion()==VERSION) floors.putIfAbsent(f,level.getGameTime());
        }
        int deepest=level.players().stream().mapToInt(p->floorAt(p.blockPosition())).filter(f->f>0).max().orElse(0);
        deepest=Math.max(deepest,OrdinaryMineProgress.get(level).deepestDisconnected(StardewTimeManager.get().getAbsoluteDay()));
        for(var iterator=floors.entrySet().iterator();iterator.hasNext();) {
            var entry=iterator.next();int floor=entry.getKey();
            if(floor>0 && floor>deepest && level.getGameTime()-entry.getValue()>40 && !occupied(level,floor)) {
                var data=manager.getFloorData(floor);if(data!=null) {data.setGenerationVersion(0);manager.setFloorData(floor,data);}
                iterator.remove();
            }
        }
    }
}
