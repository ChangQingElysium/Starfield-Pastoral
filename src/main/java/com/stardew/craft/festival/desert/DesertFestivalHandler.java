package com.stardew.craft.festival.desert;

import com.stardew.craft.festival.FestivalDefinition;
import com.stardew.craft.festival.FestivalSessionState;
import com.stardew.craft.festival.PassiveFestivalHandler;
import com.stardew.craft.festival.DesertFestivalBusLayout;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class DesertFestivalHandler implements PassiveFestivalHandler {
    @Override
    public String festivalId() {
        return DesertFestivalService.FESTIVAL_ID;
    }

    @Override
    public void onNewDay(ServerLevel level, FestivalDefinition definition, FestivalSessionState session) {
        DesertFestivalService.forceRefreshNpcSchedules(level);
    }

    @Override
    public void onOpen(ServerLevel level, FestivalDefinition definition, FestivalSessionState session) {
        DesertFestivalBusLayout.repair(level);
        DesertFestivalService.setupFestivalInteractions(level);
        DesertFestivalService.forceRefreshNpcSchedules(level);
        DesertFestivalService.syncCurrencyHud(level.getServer());
    }

    @Override
    public void tick(ServerLevel level, FestivalDefinition definition, FestivalSessionState session) {
        if (level.getGameTime() % 20 == 0) DesertFestivalBusLayout.repair(level);
        DesertFestivalSpecialInteractionService.syncFestivalTravelingCart(level);
        DesertFestivalRaceService.tick(level.getServer());
        DesertFestivalService.syncCurrencyHud(level.getServer());
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            DesertFestivalMakeoverService.tickPlayer(player);
        }
    }

    @Override
    public void onMapOverlayApplied(ServerLevel level, FestivalDefinition definition, FestivalSessionState session) {
        DesertFestivalBusLayout.repair(level);
    }

    @Override
    public void onMapOverlayRestored(ServerLevel level, FestivalDefinition definition, FestivalSessionState session) {
        // An older persisted runtime backup can contain the same retired bus construction.
        DesertFestivalBusLayout.repair(level);
    }

    @Override
    public void onCleanup(ServerLevel level, FestivalDefinition definition, FestivalSessionState session) {
        DesertFestivalService.cleanupFestivalInteractions(level);
        DesertFestivalRaceService.closeForFestivalCleanup(level.getServer());
        DesertFestivalMineService.clearHudForAll(level.getServer());
        DesertFestivalService.clearCurrencyHud(level.getServer());
        DesertFestivalService.cleanupExpiredEggs(level.getServer());
        DesertFestivalService.forceRefreshNpcSchedules(level);
    }
}
