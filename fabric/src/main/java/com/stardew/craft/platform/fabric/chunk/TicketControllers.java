package com.stardew.craft.platform.fabric.chunk;

import com.stardew.craft.platform.fabric.event.IEventBus;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;

/** Native owned tickets. Never delegates to setChunkForced or changes the vanilla Forced set. */
public final class TicketControllers {
    private static final TicketType<TicketOwner> TYPE = TicketType.create("stardewcraft:controller", Comparator.<TicketOwner>naturalOrder());
    private static Map<ResourceLocation, TicketController> controllers;
    private static boolean installing;
    private static IEventBus installedBus;
    private TicketControllers() {}

    public static synchronized void install(IEventBus modBus) {
        Objects.requireNonNull(modBus, "modBus");
        if (installedBus == modBus) return;
        if (installing || installedBus != null) throw new IllegalStateException("Ticket controllers already installed or installing");
        installing = true;
        try {
            controllers = collectControllers(modBus);
            ServerWorldEvents.LOAD.register((server, level) -> {
                var saved = level.getDataStorage().get(ForcedChunksSavedData.factory(), "chunks");
                if (saved != null) ensureRestored(level, saved);
            });
            installedBus = modBus;
        } catch (RuntimeException | Error failure) { controllers = null; throw failure; }
    }

    static Map<ResourceLocation, TicketController> collectControllers(IEventBus modBus) {
        var registered = new LinkedHashMap<ResourceLocation, TicketController>();
        var event = new RegisterTicketControllersEvent(controller -> {
            if (registered.putIfAbsent(controller.id(), controller) != null) throw new IllegalArgumentException("Duplicate ticket controller " + controller.id());
        });
        try { modBus.post(event); return Map.copyOf(registered); }
        finally { event.close(); }
    }

    static boolean forceChunk(TicketController controller, ServerLevel level, Object owner, int x, int z, boolean add, boolean ticking) {
        requireInstalled(); Objects.requireNonNull(level, "level");
        if (!controllers.containsKey(controller.id())) throw new IllegalArgumentException("Unregistered ticket controller " + controller.id());
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Chunk tickets must be changed on the server thread");
        var saved = level.getDataStorage().computeIfAbsent(ForcedChunksSavedData.factory(), "chunks");
        var data = ensureRestored(level, saved);
        return apply(data, new TicketOwner(controller.id(), owner, ticking), new ChunkPos(x, z), add, backend(level, saved));
    }

    /** Owned controller tickets only; the caller retains vanilla's independent forced-set predicate. */
    public static boolean hasForcedChunks(ServerLevel level) {
        requireInstalled(); var saved = level.getDataStorage().get(ForcedChunksSavedData.factory(), "chunks");
        return saved != null && ensureRestored(level, saved).hasTickets();
    }
    public static boolean shouldForceTicks(ServerLevel level, long chunk) {
        requireInstalled(); var saved = level.getDataStorage().get(ForcedChunksSavedData.factory(), "chunks");
        return saved != null && ensureRestored(level, saved).shouldForceTicks(chunk);
    }
    private static void requireInstalled() { if (controllers == null) throw new IllegalStateException("Ticket controllers have not been registered"); }

    private static ChunkTicketData ensureRestored(ServerLevel level, ForcedChunksSavedData saved) {
        requireInstalled(); var data = ((ForcedChunkDataAccess) saved).stardewcraft$controllerTickets(); data.requireHealthy();
        if (data.restored()) return data;
        try {
            validate(data, controllers, level, saved::setDirty);
            data.restored(true);
            // Restoring persisted tickets, like Neo's reinstatement pass, schedules native loading without getChunk's synchronous wait.
            var backend = backend(level, saved);
            data.snapshot().forEach((owner, chunks) -> { for (long chunk : chunks) backend.add(new ChunkPos(chunk), owner); });
            return data;
        } catch (RuntimeException | Error failure) { data.failed(); throw failure; }
    }

    static void validate(ChunkTicketData data, Map<ResourceLocation, TicketController> registered, ServerLevel level, Runnable dirty) {
        data.requireHealthy(); if (data.validated()) return;
        try {
            data.prepare(registered.keySet()); data.validated(true);
            registered.forEach((id, controller) -> {
                if (controller.callback() == null) return;
                var helper = new TicketHelper(data, id, dirty);
                if (!helper.getBlockTickets().isEmpty() || !helper.getEntityTickets().isEmpty()) controller.callback().validateTickets(level, helper);
            });
        } catch (RuntimeException | Error failure) { data.failed(); throw failure; }
    }

    interface Backend {
        void load(ChunkPos pos);
        void add(ChunkPos pos, TicketOwner owner);
        void remove(ChunkPos pos, TicketOwner owner);
        void dirty();
    }
    private static Backend backend(ServerLevel level, ForcedChunksSavedData saved) { return new NativeBackend(level, saved); }
    private static final class NativeBackend implements Backend {
        private final ServerLevel level;
        private final ForcedChunksSavedData saved;
        NativeBackend(ServerLevel level, ForcedChunksSavedData saved) { this.level = level; this.saved = saved; }
        @Override public void load(ChunkPos pos) { level.getChunk(pos.x, pos.z); }
        @Override public void add(ChunkPos pos, TicketOwner owner) { level.getChunkSource().addRegionTicket(TYPE, pos, 2, owner); }
        @Override public void remove(ChunkPos pos, TicketOwner owner) { level.getChunkSource().removeRegionTicket(TYPE, pos, 2, owner); }
        @Override public void dirty() { saved.setDirty(); }
    }

    static boolean apply(ChunkTicketData data, TicketOwner owner, ChunkPos pos, boolean add, Backend backend) {
        data.requireHealthy(); long chunk = pos.toLong();
        if (add == data.contains(owner, chunk)) return false;
        if (add) {
            data.add(owner, chunk); boolean attemptedAdd = false;
            try { backend.load(pos); attemptedAdd = true; backend.add(pos, owner); }
            catch (RuntimeException | Error failure) {
                data.remove(owner, chunk);
                if (attemptedAdd) try { backend.remove(pos, owner); }
                catch (RuntimeException | Error rollbackFailure) { data.failed(); failure.addSuppressed(rollbackFailure); }
                backend.dirty(); throw failure;
            }
        } else {
            data.remove(owner, chunk);
            try { backend.remove(pos, owner); }
            catch (RuntimeException | Error failure) {
                data.add(owner, chunk);
                try { backend.add(pos, owner); }
                catch (RuntimeException | Error rollbackFailure) { data.failed(); failure.addSuppressed(rollbackFailure); }
                throw failure;
            }
        }
        backend.dirty(); return true;
    }
}
