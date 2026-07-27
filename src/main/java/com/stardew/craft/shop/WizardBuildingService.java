package com.stardew.craft.shop;

import com.stardew.craft.api.v1.building.StardewBuildingBlueprints;
import com.stardew.craft.api.v1.building.StardewBuildingBuilders;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.world.WizardBuildingCatalogService;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Wizard construction catalogue data transcribed from vanilla Data/Buildings. */
public final class WizardBuildingService {
    public static final String BUILDER_ID = "Wizard";
    private WizardBuildingService() {
    }

    /** @deprecated use {@link StardewBuildingBlueprints#forBuilder}. */
    @Deprecated(forRemoval = false)
    public static List<CarpenterBlueprint> getBlueprints(ServerPlayer player) {
        return StardewBuildingBlueprints.forBuilder(
                        player, StardewBuildingBuilders.WIZARD).stream()
                .map(CarpenterBlueprint::from)
                .toList();
    }

    public static void open(ServerPlayer player) {
        if (!canUse(player)) {
            return;
        }

        StardewBuildingBlueprints.open(
                player, StardewBuildingBuilders.WIZARD);
    }

    public static boolean canUse(ServerPlayer player) {
        return player.level().dimension() == ModDimensions.STARDEW_VALLEY
                && player.distanceToSqr(
                        WizardBuildingCatalogService.CATALOG_POS.getX() + 0.5D,
                        WizardBuildingCatalogService.CATALOG_POS.getY() + 0.5D,
                        WizardBuildingCatalogService.CATALOG_POS.getZ() + 0.5D) <= 64.0D
                && PlayerDataManager.getPlayerData(player)
                        .hasMailFlag(WizardBuildingCatalogService.UNLOCK_FLAG);
    }

}
