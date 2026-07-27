package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.WizardBuildingItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/**
 * Keeps Wizard building ownership intact when stacks enter the world.
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class WizardBuildingOwnershipEvents {
    private WizardBuildingOwnershipEvents() {
    }

    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getItemEntity().getItem();
        if (!(stack.getItem() instanceof WizardBuildingItem)) {
            return;
        }
        if (WizardBuildingItem.getOwner(stack) == null) {
            WizardBuildingItem.bindTo(stack, player);
            return;
        }
        if (!WizardBuildingItem.isOwnedBy(stack, player.getUUID())) {
            event.setCanPickup(TriState.FALSE);
            event.getItemEntity().setPickUpDelay(20);
            player.displayClientMessage(Component.translatable(
                    "message.stardewcraft.wizard_building.owner_only",
                    WizardBuildingItem.getOwnerName(stack)), true);
        }
    }
}
