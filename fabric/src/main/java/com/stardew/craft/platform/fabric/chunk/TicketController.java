package com.stardew.craft.platform.fabric.chunk;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public record TicketController(ResourceLocation id, LoadingValidationCallback callback) {
    public TicketController { Objects.requireNonNull(id, "id"); }
    public TicketController(ResourceLocation id) { this(id, null); }
    public boolean forceChunk(ServerLevel level, BlockPos owner, int x, int z, boolean add, boolean ticking) {
        return TicketControllers.forceChunk(this, level, owner, x, z, add, ticking);
    }
    public boolean forceChunk(ServerLevel level, UUID owner, int x, int z, boolean add, boolean ticking) {
        return TicketControllers.forceChunk(this, level, owner, x, z, add, ticking);
    }
    public boolean forceChunk(ServerLevel level, Entity owner, int x, int z, boolean add, boolean ticking) {
        return forceChunk(level, owner.getUUID(), x, z, add, ticking);
    }
}
