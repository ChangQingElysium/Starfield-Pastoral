package com.stardew.craft.api.v1.communitycenter;

import com.stardew.craft.communitycenter.data.BundleDefinition;
import com.stardew.craft.communitycenter.state.CommunityCenterSavedData;
import com.stardew.craft.api.v1.internal.communitycenter.StardewCommunityCenterVariantRegistry;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;

/** Read-only, deeply immutable Community Center progress snapshots. */
public final class StardewCommunityCenterProgress {
    private StardewCommunityCenterProgress() {
    }

    public static Snapshot snapshot(ServerLevel level, UUID playerId) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(playerId, "playerId");
        CommunityCenterSavedData data = CommunityCenterSavedData.get(level);
        Map<Integer, boolean[]> storedSlots = data.getBundleSlotsView(playerId);

        TreeSet<Integer> bundleIds = new TreeSet<>(storedSlots.keySet());
        for (BundleDefinition definition
                : StardewCommunityCenterVariantRegistry.all(playerId)) {
            bundleIds.add(definition.bundleId());
        }

        ArrayList<BundleProgress> bundles = new ArrayList<>(bundleIds.size());
        for (int bundleId : bundleIds) {
            boolean[] slots = storedSlots.get(bundleId);
            ArrayList<Boolean> slotSnapshot = new ArrayList<>(
                    slots == null ? 0 : slots.length);
            if (slots != null) {
                for (boolean slot : slots) {
                    slotSnapshot.add(slot);
                }
            }
            bundles.add(new BundleProgress(
                    bundleId,
                    slotSnapshot,
                    data.isBundleComplete(playerId, bundleId),
                    data.isRewardAvailable(playerId, bundleId)
            ));
        }
        bundles.sort(Comparator.comparingInt(BundleProgress::bundleId));

        LinkedHashMap<Integer, Boolean> areas = new LinkedHashMap<>();
        for (int areaId = 0; areaId < 7; areaId++) {
            areas.put(areaId, data.isAreaComplete(playerId, areaId));
        }
        return new Snapshot(playerId, bundles, areas, data.areAllAreasComplete(playerId));
    }

    public record Snapshot(
            UUID playerId,
            List<BundleProgress> bundles,
            Map<Integer, Boolean> areasComplete,
            boolean allMainAreasComplete
    ) {
        public Snapshot {
            playerId = Objects.requireNonNull(playerId, "playerId");
            bundles = List.copyOf(bundles);
            areasComplete = Map.copyOf(areasComplete);
        }
    }

    public record BundleProgress(
            int bundleId,
            List<Boolean> slots,
            boolean complete,
            boolean rewardAvailable
    ) {
        public BundleProgress {
            slots = List.copyOf(slots);
        }

        public int filledSlots() {
            int count = 0;
            for (boolean slot : slots) {
                if (slot) {
                    count++;
                }
            }
            return count;
        }
    }
}
