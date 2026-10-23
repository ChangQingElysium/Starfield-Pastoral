package com.stardew.craft.platform.fabric.client.event;

import com.stardew.craft.platform.fabric.event.IEventBus;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public final class FabricClientPlayerEvents {
    private static IEventBus installedBus;
    private FabricClientPlayerEvents() {}

    public static synchronized void install(IEventBus bus) {
        Objects.requireNonNull(bus, "bus");
        if (installedBus == bus) return;
        if (installedBus != null) throw new IllegalStateException("Client player events already have an installed bus");
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> bus.post(new ClientPlayerNetworkEvent.LoggingIn(
                Objects.requireNonNull(client.gameMode, "joined game mode"), Objects.requireNonNull(client.player, "joined player"), handler.getConnection())));
        installedBus = bus;
    }

    public static void loggingOut(Minecraft client) {
        LocalPlayer player = client.player;
        postLoggingOut(client.gameMode, player, player == null ? null : player.connection.getConnection());
    }

    static void postLoggingOut(@Nullable MultiPlayerGameMode gameMode, @Nullable LocalPlayer player, @Nullable Connection connection) {
        bus().post(new ClientPlayerNetworkEvent.LoggingOut(gameMode, player, connection));
    }

    public static void cloned(Minecraft client, LocalPlayer oldPlayer) {
        LocalPlayer player = Objects.requireNonNull(client.player, "respawned player");
        bus().post(new ClientPlayerNetworkEvent.Clone(client.gameMode, oldPlayer, player, player.connection.getConnection()));
    }

    private static IEventBus bus() {
        if (installedBus == null) throw new IllegalStateException("Client player event bus is not installed");
        return installedBus;
    }
}
