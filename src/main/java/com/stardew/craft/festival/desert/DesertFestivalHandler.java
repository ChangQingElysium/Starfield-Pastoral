package com.stardew.craft.festival.desert;

import com.stardew.craft.festival.FestivalDefinition;
import com.stardew.craft.festival.FestivalSessionState;
import com.stardew.craft.festival.PassiveFestivalHandler;
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
        DesertFestivalService.setupFestivalInteractions(level);
        DesertFestivalService.forceRefreshNpcSchedules(level);
        DesertFestivalService.syncCurrencyHud(level.getServer());
    }

    @Override
    public void tick(ServerLevel level, FestivalDefinition definition, FestivalSessionState session) {
        DesertFestivalSpecialInteractionService.syncFestivalTravelingCart(level);
        DesertFestivalRaceService.tick(level.getServer());
        DesertFestivalService.syncCurrencyHud(level.getServer());
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            DesertFestivalMakeoverService.tickPlayer(player);
        }
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
