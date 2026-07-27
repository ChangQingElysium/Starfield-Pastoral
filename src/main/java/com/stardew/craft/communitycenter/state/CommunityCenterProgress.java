package com.stardew.craft.communitycenter.state;

import com.stardew.craft.communitycenter.data.BundleDataManager;
import com.stardew.craft.communitycenter.data.BundleDefinition;
import com.stardew.craft.api.v1.internal.communitycenter.StardewCommunityCenterVariantRegistry;

import java.util.List;
import java.util.UUID;

/**
 * Read-only query API for Community Center progress.
 * Wraps {@link CommunityCenterSavedData} with higher-level queries
 * that mirror SDV's CommunityCenter logic.
 * <p>
 * 所有方法现在需要传入玩家 UUID 以获取该玩家的独立进度。
 */
public final class CommunityCenterProgress {

    private CommunityCenterProgress() {}

    // ── Area Unlock Logic ──

    /**
     * Whether the Junimo Note (scroll) should appear in a given area.
     * Determines room unlock order based on total completed bundles.
     */
    public static boolean shouldNoteAppearInArea(int areaId, UUID player) {
        CommunityCenterSavedData data = CommunityCenterSavedData.get();
        if (data.isAreaComplete(player, areaId)) return false;

        int completedBundles = data.numberOfCompleteBundles(player);
        return switch (areaId) {
            case 1 -> true;                    // Crafts Room: always visible
            case 0, 2 -> completedBundles > 0; // Pantry, Fish Tank: after 1st bundle
            case 3 -> completedBundles > 1;    // Boiler Room: after 2nd bundle
            case 5 -> completedBundles > 2;    // Bulletin Board: after 3rd bundle
            case 4 -> completedBundles > 3;    // Vault: after 4th bundle
            case 6 -> false;                   // Abandoned Joja: requires event check
            default -> false;
        };
    }

    /**
     * Check if all bundles within an area are complete.
     */
    public static boolean areAllBundlesInAreaComplete(int areaId, UUID player) {
        CommunityCenterSavedData data = CommunityCenterSavedData.get();
        List<BundleDefinition> bundles =
                StardewCommunityCenterVariantRegistry.area(player, areaId);
        if (bundles.isEmpty()) return false;

        for (BundleDefinition def : bundles) {
            if (!data.isBundleComplete(player, def.bundleId())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get the area completion reward mail flag.
     */
    public static String getAreaRewardMailFlag(int areaId) {
        return switch (areaId) {
            case 0 -> "ccPantry";        // Greenhouse repair
            case 1 -> "ccCraftsRoom";    // Quarry bridge
            case 2 -> "ccFishTank";      // Glittering Boulder / Panning
            case 3 -> "ccBoilerRoom";    // Minecarts
            case 4 -> "ccVault";         // Desert bus
            case 5 -> "ccBulletin";      // Friendship reward
            default -> "";
        };
    }

    /**
     * Get a summary string for debug display.
     */
    public static String getDebugSummary(UUID player) {
        CommunityCenterSavedData data = CommunityCenterSavedData.get();
        StringBuilder sb = new StringBuilder();
        sb.append("Community Center Progress (").append(player).append("):\n");

        for (int areaId = 0; areaId <= 6; areaId++) {
            String areaName = BundleDataManager.getAreaName(areaId);
            if (areaName == null) continue;

            boolean complete = data.isAreaComplete(player, areaId);
            List<BundleDefinition> bundles =
                    StardewCommunityCenterVariantRegistry.area(player, areaId);

            sb.append(String.format("  [%s] %s (%d/%d bundles)%s\n",
                    complete ? "\u2713" : " ",
                    areaName,
                    bundles.stream().filter(b -> data.isBundleComplete(player, b.bundleId())).count(),
                    bundles.size(),
                    shouldNoteAppearInArea(areaId, player) ? " [UNLOCKED]" : ""));

            for (BundleDefinition def : bundles) {
                boolean bundleDone = data.isBundleComplete(player, def.bundleId());
                int filled = data.countFilledSlots(player, def.bundleId());
                sb.append(String.format("    [%s] %s (%d/%d slots, need %d)\n",
                        bundleDone ? "\u2713" : " ",
                        def.internalName(),
                        filled,
                        def.totalSlots(),
                        def.requiredCount()));
            }
        }

        sb.append(String.format("  Total: %d bundles complete, all areas: %s\n",
                data.numberOfCompleteBundles(player),
                data.areAllAreasComplete(player) ? "YES" : "NO"));

        return sb.toString();
    }
}
