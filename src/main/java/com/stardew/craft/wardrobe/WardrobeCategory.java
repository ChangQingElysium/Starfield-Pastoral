package com.stardew.craft.wardrobe;

import com.stardew.craft.core.ModTags;
import com.stardew.craft.item.cosmetic.StardewCosmeticItem;
import com.stardew.craft.item.cosmetic.StardewCosmeticSlot;
import com.stardew.craft.item.equipment.CombinedRingItem;
import com.stardew.craft.item.equipment.StardewBootsItem;
import com.stardew.craft.item.equipment.StardewRingItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public enum WardrobeCategory {
    HATS("stardewcraft.wardrobe.category.hats"),
    SHIRTS("stardewcraft.wardrobe.category.shirts"),
    PANTS("stardewcraft.wardrobe.category.pants"),
    SHOES("stardewcraft.wardrobe.category.shoes"),
    RINGS("stardewcraft.wardrobe.category.rings");

    private final String translationKey;

    WardrobeCategory(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }

    public static boolean isAccepted(ItemStack stack) {
        return categoryFor(stack) != null || stack.is(ModTags.Items.WARDROBE_ACCEPTED);
    }

    public static WardrobeCategory categoryFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Item item = stack.getItem();
        if (item instanceof StardewCosmeticItem cosmetic) {
            StardewCosmeticSlot slot = cosmetic.getCosmeticSlot();
            return switch (slot) {
                case HAT -> WardrobeCategory.HATS;
                case SHIRT -> WardrobeCategory.SHIRTS;
                case PANTS -> WardrobeCategory.PANTS;
            };
        }
        if (item instanceof StardewRingItem || item instanceof CombinedRingItem) {
            return RINGS;
        }
        if (item instanceof StardewBootsItem) {
            return SHOES;
        }
        if (item instanceof ArmorItem armor) {
            EquipmentSlot slot = armor.getEquipmentSlot();
            if (slot == EquipmentSlot.HEAD) {
                return HATS;
            }
            if (slot == EquipmentSlot.CHEST) {
                return SHIRTS;
            }
            if (slot == EquipmentSlot.LEGS) {
                return PANTS;
            }
            if (slot == EquipmentSlot.FEET) {
                return SHOES;
            }
        }
        return null;
    }
}
