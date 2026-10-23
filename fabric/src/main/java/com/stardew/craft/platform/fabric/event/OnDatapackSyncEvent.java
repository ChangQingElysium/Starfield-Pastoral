package com.stardew.craft.platform.fabric.event;

import java.util.Objects;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

/** Login recipient or the current player list, immediately before native data packets. */
public final class OnDatapackSyncEvent extends Event {
    private final PlayerList playerList;
    private final ServerPlayer player;

    public OnDatapackSyncEvent(PlayerList playerList, @Nullable ServerPlayer player) {
        this.playerList = Objects.requireNonNull(playerList, "playerList");
        this.player = player;
    }

    public PlayerList getPlayerList() { return playerList; }
    @Nullable public ServerPlayer getPlayer() { return player; }
    public Stream<ServerPlayer> getRelevantPlayers() {
        return player == null ? playerList.getPlayers().stream() : Stream.of(player);
    }
}
