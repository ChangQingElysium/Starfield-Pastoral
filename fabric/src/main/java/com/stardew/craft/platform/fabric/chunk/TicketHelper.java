package com.stardew.craft.platform.fabric.chunk;

import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public final class TicketHelper {
    private final ChunkTicketData data;
    private final ResourceLocation controller;
    private final Runnable dirty;
    private final Map<BlockPos, TicketSet> blockTickets;
    private final Map<UUID, TicketSet> entityTickets;
    TicketHelper(ChunkTicketData data, ResourceLocation controller, Runnable dirty) {
        this.data = data; this.controller = controller; this.dirty = dirty;
        blockTickets = data.ownerSnapshot(controller, BlockPos.class); entityTickets = data.ownerSnapshot(controller, UUID.class);
    }
    public Map<BlockPos, TicketSet> getBlockTickets() { return blockTickets; }
    public Map<UUID, TicketSet> getEntityTickets() { return entityTickets; }
    public void removeAllTickets(BlockPos owner) { removeAll(owner); }
    public void removeAllTickets(UUID owner) { removeAll(owner); }
    private void removeAll(Object owner) { if (data.removeAll(controller, owner)) dirty.run(); }
    public void removeTicket(BlockPos owner, long chunk, boolean ticking) { remove(owner, chunk, ticking); }
    public void removeTicket(UUID owner, long chunk, boolean ticking) { remove(owner, chunk, ticking); }
    private void remove(Object owner, long chunk, boolean ticking) { if (data.remove(new TicketOwner(controller, owner, ticking), chunk)) dirty.run(); }
}
