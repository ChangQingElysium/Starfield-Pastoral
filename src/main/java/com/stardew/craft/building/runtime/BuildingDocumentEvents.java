package com.stardew.craft.building.runtime;

import com.stardew.craft.StardewCraft;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.common.util.TriState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/** Documents own right-clicks; a chest/manager underneath must not steal the input. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class BuildingDocumentEvents {
    private BuildingDocumentEvents() {}
    @SubscribeEvent public static void use(PlayerInteractEvent.RightClickBlock event) {
        var item = event.getItemStack().getItem();
        if (item instanceof BuildingBlueprintItem || item instanceof BuildingUpgradePermitItem) event.setUseBlock(TriState.FALSE.toResult());
    }
}
