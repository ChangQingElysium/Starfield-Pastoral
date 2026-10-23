package com.stardew.craft.platform.fabric.client.event;

import com.stardew.craft.platform.fabric.event.Event;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class ClientPlayerNetworkEvent extends Event {
    private final MultiPlayerGameMode gameMode;
    private final LocalPlayer player;
    private final Connection connection;

    protected ClientPlayerNetworkEvent(MultiPlayerGameMode gameMode, LocalPlayer player, Connection connection) {
        this.gameMode = gameMode;
        this.player = player;
        this.connection = connection;
    }

    public MultiPlayerGameMode getMultiPlayerGameMode() { return gameMode; }
    public LocalPlayer getPlayer() { return player; }
    public Connection getConnection() { return connection; }

    public static final class LoggingIn extends ClientPlayerNetworkEvent {
        public LoggingIn(MultiPlayerGameMode gameMode, LocalPlayer player, Connection connection) { super(gameMode, player, connection); }
    }

    public static final class LoggingOut extends ClientPlayerNetworkEvent {
        public LoggingOut(@Nullable MultiPlayerGameMode gameMode, @Nullable LocalPlayer player, @Nullable Connection connection) { super(gameMode, player, connection); }
        @Override public @Nullable MultiPlayerGameMode getMultiPlayerGameMode() { return super.getMultiPlayerGameMode(); }
        @Override public @Nullable LocalPlayer getPlayer() { return super.getPlayer(); }
        @Override public @Nullable Connection getConnection() { return super.getConnection(); }
    }

    public static final class Clone extends ClientPlayerNetworkEvent {
        private final LocalPlayer oldPlayer;
        public Clone(MultiPlayerGameMode gameMode, LocalPlayer oldPlayer, LocalPlayer newPlayer, Connection connection) {
            super(gameMode, newPlayer, connection);
            this.oldPlayer = oldPlayer;
        }
        public LocalPlayer getOldPlayer() { return oldPlayer; }
        public LocalPlayer getNewPlayer() { return getPlayer(); }
    }
}
