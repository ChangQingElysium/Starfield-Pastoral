package com.stardew.craft.npc.runtime;

import com.stardew.craft.StardewCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import com.stardew.craft.port.net.neoforged.neoforge.common.world.chunk.TicketController;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Owned MinecraftForge tickets: never modifies vanilla /forceload or another module's tickets. */
@SuppressWarnings("removal")
@EventBusSubscriber(modid=StardewCraft.MODID,bus=EventBusSubscriber.Bus.MOD)
public final class NpcChunkForceManager {
    private static final TicketController TICKETS=new TicketController(
            new ResourceLocation(StardewCraft.MODID,"npc_navigation"),
            (level,helper)->{
                // Navigation is reconstructed from logical actor state, so persisted leases are stale.
                helper.getEntityTickets().keySet().forEach(helper::removeAllTickets);
                helper.getBlockTickets().keySet().forEach(helper::removeAllTickets);
            });
    private static final Map<ServerLevel,Map<String,Lease>> LEVELS=new IdentityHashMap<>();
    private NpcChunkForceManager() {}
    @SubscribeEvent public static void register(RegisterTicketControllersEvent event) { event.register(TICKETS); }

    public static void resetState() {
        for (ServerLevel level:java.util.List.copyOf(LEVELS.keySet())) releaseAllForcedChunks(level);
    }
    public static void releaseAllForcedChunks(ServerLevel level) {
        Map<String,Lease> leases=LEVELS.remove(level);
        if (leases!=null) leases.forEach((id,lease)->apply(level,id,lease,Set.of()));
    }
    public static void ensureRouteTargetChunkForced(ServerLevel level,String npcId,Vec3 target) {
        if (level==null || npcId==null || npcId.isBlank() || target==null) return;
        Lease lease=lease(level,npcId);
        long key=chunk(target);
        if (lease.target!=null && lease.target==key) return;
        lease.target=key;
        update(level,npcId,lease);
    }
    public static void ensureResidentChunkForced(ServerLevel level,String npcId,Vec3 position) {
        if (level==null || npcId==null || npcId.isBlank() || position==null) return;
        Lease lease=lease(level,npcId);
        long key=chunk(position);
        if (lease.resident!=null && lease.resident==key) return;
        lease.resident=key;
        update(level,npcId,lease);
    }
    public static String currentForcedTargetChunk(ServerLevel level,String npcId) {
        Map<String,Lease> leases=LEVELS.get(level);
        Lease lease=leases==null?null:leases.get(npcId);
        return lease==null || lease.target==null ? "<none>" : (int)(lease.target>>32)+","+(int)(long)lease.target;
    }
    public static void ensureRouteCorridorChunksForced(ServerLevel level,String npcId,Vec3 from,Vec3 to) {
        if (level==null || npcId==null || from==null || to==null) return;
        Lease lease=lease(level,npcId);
        long start=chunk(from),end=chunk(to);
        int radius=NpcNavigationPolicy.current().corridorRadius();
        if (start==lease.start && end==lease.end && radius==lease.radius) return;
        lease.start=start; lease.end=end; lease.radius=radius;
        int x=(int)(start>>32),z=(int)start,tx=(int)(end>>32),tz=(int)end;
        Set<Long> corridor=new HashSet<>();
        // Load a bounded moving window for long trips; data-defined intermediate points can
        // describe detours. The destination has a separate lease and isn't the window origin.
        int steps=Math.max(Math.abs(tx-x),Math.abs(tz-z));
        int window=Math.min(steps,8);
        for (int i=0;i<=window;i++) {
            int cx=x+(int)Math.round((tx-x)*(steps==0?0:(double)i/steps));
            int cz=z+(int)Math.round((tz-z)*(steps==0?0:(double)i/steps));
            for (int dx=-radius;dx<=radius;dx++) for (int dz=-radius;dz<=radius;dz++)
                corridor.add(pack(cx+dx,cz+dz));
        }
        lease.corridor=Set.copyOf(corridor);
        update(level,npcId,lease);
    }
    public static void releaseRouteCorridor(ServerLevel level,String npcId) {
        Map<String,Lease> leases=LEVELS.get(level);
        Lease lease=leases==null?null:leases.get(npcId);
        if (lease==null || lease.corridor.isEmpty()) return;
        lease.corridor=Set.of(); lease.start=Long.MIN_VALUE; lease.end=Long.MIN_VALUE;
        update(level,npcId,lease);
    }
    public static void releaseInactiveForcedChunks(ServerLevel level,Set<String> activeNpcIds) {
        Map<String,Lease> leases=LEVELS.get(level);
        if (leases==null) return;
        for (String id:java.util.List.copyOf(leases.keySet()))
            if (!activeNpcIds.contains(id)) releaseNpcForcedChunks(level,id);
    }
    public static void releaseNpcForcedChunks(ServerLevel level,String npcId) {
        Map<String,Lease> leases=LEVELS.get(level);
        Lease lease=leases==null?null:leases.remove(npcId);
        if (lease!=null) apply(level,npcId,lease,Set.of());
    }
    private static Lease lease(ServerLevel level,String id) {
        return LEVELS.computeIfAbsent(level,ignored->new HashMap<>()).computeIfAbsent(id,ignored->new Lease());
    }
    private static void update(ServerLevel level,String id,Lease lease) {
        Set<Long> next=new HashSet<>(lease.corridor);
        if (lease.target!=null) next.add(lease.target);
        if (lease.resident!=null) next.add(lease.resident);
        apply(level,id,lease,next);
    }
    private static void apply(ServerLevel level,String id,Lease lease,Set<Long> next) {
        UUID owner=UUID.nameUUIDFromBytes(("stardewcraft:npc/"+id).getBytes(StandardCharsets.UTF_8));
        for (long key:next) if (!lease.held.contains(key))
            TICKETS.forceChunk(level,owner,(int)(key>>32),(int)key,true,true);
        for (long key:lease.held) if (!next.contains(key))
            TICKETS.forceChunk(level,owner,(int)(key>>32),(int)key,false,true);
        lease.held=Set.copyOf(next);
    }
    private static long chunk(Vec3 p) { return pack(((int)Math.floor(p.x))>>4,((int)Math.floor(p.z))>>4); }
    private static long pack(int x,int z) { return ((long)x<<32)|(z&0xffffffffL); }
    private static final class Lease {
        Long target;
        Long resident;
        long start=Long.MIN_VALUE,end=Long.MIN_VALUE;
        int radius=-1;
        Set<Long> corridor=Set.of(),held=Set.of();
    }
}
