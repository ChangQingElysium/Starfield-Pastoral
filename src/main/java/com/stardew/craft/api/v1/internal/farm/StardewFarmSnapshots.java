package com.stardew.craft.api.v1.internal.farm;

import com.stardew.craft.api.v1.farm.StardewFarmSnapshot;
import com.stardew.craft.farm.FarmInstance;

import java.util.Objects;

/** Core snapshot bridge. Not part of the public compatibility surface. */
public final class StardewFarmSnapshots {
    private StardewFarmSnapshots() {
    }

    public static StardewFarmSnapshot from(FarmInstance farm) {
        Objects.requireNonNull(farm, "farm");
        return new StardewFarmSnapshot(
                farm.getOwnerUUID(),
                farm.getOwnerName(),
                farm.getFarmName(),
                farm.getSlotIndex(),
                farm.getOrigin(),
                farm.getFarmLayoutId(),
                farm.getFarmLayoutVersion(),
                farm.getFarmLayoutConfiguration(),
                farm.getFarmLayoutAttachments(),
                farm.isInitialized(),
                farm.getCreatedTimestamp(),
                farm.getLastOnlineDay(),
                farm.getLastOnlineSeason(),
                farm.getGraceDaysLeft(),
                farm.getCaveChoice().getName(),
                farm.hasGoldClock(),
                farm.isGoldClockEnabled(),
                farm.getMembers()
        );
    }
}
