package com.stardew.craft.communitycenter.data;

import com.stardew.craft.api.v1.internal.communitycenter.StardewCommunityCenterVariantRegistry;
import com.stardew.craft.communitycenter.state.CommunityCenterSavedData;

import java.util.UUID;

/**
 * SDV parity: {@code CommunityCenter.shouldNoteAppearInArea(int)}.
 * An area is visible to the player when:
 *  - it has at least one bundle defined, AND
 *  - it is NOT already fully complete, AND
 *  - the CC progress threshold for that area is met.
 *
 * Thresholds (SDV):
 *   area 1 (Crafts Room) → always
 *   area 0 (Pantry) / 2 (Fish Tank) → after 1 bundle complete
 *   area 3 (Boiler Room) → after 2 bundles complete
 *   area 5 (Bulletin Board) → after 3 bundles complete
 *   area 4 (Vault) → after 4 bundles complete
 */
public final class BundleAreaVisibility {

    private BundleAreaVisibility() {}

    public static boolean shouldNoteAppearInArea(UUID player, int area) {
        if (!hasAnyBundle(player, area)) return false;

        CommunityCenterSavedData data = CommunityCenterSavedData.get();
        if (data.isAreaComplete(player, area)) return false;

        int completeBundles = countCompleteBundles(data, player);
        return switch (area) {
            case 1 -> true;
            case 0, 2 -> completeBundles > 0;
            case 3 -> completeBundles > 1;
            case 5 -> completeBundles > 2;
            case 4 -> completeBundles > 3;
            default -> false;
        };
    }

    private static boolean hasAnyBundle(UUID player, int area) {
        for (BundleDefinition def
                : StardewCommunityCenterVariantRegistry.area(player, area)) {
            if (def != null) return true;
        }
        return false;
    }

    private static int countCompleteBundles(CommunityCenterSavedData data, UUID player) {
        int n = 0;
        for (int a = 0; a < 6; a++) {
            for (BundleDefinition def
                    : StardewCommunityCenterVariantRegistry.area(player, a)) {
                if (def != null && data.isBundleComplete(player, def.bundleId())) n++;
            }
        }
        return n;
    }
}
