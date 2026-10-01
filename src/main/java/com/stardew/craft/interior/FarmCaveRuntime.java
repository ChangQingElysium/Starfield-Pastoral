package com.stardew.craft.interior;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.farm.StardewFarmLayout;
import com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.PortalTriggerBlockEntity;
import com.stardew.craft.core.FarmAreaResolver;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.farm.*;
import com.stardew.craft.mining.StructureLoader;
import com.stardew.craft.warp.ModTeleport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.server.ServerStoppingEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.LevelTickEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.*;

/** Server-authoritative cave construction, migration and asynchronous, validated door travel. */
@EventBusSubscriber(modid=StardewCraft.MODID)
public final class FarmCaveRuntime {
    private static final TicketType<UUID> TICKET=TicketType.create("stardewcraft_farm_cave",UUID::compareTo);
    private static final Map<ServerLevel,Map<UUID,Job>> JOBS=new IdentityHashMap<>();
    private static final Map<UUID,Transit> TRAVEL=new HashMap<>();
    private static Map<BlockPos,BlockState> architecture;
    private static Map<BlockPos,BlockState> legacyArchitecture;
    private static final String COOLDOWN="stardewcraft_last_portal_tick";
    private FarmCaveRuntime() {}

    private static final class Lease {
        final ServerLevel level;final UUID id=UUID.randomUUID();final Set<ChunkPos> chunks=new HashSet<>();
        Lease(ServerLevel level) { this.level=level; }
        void region(BlockPos min,int width,int length) {
            for(int x=(min.getX()-1)>>4;x<=(min.getX()+width)>>4;x++)
                for(int z=(min.getZ()-1)>>4;z<=(min.getZ()+length)>>4;z++) {
                    ChunkPos cp=new ChunkPos(x,z);
                    if(chunks.add(cp))level.getChunkSource().addRegionTicket(TICKET,cp,0,id);
                }
        }
        boolean loaded() { return chunks.stream().allMatch(p->level.getChunkSource().getChunkNow(p.x,p.z)!=null); }
        boolean lit() { return loaded() && chunks.stream().allMatch(p->level.getChunkSource().getChunkNow(p.x,p.z).isLightCorrect()); }
        void close() { for(ChunkPos p:chunks)level.getChunkSource().removeRegionTicket(TICKET,p,0,id);chunks.clear(); }
    }
    private static final class Job {
        final FarmCaveData.Entry entry;final Lease lease;final long start;
        long built=-1,release=-1;boolean failed;
        final Map<Integer,Runnable> daily=new LinkedHashMap<>();
        Job(ServerLevel level,FarmCaveData.Entry entry) {
            this.entry=entry;start=level.getGameTime();lease=new Lease(level);
            lease.region(entry.origin,FarmCaveLayout.WIDTH,FarmCaveLayout.LENGTH);
            if(!entry.installed() && entry.legacy!=null)lease.region(entry.legacy,9,10);
        }
    }
    private static final class Transit {
        final ServerPlayer player;final ServerLevel level;final UUID farmId;final boolean entering;
        final BlockPos target;final Vec3 source;final float yaw;final long start;final Lease lease;
        final boolean rescue,gravity;
        long arrived=-1;
        Transit(ServerPlayer p,UUID farmId,boolean entering,BlockPos target,float yaw,boolean rescue) {
            player=p;level=p.serverLevel();this.farmId=farmId;this.entering=entering;
            this.target=target;this.yaw=yaw;source=p.position();start=level.getGameTime();this.rescue=rescue;
            gravity=p.isNoGravity();if(rescue)p.setNoGravity(true);
            lease=new Lease(level);lease.region(target.offset(-2,0,-2),5,5);
        }
        void close() { lease.close();if(rescue)player.setNoGravity(gravity); }
    }
    private static Map<BlockPos,BlockState> read(String name) {
        var parsed=StructureLoader.readStructureNbtBlocks("data/stardewcraft/structure/farm_layouts/"+name+".nbt");
        if(parsed==null)throw new IllegalStateException("Missing cave template "+name);
        Map<BlockPos,BlockState> result=new LinkedHashMap<>();
        for(var b:parsed.states())result.put(new BlockPos(b.dx(),b.dy(),b.dz()),b.state());
        return result;
    }
    private static Map<BlockPos,BlockState> architecture() {
        if(architecture==null)architecture=read("cave");return architecture;
    }
    /**
     * Old farm saves keep a layout snapshot.  That snapshot predates the cave
     * exit field in some worlds, so portal travel must prefer the current
     * registered layout whenever its authored farm dimensions still match.
     */
    private static StardewFarmLayout layoutFor(FarmInstance farm) {
        StardewFarmLayout saved=farm.getFarmLayout();
        if(saved==null)return StardewFarmLayoutRegistry.find(farm.getFarmLayoutId()).orElse(null);
        return StardewFarmLayoutRegistry.find(farm.getFarmLayoutId())
                .filter(current -> current.width()==saved.width()
                        && current.height()==saved.height()
                        && current.length()==saved.length())
                .orElse(saved);
    }
    public static FarmInstance farm(UUID id) {
        for(FarmInstance f:FarmInstanceRegistry.get().getAllFarms())if(f.getInstanceId().equals(id))return f;
        return null;
    }
    public static FarmInstance farmAt(ServerLevel level,BlockPos pos) {
        if(!level.dimension().equals(ModDimensions.STARDEW_VALLEY))return null;
        if(!isCaveRegion(pos))return null;
        var e=FarmCaveData.get(level).atColumn(pos);return e==null?null:farm(e.farmId);
    }
    public static BlockPos origin(ServerLevel level,FarmInstance farm) { return FarmCaveData.get(level).allocate(level,farm).origin; }
    public static boolean installed(ServerLevel level,FarmInstance farm) {
        var e=FarmCaveData.get(level).find(farm.getInstanceId());return e!=null && e.installed();
    }
    /** Cancels all runtime work for a deleted farm and removes its private cave volume. */
    public static void retire(ServerLevel level,UUID farmId) {
        Map<UUID,Job> jobs=JOBS.get(level);Job job=jobs==null?null:jobs.remove(farmId);
        if(job!=null)job.lease.close();if(jobs!=null && jobs.isEmpty())JOBS.remove(level);
        for(var entry:new ArrayList<>(TRAVEL.entrySet()))if(entry.getValue().farmId.equals(farmId)) {
            entry.getValue().close();TRAVEL.remove(entry.getKey());
        }
        BlockPos origin=FarmCaveData.get(level).remove(farmId);if(origin==null)return;
        for(BlockPos pos:BlockPos.betweenClosed(origin,
                origin.offset(FarmCaveLayout.WIDTH-1,FarmCaveLayout.HEIGHT-1,FarmCaveLayout.LENGTH-1))) {
            if(!level.getBlockState(pos).isAir())level.setBlock(pos,Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS);
        }
    }
    /** Request only: never synchronously wait for a cold chunk. */
    public static void request(ServerLevel level,FarmInstance farm) {
        if(!level.dimension().equals(ModDimensions.STARDEW_VALLEY))return;
        var e=FarmCaveData.get(level).allocate(level,farm);
        var jobs=JOBS.computeIfAbsent(level,l->new LinkedHashMap<>());
        Job previous=jobs.get(e.farmId);
        if(previous!=null && previous.failed && level.getGameTime()-previous.start>20)jobs.remove(e.farmId);
        else if(previous!=null && previous.release>=0 && (!safe(level,e.origin.offset(FarmCaveLayout.SPAWN))
                || spawnLampPresent(level,e.origin) || !exitReady(level,e.origin)
                || !e.appliedChoice.equals(farm.getCaveChoice().name()))) {
            previous.built=-1;previous.release=-1;
        }
        jobs.computeIfAbsent(e.farmId,id->new Job(level,e));
    }
    public static void daily(ServerLevel level,FarmInstance farm,int day,Runnable action) {
        var entry=FarmCaveData.get(level).allocate(level,farm);
        if(entry.lastDay>=day)return;
        request(level,farm);
        JOBS.get(level).get(farm.getInstanceId()).daily.putIfAbsent(day,action);
    }
    public static boolean isCaveRegion(BlockPos pos) {
        return pos.getX()>=FarmCaveLayout.BASE.getX() && pos.getX()<FarmCaveLayout.BASE.getX()+16
                && pos.getY()>=FarmCaveLayout.BASE.getY() && pos.getY()<FarmCaveLayout.BASE.getY()+13
                && pos.getZ()>=FarmCaveLayout.BASE.getZ() && (pos.getZ()-FarmCaveLayout.BASE.getZ())%32<18;
    }
    public static boolean ready(ServerLevel level,FarmInstance farm) {
        var e=FarmCaveData.get(level).find(farm.getInstanceId());
        Job job=JOBS.getOrDefault(level,Map.of()).get(farm.getInstanceId());
        return e!=null && e.installed() && (job==null || job.release>=0) && safe(level,e.origin.offset(FarmCaveLayout.SPAWN)) && exitReady(level,e.origin);
    }
    public static boolean safe(ServerLevel level,BlockPos pos) {
        if(level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null)return false;
        BlockPos below=pos.below();
        return level.getBlockState(below).isFaceSturdy(level,below,Direction.UP)
                && level.getFluidState(pos).isEmpty() && level.getFluidState(pos.above()).isEmpty()
                && level.noCollision(new AABB(pos.getX()+.2,pos.getY(),pos.getZ()+.2,pos.getX()+.8,pos.getY()+1.8,pos.getZ()+.8));
    }
    public static boolean fixed(ServerLevel level,BlockPos pos) {
        FarmInstance f=farmAt(level,pos);if(f==null)return false;
        BlockPos local=pos.subtract(origin(level,f));
        return architecture().containsKey(local) || FarmCaveLayout.reserved(local);
    }
    private static boolean exitReady(ServerLevel level,BlockPos origin) {
        BlockPos base=origin.offset(FarmCaveLayout.EXIT);
        if(level.getChunkSource().getChunkNow(base.getX()>>4,base.getZ()>>4)==null || !safe(level,base))return false;
        for(int y=0;y<2;y++)if(!(level.getBlockEntity(base.above(y)) instanceof PortalTriggerBlockEntity be)
                || !"farm_cave_exit".equals(be.getTargetId()))return false;
        return true;
    }
    private static boolean spawnLampPresent(ServerLevel level,BlockPos origin) {
        BlockPos lamp=origin.offset(FarmCaveLayout.SPAWN).above();
        return level.getChunkSource().getChunkNow(lamp.getX()>>4,lamp.getZ()>>4)!=null
                && level.getBlockState(lamp).is(ModBlocks.MINE_LAMP.get());
    }
    private static void removeSpawnLamp(ServerLevel level,BlockPos origin) {
        BlockPos lamp=origin.offset(FarmCaveLayout.SPAWN).above();
        if(level.getBlockState(lamp).is(ModBlocks.MINE_LAMP.get()))
            level.removeBlock(lamp,false);
    }
    private static void exitPortal(ServerLevel level,BlockPos origin) {
        for(int y=0;y<2;y++) {
            BlockPos p=origin.offset(FarmCaveLayout.EXIT).above(y);
            var current=level.getBlockState(p);
            if(!current.isAir() && !current.is(ModBlocks.PORTAL_TRIGGER.get()))
                throw new IllegalStateException("Cave exit occupied at "+p);
            if(!current.is(ModBlocks.PORTAL_TRIGGER.get()))level.setBlock(p,ModBlocks.PORTAL_TRIGGER.get().defaultBlockState(),Block.UPDATE_ALL);
            if(!(level.getBlockEntity(p) instanceof PortalTriggerBlockEntity be))throw new IllegalStateException("Missing cave portal entity at "+p);
            if(!"farm_cave_exit".equals(be.getTargetId()))be.configure("farm_cave_exit","sdv_portal_marker:farm_cave_inside");
        }
    }
    private record Content(BlockPos old,BlockState state,CompoundTag nbt) {}
    private record Move(Content content,BlockPos local) {}
    private static List<Move> migration(ServerLevel level,FarmCaveData.Entry e) {
        if(e.legacy==null)return List.of();
        if(legacyArchitecture==null)legacyArchitecture=read("cave_legacy");
        Map<BlockPos,Content> pending=new LinkedHashMap<>();
        for(BlockPos p:BlockPos.betweenClosed(0,0,0,8,5,9)) {
            BlockPos at=e.legacy.offset(p);BlockState state=level.getBlockState(at);
            if(state.isAir() || state.is(ModBlocks.PORTAL_TRIGGER.get()))continue;
            BlockState expected=legacyArchitecture.get(p);
            if(expected!=null && state.is(expected.getBlock()))continue;
            var be=level.getBlockEntity(at);
            pending.put(p.immutable(),new Content(p.immutable(),state,be==null?null:be.saveWithFullMetadata(level.registryAccess())));
        }
        List<Move> moves=new ArrayList<>();Set<BlockPos> used=new HashSet<>();
        FarmInstance ownerFarm=farm(e.farmId);
        if(ownerFarm!=null && ownerFarm.getCaveChoice()==FarmCaveChoice.MUSHROOMS) {
            used.addAll(FarmCaveLayout.BOXES);used.add(FarmCaveLayout.DEHYDRATOR);
        }
        for(int i=0;i<FarmCaveLayout.LEGACY_BOXES.size();i++) {
            Content c=pending.get(FarmCaveLayout.LEGACY_BOXES.get(i));
            if(c!=null && c.state.is(ModBlocks.MUSHROOM_BOX.get())) {
                BlockPos to=FarmCaveLayout.BOXES.get(i);moves.add(new Move(c,to));used.add(to);pending.remove(c.old);
            }
        }
        // Move connected furnishings as a unit, preserving double blocks and relative positions.
        while(!pending.isEmpty()) {
            Content first=pending.values().iterator().next();List<Content> group=new ArrayList<>();Deque<BlockPos> queue=new ArrayDeque<>();queue.add(first.old);
            while(!queue.isEmpty()) {
                Content c=pending.remove(queue.remove());if(c==null)continue;group.add(c);
                for(Direction d:Direction.values())if(pending.containsKey(c.old.relative(d)))queue.add(c.old.relative(d));
            }
            List<BlockPos> candidates=new ArrayList<>();candidates.add(first.old.offset(3,2,3));
            for(int y=FarmCaveLayout.FLOOR;y<=FarmCaveLayout.FLOOR;y++)for(int z=6;z<13;z++)for(int x=4;x<13;x++)candidates.add(new BlockPos(x,y,z));
            BlockPos shift=null;
            for(BlockPos candidate:candidates) {
                BlockPos delta=candidate.subtract(first.old);boolean fits=true;
                for(Content c:group) {
                    BlockPos to=c.old.offset(delta);
                    if(to.getY()<3 || to.getY()>=9 || !FarmCaveLayout.floor(to.getX(),to.getZ())
                            || FarmCaveLayout.reserved(to) || architecture().containsKey(to) || used.contains(to)) {fits=false;break;}
                }
                if(fits){shift=delta;break;}
            }
            if(shift==null)throw new IllegalStateException("Legacy furnishings do not fit; original cave retained at "+e.legacy);
            for(Content c:group){BlockPos to=c.old.offset(shift);moves.add(new Move(c,to));used.add(to);}
        }
        return moves;
    }
    private static void restore(ServerLevel level,BlockPos origin,List<Move> moves) {
        for(Move m:moves)level.setBlock(origin.offset(m.local),m.content.state,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        for(Move m:moves)if(m.content.nbt!=null) {
            BlockPos to=origin.offset(m.local);var be=level.getBlockEntity(to);
            if(be==null)throw new IllegalStateException("Missing migrated block entity at "+to);
            CompoundTag nbt=m.content.nbt.copy();nbt.putInt("x",to.getX());nbt.putInt("y",to.getY());nbt.putInt("z",to.getZ());
            be.loadWithComponents(nbt,level.registryAccess());be.setChanged();level.sendBlockUpdated(to,m.content.state,m.content.state,Block.UPDATE_CLIENTS);
        }
    }
    public static void facilities(ServerLevel level,FarmInstance farm) {
        var data=FarmCaveData.get(level);var e=data.find(farm.getInstanceId());if(e==null)return;
        String choice=farm.getCaveChoice().name();
        if(!choice.equals(e.appliedChoice)) {
            if(farm.getCaveChoice()!=FarmCaveChoice.MUSHROOMS)for(BlockPos off:FarmCaveLayout.BOXES) {
                BlockPos p=e.origin.offset(off);if(level.getBlockState(p).is(ModBlocks.MUSHROOM_BOX.get()))level.removeBlock(p,false);
            }
            if(FarmCaveChoice.FRUIT_BATS.name().equals(e.appliedChoice)
                    || e.appliedChoice.isEmpty() && farm.getCaveChoice()==FarmCaveChoice.MUSHROOMS)
                FarmCaveAPI.clearCaveFruits(level,e.origin);
            e.appliedChoice=choice;data.setDirty();
        }
        if(farm.getCaveChoice()!=FarmCaveChoice.MUSHROOMS)return;
        for(BlockPos off:FarmCaveLayout.BOXES) {
            BlockPos p=e.origin.offset(off);
            if(level.getBlockState(p).isAir())level.setBlock(p,ModBlocks.MUSHROOM_BOX.get().defaultBlockState(),Block.UPDATE_ALL);
        }
        if(!e.dehydratorGranted || !e.installed()) {
            BlockPos p=e.origin.offset(FarmCaveLayout.DEHYDRATOR);
            if(level.getBlockState(p).isAir()) {
                level.setBlock(p,ModBlocks.DEHYDRATOR.get().defaultBlockState(),Block.UPDATE_ALL);
                e.dehydratorGranted=true;data.setDirty();
            }
        }
    }
    private static void build(ServerLevel level,Job job,FarmInstance farm) {
        var e=job.entry;
        if(!e.installed()) {
            List<Move> moves=migration(level,e);
            var template=level.getStructureManager().get(FarmCaveLayout.TEMPLATE).orElseThrow(()->new IllegalStateException("Farm cave template missing"));
            if(template.getSize().getX()!=FarmCaveLayout.WIDTH || template.getSize().getY()!=FarmCaveLayout.HEIGHT || template.getSize().getZ()!=FarmCaveLayout.LENGTH)
                throw new IllegalStateException("Farm cave dimensions mismatch");
            if(!template.placeInWorld(level,e.origin,e.origin,new StructurePlaceSettings().setKnownShape(true),level.random,Block.UPDATE_CLIENTS))
                throw new IllegalStateException("Farm cave placement failed");
            restore(level,e.origin,moves);
        } else {
            // Recover missing fixed rock/floors without replaying air over player content.
            for(var b:architecture().entrySet()) {
                BlockPos p=e.origin.offset(b.getKey());if(level.getBlockState(p).isAir())level.setBlock(p,b.getValue(),Block.UPDATE_CLIENTS);
            }
        }
        // The authored template used to place a lamp directly above the
        // arrival tile.  Keep old installed caves compatible by removing it
        // during every normal cave repair/request as well as from new caves.
        removeSpawnLamp(level,e.origin);
        exitPortal(level,e.origin);facilities(level,farm);
        if(!safe(level,e.origin.offset(FarmCaveLayout.SPAWN)))throw new IllegalStateException("Unsafe cave arrival at "+e.origin);
        job.built=level.getGameTime();
    }
    private static void runDaily(ServerLevel level,Job job) {
        for(var callback:List.copyOf(job.daily.entrySet())) {
            if(callback.getKey()<=job.entry.lastDay){job.daily.remove(callback.getKey());continue;}
            try {
                callback.getValue().run();job.entry.lastDay=callback.getKey();FarmCaveData.get(level).setDirty();
            } catch(RuntimeException ex) { StardewCraft.LOGGER.error("[FARM-CAVE] Daily update failed for {}",job.entry.farmId,ex); }
            job.daily.remove(callback.getKey());
        }
    }
    public static void enter(ServerPlayer player) {
        FarmInstance farm=FarmAreaResolver.getFarmAt(player.blockPosition());
        if(farm==null){message(player,"stardewcraft.farm.not_found");return;}
        // Only the cave mouth of the farm the player is physically visiting selects the target.
        var layout=layoutFor(farm);
        if(layout==null)return;
        var door=layout.cavePortalWall();
        if(door==null)return;
        BlockPos min=farm.getOrigin().offset(door.min()),max=farm.getOrigin().offset(door.max());
        if(!new AABB(Vec3.atLowerCornerOf(min),Vec3.atLowerCornerOf(max.offset(1,1,1))).inflate(3).contains(player.position()))return;
        begin(player,farm,true,false);
    }
    private static FarmCaveData.Entry legacyEntry(ServerLevel level,BlockPos pos) {
        var alloc=PlayerInteriorAllocator.get(level);UUID owner=alloc.findLegacyCaveOwner(pos);if(owner==null)return null;
        BlockPos origin=alloc.getLegacyCaveOrigin(owner);
        for(var e:FarmCaveData.get(level).entries())if(origin.equals(e.legacy))return e;
        FarmInstance f=FarmInstanceRegistry.get().getFarm(owner);
        return f==null?null:FarmCaveData.get(level).allocate(level,f);
    }
    private static void publicExit(ServerPlayer player,UUID retiredFarm,boolean rescue) {
        BlockPos target=com.stardew.craft.event.InteriorPortalInteractionEvents.publicFarmExitTarget();
        Transit previous=TRAVEL.get(player.getUUID());
        if(previous!=null && previous.arrived<0 && previous.target.equals(target))return;
        if(previous!=null)previous.close();
        TRAVEL.put(player.getUUID(),new Transit(player,retiredFarm,false,target,-90,rescue));
    }
    public static void exit(ServerPlayer player) {
        var entry=FarmCaveData.get(player.serverLevel()).atColumn(player.blockPosition());
        if(entry==null)entry=legacyEntry(player.serverLevel(),player.blockPosition());
        if(entry==null){message(player,"stardewcraft.farm.not_found");return;}
        FarmInstance f=farm(entry.farmId);
        if(f==null)publicExit(player,entry.farmId,false);else begin(player,f,false,false);
    }
    private static void begin(ServerPlayer player,FarmInstance farm,boolean entering,boolean rescue) {
        Transit previous=TRAVEL.get(player.getUUID());
        if(previous!=null){if(previous.arrived<0)return;previous.close();TRAVEL.remove(player.getUUID());}
        if(!rescue && player.serverLevel().getGameTime()-player.getPersistentData().getLong(COOLDOWN)<8)return;
        if(entering && !FarmPermissionManager.get().canVisit(farm.getOwnerUUID(),player.getUUID())) {
            if(rescue)begin(player,farm,false,true);else message(player,"stardewcraft.farm.no_access");return;
        }
        BlockPos target;
        StardewFarmLayout layout=layoutFor(farm);
        if(layout==null){message(player,"stardewcraft.farm.not_found");return;}
        if(entering) {request(player.serverLevel(),farm);target=origin(player.serverLevel(),farm).offset(FarmCaveLayout.SPAWN);}
        else {
            // A few pre-cave snapshots have no CaveExit field.  The current
            // registry repairs that case; the south farm entrance is a final
            // safe fallback for custom layouts that never authored one.
            BlockPos exit=layout.caveExitSpawn();
            if(exit==null)exit=layout.entrySouth().teleportOffset();
            target=farm.getOrigin().offset(exit);
        }
        player.closeContainer();player.stopUsingItem();
        float yaw=entering?180:layout.caveExitSpawn()!=null?layout.caveExitYaw():layout.entrySouth().yaw();
        TRAVEL.put(player.getUUID(),new Transit(player,farm.getInstanceId(),entering,target,yaw,rescue));
    }
    private static void message(ServerPlayer p,String key) { p.displayClientMessage(Component.translatable(key),true); }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level))return;
        long now=level.getGameTime();var jobs=JOBS.get(level);int buildBudget=1;
        if(jobs!=null)for(var job:List.copyOf(jobs.values())) {
            FarmInstance f=farm(job.entry.farmId);
            if(f==null || now-job.start>600 || (job.release>=0 && now>=job.release)) {
                job.lease.close();jobs.remove(job.entry.farmId);continue;
            }
            if(job.release>=0 && !job.daily.isEmpty())runDaily(level,job);
            if(job.failed || job.release>=0 || !job.lease.loaded())continue;
            try {
                if(job.built<0){if(buildBudget--<=0)continue;build(level,job,f);}
                if(now>job.built+2 && job.lease.lit()) {
                    job.entry.version=FarmCaveLayout.VERSION;FarmCaveData.get(level).setDirty();job.release=now+60;
                    runDaily(level,job);
                    if(job.entry.legacy!=null)PlayerInteriorAllocator.get(level).releaseLegacyCaveChunks(level,job.entry.legacy);
                }
            } catch(RuntimeException ex) {
                job.failed=true;job.lease.close();
                StardewCraft.LOGGER.error("[FARM-CAVE] Preparation failed for farm {} at {}; no player transferred",job.entry.farmId,job.entry.origin,ex);
            }
        }
        if(jobs!=null && jobs.isEmpty())JOBS.remove(level);
        for(var trip:List.copyOf(TRAVEL.values())) {
            if(trip.level!=level)continue;
            ServerPlayer player=trip.player;
            if(trip.arrived>=0) {
                if(now-trip.arrived>=60 || player.isRemoved()){trip.close();TRAVEL.remove(player.getUUID());}continue;
            }
            FarmInstance f=farm(trip.farmId);
            boolean cancelled=player.isRemoved() || !player.isAlive() || player.serverLevel()!=level || (!trip.rescue && player.position().distanceToSqr(trip.source)>16);
            Job job=JOBS.getOrDefault(level,Map.of()).get(trip.farmId);
            boolean failed=(trip.entering && f==null) || now-trip.start>600 || (trip.entering && job!=null && job.failed);
            if(cancelled || failed) {
                trip.close();TRAVEL.remove(player.getUUID());
                if(failed && !player.isRemoved()) {
                    message(player,"stardewcraft.farm_cave.travel_failed");
                    if(trip.rescue && f!=null)begin(player,f,false,true);
                    else if(f==null && trip.entering)publicExit(player,trip.farmId,trip.rescue);
                }
                continue;
            }
            if(trip.rescue){player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;}
            if(!trip.lease.lit() || (trip.entering && !ready(level,f)))continue;
            if(trip.entering && !FarmPermissionManager.get().canVisit(f.getOwnerUUID(),player.getUUID())) {
                message(player,"stardewcraft.farm.no_access");trip.close();TRAVEL.remove(player.getUUID());continue;
            }
            if(!safe(level,trip.target))continue;
            ModTeleport.to(player,level,trip.target,trip.yaw,0);player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;
            player.getPersistentData().putLong(COOLDOWN,now);player.getPersistentData().putBoolean("stardewcraft_interior_space",trip.entering);
            if(trip.rescue)player.setNoGravity(trip.gravity);
            trip.arrived=now;
        }
    }
    @SubscribeEvent public static void rescue(PlayerTickEvent.Post event) {
        if(!(event.getEntity() instanceof ServerPlayer p) || !p.serverLevel().dimension().equals(ModDimensions.STARDEW_VALLEY) || TRAVEL.containsKey(p.getUUID()))return;
        ServerLevel level=p.serverLevel();
        var e=FarmCaveData.get(level).atColumn(p.blockPosition());
        if(e!=null && (p.getY()<e.origin.getY()+FarmCaveLayout.FLOOR || farm(e.farmId)==null)) {
            FarmInstance f=farm(e.farmId);if(f!=null)begin(p,f,true,true);else publicExit(p,e.farmId,true);return;
        }
        var legacy=legacyEntry(level,p.blockPosition());
        if(legacy!=null) {
            FarmInstance f=farm(legacy.farmId);if(f!=null)begin(p,f,true,true);else publicExit(p,legacy.farmId,true);
        }
    }
    @SubscribeEvent public static void stop(ServerStoppingEvent event) {
        for(var jobs:JOBS.values())for(var job:jobs.values())job.lease.close();JOBS.clear();
        for(var trip:TRAVEL.values())trip.close();TRAVEL.clear();architecture=null;legacyArchitecture=null;
    }
}
