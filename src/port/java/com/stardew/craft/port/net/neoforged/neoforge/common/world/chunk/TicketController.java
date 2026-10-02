package com.stardew.craft.port.net.neoforged.neoforge.common.world.chunk;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.world.ForgeChunkManager;
import org.jetbrains.annotations.Nullable;

/**
 * PORT(1.20.1): NeoForge ticket controller on {@link ForgeChunkManager}. Forge keys forced-chunk tickets by mod id,
 * so tickets of all controllers in one namespace share a ticket set and every controller's validation callback
 * sees all of them (see docs/porting/bulk-port-gaps.md).
 */
public final class TicketController {
    private final ResourceLocation id;
    @Nullable
    private final ForgeChunkManager.LoadingValidationCallback callback;

    public TicketController(ResourceLocation id, @Nullable ForgeChunkManager.LoadingValidationCallback callback) {
        this.id = Objects.requireNonNull(id);
        this.callback = callback;
    }

    public TicketController(ResourceLocation id) {
        this(id, null);
    }

    public ResourceLocation id() {
        return id;
    }

    @Nullable
    public ForgeChunkManager.LoadingValidationCallback callback() {
        return callback;
    }

    public boolean forceChunk(ServerLevel level, BlockPos owner, int chunkX, int chunkZ, boolean add, boolean ticking) {
        return ForgeChunkManager.forceChunk(level, id.getNamespace(), owner, chunkX, chunkZ, add, ticking);
    }

    public boolean forceChunk(ServerLevel level, Entity owner, int chunkX, int chunkZ, boolean add, boolean ticking) {
        return ForgeChunkManager.forceChunk(level, id.getNamespace(), owner, chunkX, chunkZ, add, ticking);
    }

    public boolean forceChunk(ServerLevel level, UUID owner, int chunkX, int chunkZ, boolean add, boolean ticking) {
        return ForgeChunkManager.forceChunk(level, id.getNamespace(), owner, chunkX, chunkZ, add, ticking);
    }

    @Override
    public String toString() {
        return "TicketController{" + id + "}";
    }
}
