package com.stardew.craft.platform.fabric.extension;

import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SpyglassItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

/** Stack-sensitive queries replacing the upstream ItemStack extension method. */
public final class ItemActions {
    private ItemActions() {}

    public static boolean canPerformAction(ItemStack stack, ItemAbility ability) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        // A custom override must also be able to reject an inherited vanilla ability.
        if (item instanceof ItemAbilityProvider provider) {
            return provider.canPerformAction(stack, ability);
        }
        // These memberships follow the actual 21.1.217 vanilla item patches. Tool
        // tags describe other concepts (e.g. enchantment eligibility), not actions.
        if (item instanceof AxeItem) return ItemAbilities.DEFAULT_AXE_ACTIONS.contains(ability);
        if (item instanceof PickaxeItem) return ItemAbilities.DEFAULT_PICKAXE_ACTIONS.contains(ability);
        if (item instanceof ShovelItem) return ItemAbilities.DEFAULT_SHOVEL_ACTIONS.contains(ability);
        if (item instanceof HoeItem) return ItemAbilities.DEFAULT_HOE_ACTIONS.contains(ability);
        if (item instanceof SwordItem) return ItemAbilities.DEFAULT_SWORD_ACTIONS.contains(ability);
        if (item instanceof ShearsItem) return ItemAbilities.DEFAULT_SHEARS_ACTIONS.contains(ability);
        if (item instanceof ShieldItem) return ItemAbilities.DEFAULT_SHIELD_ACTIONS.contains(ability);
        if (item instanceof FishingRodItem) return ItemAbilities.DEFAULT_FISHING_ROD_ACTIONS.contains(ability);
        if (item instanceof TridentItem) return ItemAbilities.DEFAULT_TRIDENT_ACTIONS.contains(ability);
        if (item instanceof BrushItem) return ItemAbilities.DEFAULT_BRUSH_ACTIONS.contains(ability);
        if (item instanceof FlintAndSteelItem) return ItemAbilities.DEFAULT_FLINT_ACTIONS.contains(ability);
        if (item instanceof FireChargeItem) return ItemAbilities.DEFAULT_FIRECHARGE_ACTIONS.contains(ability);
        if (item instanceof SpyglassItem) return ItemAbilities.DEFAULT_SPYGLASS_ACTIONS.contains(ability);
        return false;
    }
}
