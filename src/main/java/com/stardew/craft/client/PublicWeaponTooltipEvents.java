package com.stardew.craft.client;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.equipment.StardewEquipmentData;
import com.stardew.craft.api.v1.equipment.StardewEquipmentDataApi;
import com.stardew.craft.item.weapon.IStardewWeapon;
import com.stardew.craft.item.weapon.WeaponTooltipBuilder;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Adds the shared combat-stat presentation to public API weapons. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class PublicWeaponTooltipEvents {
    private PublicWeaponTooltipEvents() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || stack.getItem() instanceof IStardewWeapon) {
            return;
        }
        StardewEquipmentData data = StardewEquipmentDataApi.get(stack);
        if (data == null || data.weapon().isEmpty()) {
            return;
        }
        event.getToolTip().addAll(
                WeaponTooltipBuilder.buildPublicApiCombatAttributes(stack)
        );
    }
}
