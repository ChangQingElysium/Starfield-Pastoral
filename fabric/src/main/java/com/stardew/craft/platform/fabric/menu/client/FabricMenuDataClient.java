package com.stardew.craft.platform.fabric.menu.client;

import com.stardew.craft.platform.fabric.menu.MenuDataPayload;
import com.stardew.craft.platform.mixin.menu.mixin.MenuDataSlotAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Client classes are isolated from the common payload and server synchronizer signatures. */
@Environment(EnvType.CLIENT)
public final class FabricMenuDataClient {
    public static void apply(MenuDataPayload payload) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        AbstractContainerMenu current = player.containerMenu;
        if (current == player.inventoryMenu || current.containerId != payload.containerId()) return;
        ResourceLocation type = BuiltInRegistries.MENU.getKey(current.getType());
        int size = ((MenuDataSlotAccessor) current).stardewcraft$getDataSlots().size();
        if (payload.matches(current.containerId, type, size)) {
            current.setData(payload.dataSlot(), payload.value());
        }
    }

    private FabricMenuDataClient() {}
}
