package com.stardew.craft.interior;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import com.stardew.craft.port.net.neoforged.neoforge.common.world.chunk.TicketController;
import java.util.*;

/** Portal placement never changes vanilla /forceload or another system's ticket. */
@SuppressWarnings("removal")
@EventBusSubscriber(modid=StardewCraft.MODID,bus=EventBusSubscriber.Bus.MOD)
public final class InteriorPortalTickets {
    // Keep the old controller registered only to discard persisted leases from older saves.
    private static final TicketController TICKETS=new TicketController(new ResourceLocation(StardewCraft.MODID,"portal_placement"),
            (level,helper)->{helper.getBlockTickets().keySet().forEach(helper::removeAllTickets);helper.getEntityTickets().keySet().forEach(helper::removeAllTickets);});
    private static final TicketType<BlockPos> PLACEMENT=TicketType.create("stardewcraft_portal_placement",BlockPos::compareTo);
    private record Lease(BlockPos owner,int x,int z) {}
    private static final Map<ServerLevel,Set<Lease>> HELD=new IdentityHashMap<>();
    private InteriorPortalTickets() {}
    @SubscribeEvent public static void register(RegisterTicketControllersEvent event) {event.register(TICKETS);}
    static void request(ServerLevel level,BlockPos owner,int x,int z) {
        var lease=new Lease(owner.immutable(),x,z);
        // TicketController.forceChunk calls level.getChunk synchronously before adding its ticket.
        // Register a transient FULL ticket directly; the regular chunk pipeline loads it on later ticks.
        if(HELD.computeIfAbsent(level,ignored->new HashSet<>()).add(lease))
            level.getChunkSource().addRegionTicket(PLACEMENT,new ChunkPos(x,z),0,lease.owner);
    }
    static void release(ServerLevel level,BlockPos owner) {
        var leases=HELD.get(level);if(leases==null)return;
        for(var lease:List.copyOf(leases)) if(lease.owner.equals(owner)) {
            level.getChunkSource().removeRegionTicket(PLACEMENT,new ChunkPos(lease.x,lease.z),0,lease.owner);leases.remove(lease);
        }
        if(leases.isEmpty()) HELD.remove(level);
    }
    static void clear() {
        for(var entry:Map.copyOf(HELD).entrySet()) for(var lease:List.copyOf(entry.getValue()))
            release(entry.getKey(),lease.owner);
    }
}
