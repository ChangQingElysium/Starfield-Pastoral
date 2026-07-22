package com.stardew.craft.shop;

import com.stardew.craft.network.payload.OpenCarpenterMenuPayload;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.world.WizardBuildingCatalogService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Wizard construction catalogue data transcribed from vanilla Data/Buildings. */
public final class WizardBuildingService {
    public static final String BUILDER_ID = "Wizard";
    private static final String VISITED_ISLAND_FLAG = "Visited_Island";

    private static final List<CarpenterBlueprint> BLUEPRINTS = List.of(
        magical(
            "Junimo Hut", "junimo_hut", 20_000,
            List.of(
                material("stardewcraft:stone", 200),
                material("stardewcraft:starfruit", 9),
                material("stardewcraft:fiber", 100)
            ),
            64
        ),
        magical(
            "Earth Obelisk", "earth_obelisk", 500_000,
            List.of(
                material("stardewcraft:iridium_bar", 10),
                material("stardewcraft:earth_crystal", 10)
            ),
            128
        ),
        magical(
            "Water Obelisk", "water_obelisk", 500_000,
            List.of(
                material("stardewcraft:iridium_bar", 5),
                material("stardewcraft:clam", 10),
                material("stardewcraft:coral", 10)
            ),
            128
        ),
        magical(
            "Desert Obelisk", "desert_obelisk", 1_000_000,
            List.of(
                material("stardewcraft:iridium_bar", 20),
                material("stardewcraft:coconut", 10),
                material("stardewcraft:cactus_fruit", 10)
            ),
            128
        ),
        magical(
            "Island Obelisk", "island_obelisk", 1_000_000,
            List.of(
                material("stardewcraft:iridium_bar", 10),
                material("stardewcraft:dragon_tooth", 10),
                material("stardewcraft:banana", 10)
            ),
            128
        ),
        magical("Gold Clock", "gold_clock", 10_000_000, List.of(), 80)
    );

    private WizardBuildingService() {
    }

    public static List<CarpenterBlueprint> getBlueprints(ServerPlayer player) {
        boolean visitedIsland = PlayerDataManager.getPlayerData(player).hasMailFlag(VISITED_ISLAND_FLAG);
        return BLUEPRINTS.stream()
                .filter(blueprint -> visitedIsland || !"Island Obelisk".equals(blueprint.id()))
                .toList();
    }

    public static void open(ServerPlayer player) {
        if (!canUse(player)) {
            return;
        }

        PacketDistributor.sendToPlayer(player, new OpenCarpenterMenuPayload(
                BUILDER_ID,
                PlayerStardewDataAPI.getMoney(player),
                new ArrayList<>(getBlueprints(player))
        ));
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

    private static CarpenterBlueprint magical(String id, String itemPath, int cost,
                                                List<CarpenterBlueprint.MaterialEntry> materials,
                                                int previewCanvasSize) {
        String itemKey = "item.stardewcraft." + itemPath;
        return new CarpenterBlueprint(
                id,
                itemKey,
                itemKey + ".desc",
                cost,
                materials,
                "stardewcraft:" + itemPath,
                false,
                previewCanvasSize,
                true
        );
    }

    private static CarpenterBlueprint.MaterialEntry material(String itemId, int count) {
        return new CarpenterBlueprint.MaterialEntry(itemId, count);
    }
}
