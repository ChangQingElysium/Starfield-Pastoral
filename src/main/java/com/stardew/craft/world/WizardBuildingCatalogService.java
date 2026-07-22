package com.stardew.craft.world;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.core.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Maintains the fixed Wizard Tower catalog while its appearance stays player-specific. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class WizardBuildingCatalogService {
    public static final BlockPos CATALOG_POS = new BlockPos(-185, 34, 53);
    public static final String UNLOCK_FLAG = "wizardBuildingsUnlocked";

    private static final int MAINTENANCE_INTERVAL = 40;

    private WizardBuildingCatalogService() {
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !ModDimensions.STARDEW_VALLEY.equals(level.dimension())
                || level.getGameTime() % MAINTENANCE_INTERVAL != 0L
                || !level.hasChunkAt(CATALOG_POS)
                || level.getBlockState(CATALOG_POS).is(ModBlocks.WIZARD_BUILDING_CATALOG.get())) {
            return;
        }
        level.setBlockAndUpdate(CATALOG_POS, ModBlocks.WIZARD_BUILDING_CATALOG.get().defaultBlockState());
    }
}
