package com.stardew.craft.api.v1.internal.festival;

import com.stardew.craft.api.v1.festival.StardewFestivalClientSessionSnapshot;
import com.stardew.craft.api.v1.festival.StardewFestivalSessionEvent;
import com.stardew.craft.api.v1.festival.StardewFestivalSessionSnapshot;
import com.stardew.craft.api.v1.festival.StardewFestivalSessions;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.festival.FestivalWorldData;
import com.stardew.craft.network.payload.FestivalSessionsSyncPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Builds and distributes bounded, player-specific festival session views. */
public final class StardewFestivalSessionSyncService {
    private static final Map<MinecraftServer, SyncState> SERVER_STATES =
            new WeakHashMap<>();

    private StardewFestivalSessionSyncService() {
    }

    public static void onSessionChanged(
            StardewFestivalSessionEvent event
    ) {
        MinecraftServer server = event.level().getServer();
        SyncState syncState = stateFor(server);
        long revision = syncState.revision().incrementAndGet();
        for (ServerPlayer player : server
                .getPlayerList().getPlayers()) {
            syncToPlayer(
                    player,
                    event.level(),
                    syncState.serverEpoch(),
                    revision);
        }
    }

    public static void syncToPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ServerLevel level = canonicalLevel(player.getServer());
        if (level == null) {
            return;
        }
        SyncState syncState = stateFor(player.getServer());
        syncToPlayer(
                player,
                level,
                syncState.serverEpoch(),
                syncState.revision().get());
    }

    public static List<StardewFestivalClientSessionSnapshot> snapshotFor(
            ServerLevel level,
            ServerPlayer player
    ) {
        if (level == null || player == null) {
            return List.of();
        }
        return FestivalWorldData.get(level).sessions().stream()
                .map(StardewFestivalSessions::snapshot)
                .sorted(Comparator
                        .comparing((StardewFestivalSessionSnapshot session) ->
                                session.phase()
                                        == StardewFestivalSessionSnapshot
                                                .Phase.CLOSED)
                        .thenComparing(
                                StardewFestivalSessionSnapshot::year,
                                Comparator.reverseOrder())
                        .thenComparing(
                                StardewFestivalSessionSnapshot::season,
                                Comparator.reverseOrder())
                        .thenComparing(
                                StardewFestivalSessionSnapshot::day,
                                Comparator.reverseOrder())
                        .thenComparing(session ->
                                session.festivalId().toString()))
                .limit(FestivalSessionsSyncPayload.MAX_SESSIONS)
                .map(session -> new StardewFestivalClientSessionSnapshot(
                        session.festivalId(),
                        session.runtimeId(),
                        session.year(),
                        session.season(),
                        session.day(),
                        session.phase(),
                        session.mapPhase(),
                        session.participants().size(),
                        session.participants().contains(
                                player.getUUID())))
                .toList();
    }

    private static void syncToPlayer(
            ServerPlayer player,
            ServerLevel level,
            UUID serverEpoch,
            long revision
    ) {
        PacketDistributor.sendToPlayer(
                player,
                new FestivalSessionsSyncPayload(
                        serverEpoch,
                        revision,
                        snapshotFor(level, player)));
    }

    private static synchronized SyncState stateFor(
            MinecraftServer server
    ) {
        return SERVER_STATES.computeIfAbsent(
                server,
                ignored -> new SyncState(
                        UUID.randomUUID(), new AtomicLong()));
    }

    private static ServerLevel canonicalLevel(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        ServerLevel level = server.getLevel(ModDimensions.STARDEW_VALLEY);
        return level != null ? level : server.overworld();
    }

    private record SyncState(
            UUID serverEpoch,
            AtomicLong revision
    ) {
    }
}
