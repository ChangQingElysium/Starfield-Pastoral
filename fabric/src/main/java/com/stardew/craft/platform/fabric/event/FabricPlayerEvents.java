package com.stardew.craft.platform.fabric.event;

import java.util.Objects;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Server player lifecycle, with dimension completion published by its exact native hook. */
public final class FabricPlayerEvents {
    private static IEventBus installedBus;

    private FabricPlayerEvents() {}

    public static void install() {
        install(FabricEvents.GAME);
    }

    public static synchronized void install(IEventBus bus) {
        Objects.requireNonNull(bus, "bus");
        if (installedBus == bus) return;
        if (installedBus != null) throw new IllegalStateException("Player events already have an installed bus");
        // JOIN is PlayerList.placeNewPlayer RETURN: world and inventory are ready.
        ServerPlayerEvents.JOIN.register(player -> bus.post(new PlayerEvent.PlayerLoggedInEvent(player)));
        // LEAVE is PlayerList.remove HEAD, before saving/removing the player.
        ServerPlayerEvents.LEAVE.register(player -> bus.post(new PlayerEvent.PlayerLoggedOutEvent(player)));
        // The persistent-data mixin copies PlayerPersisted at restoreFrom HEAD.
        // Fabric's COPY_FROM runs at restoreFrom TAIL, so Clone observes that copy
        // without relying on the order of two separate return-point injections.
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) ->
                bus.post(new PlayerEvent.Clone(newPlayer, oldPlayer, !alive)));
        // alive is the original PlayerList.respawn boolean, also passed by NeoForge
        // as endConquered. Fabric publishes at method TAIL, after respawn-anchor sound.
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                bus.post(new PlayerEvent.PlayerRespawnEvent(newPlayer, alive)));
        // AFTER_PLAYER_CHANGE_WORLD also fires during cross-world respawn and
        // runs within changeDimension's advancement hook before final packets
        // and postDimensionTransition. It is not a matching producer for the
        // existing PlayerChangedDimensionEvent. ServerPlayerDimensionChangeMixin
        // publishes only the completed cross-dimension branch instead.
        installedBus = bus;
    }

    /** Called only by the native completed-transfer hook, after final synchronization. */
    public static void postDimensionChanged(ServerPlayer player, ResourceKey<Level> from, ResourceKey<Level> to) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (!from.equals(to)) {
            FabricEvents.GAME.post(new PlayerEvent.PlayerChangedDimensionEvent(player, from, to));
        }
    }

    public static void postTrackingStarted(net.minecraft.world.entity.Entity entity, ServerPlayer player) {
        FabricEvents.GAME.post(new PlayerEvent.StartTracking(player, entity));
    }

    public static void postTrackingStopped(net.minecraft.world.entity.Entity entity, ServerPlayer player) {
        FabricEvents.GAME.post(new PlayerEvent.StopTracking(player, entity));
    }
}
