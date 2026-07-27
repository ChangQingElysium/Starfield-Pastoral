package com.stardew.craft.world;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.world.data.WorldLootPoolData;
import com.stardew.craft.world.data.ForageZoneData;
import com.stardew.craft.manager.ArtifactDropService;
import com.stardew.craft.mining.MineThemeData;
import com.stardew.craft.mining.MineMonsterSpawnTableData;
import com.stardew.craft.interior.InteriorRegionRegistry;
import com.stardew.craft.interior.InteriorPortalRegistry;
import com.stardew.craft.mastery.MasteryRewardRegistry;
import com.stardew.craft.player.ProfessionData;
import com.stardew.craft.farm.FarmLayoutDataRegistry;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class WorldContentSystem {
    private WorldContentSystem() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new WorldLootPoolData.ReloadListener());
        event.addListener(new ForageZoneData.ReloadListener());
        event.addListener(new ArtifactDropService.ReloadListener());
        event.addListener(new MineThemeData.ReloadListener());
        event.addListener(new MineMonsterSpawnTableData.ReloadListener());
        event.addListener(new InteriorRegionRegistry.ReloadListener());
        event.addListener(new WorldRegionRegistry.ReloadListener());
        event.addListener(new WorldAnchorRegistry.ReloadListener());
        event.addListener(new FarmLayoutDataRegistry.ReloadListener());
        event.addListener(new InteriorPortalRegistry.ReloadListener());
        event.addListener(new MasteryRewardRegistry.ReloadListener());
        event.addListener(new ProfessionData.ReloadListener());
    }
}
