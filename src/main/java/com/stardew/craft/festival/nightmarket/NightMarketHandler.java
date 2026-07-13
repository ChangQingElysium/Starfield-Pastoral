package com.stardew.craft.festival.nightmarket;

import com.stardew.craft.festival.FestivalDefinition;
import com.stardew.craft.festival.FestivalSessionState;
import com.stardew.craft.festival.PassiveFestivalHandler;
import com.stardew.craft.network.payload.NightMarketStatePayload;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class NightMarketHandler implements PassiveFestivalHandler {
    private static final Set<UUID> MUSIC_SYNCED_PLAYERS = new HashSet<>();

    @Override
    public String festivalId() {
        return NightMarketPainterService.FESTIVAL_ID;
    }

    @Override
    public void onNewDay(ServerLevel level, FestivalDefinition definition, FestivalSessionState session) {
        NightMarketNpcVisitService.forceRefreshNpcSchedules(level);
    }

    @Override
    public void onOpen(ServerLevel level, FestivalDefinition definition, FestivalSessionState session) {
        install(level);
        NightMarketNpcVisitService.forceRefreshNpcSchedules(level);
    }

    @Override
    public void onMapOverlayApplied(ServerLevel level, FestivalDefinition definition, FestivalSessionState session) {
        install(level);
        NightMarketNpcVisitService.forceRefreshNpcSchedules(level);
    }

    @Override
    public void onMapOverlayRestoreStarted(ServerLevel level, FestivalDefinition definition, FestivalSessionState session) {
        cleanup(level);
    }

    @Override
    public void onCleanup(ServerLevel level, FestivalDefinition definition, FestivalSessionState session) {
        cleanup(level);
        NightMarketNpcVisitService.forceRefreshNpcSchedules(level);
    }

    private static void install(ServerLevel level) {
        NightMarketPainterService.install(level);
        NightMarketCoffeeService.install(level);
        NightMarketWarperService.install(level);
        NightMarketShopService.install(level);
        NightMarketSubmarineService.install(level);
        NightMarketMermaidService.install(level);
        syncMusicOpen(level);
    }

    private static void cleanup(ServerLevel level) {
        NightMarketPainterService.cleanup(level);
        NightMarketCoffeeService.cleanup(level);
        NightMarketWarperService.cleanup(level);
        NightMarketShopService.cleanup(level);
        NightMarketSubmarineService.cleanup(level);
        NightMarketMermaidService.cleanup(level);
        PacketDistributor.sendToPlayersInDimension(level, new NightMarketStatePayload(false));
        MUSIC_SYNCED_PLAYERS.clear();
    }

    @Override
    public void tick(ServerLevel level, FestivalDefinition definition, FestivalSessionState session) {
        syncMusicOpen(level);
        NightMarketMermaidService.tick(level);
    }

    private static void syncMusicOpen(ServerLevel level) {
        Set<UUID> onlinePlayers = new HashSet<>();
        level.players().forEach(player -> onlinePlayers.add(player.getUUID()));
        MUSIC_SYNCED_PLAYERS.retainAll(onlinePlayers);
        level.players().forEach(player -> {
            if (MUSIC_SYNCED_PLAYERS.add(player.getUUID())) {
                PacketDistributor.sendToPlayer(player, new NightMarketStatePayload(true));
            }
        });
    }
}
