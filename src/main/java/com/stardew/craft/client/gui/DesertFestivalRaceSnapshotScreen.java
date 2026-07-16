package com.stardew.craft.client.gui;

import com.stardew.craft.network.payload.DesertFestivalRaceSnapshot;

public interface DesertFestivalRaceSnapshotScreen extends StardewRealtimeScreen {
    void updateSnapshot(DesertFestivalRaceSnapshot snapshot);
}
