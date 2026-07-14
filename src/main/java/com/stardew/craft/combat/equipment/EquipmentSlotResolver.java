package com.stardew.craft.combat.equipment;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.equipment.StardewEquipmentData;
import com.stardew.craft.api.v1.equipment.StardewEquipmentDataApi;
import com.stardew.craft.item.equipment.CombinedRingItem;
import com.stardew.craft.item.equipment.StardewBootsItem;
import com.stardew.craft.item.equipment.StardewRingItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Resolves public equipment slots while preserving the legacy built-in item classes. */
public final class EquipmentSlotResolver {
    public static final ResourceLocation RING = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "ring");
    public static final ResourceLocation BOOTS = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "boots");
    public static final ResourceLocation WEAPON = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "weapon");

    private EquipmentSlotResolver() {
    }

    public static boolean isRing(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        StardewEquipmentData data = StardewEquipmentDataApi.get(stack);
        if (data != null) return RING.equals(data.slot());
        return stack.getItem() instanceof StardewRingItem || stack.getItem() instanceof CombinedRingItem;
    }

    public static boolean isBoots(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        StardewEquipmentData data = StardewEquipmentDataApi.get(stack);
        if (data != null) return BOOTS.equals(data.slot());
        return stack.getItem() instanceof StardewBootsItem;
    }

    public static boolean isWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        StardewEquipmentData data = StardewEquipmentDataApi.get(stack);
        return data != null && WEAPON.equals(data.slot()) && data.weapon().isPresent();
    }
}
