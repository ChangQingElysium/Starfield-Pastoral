package com.stardew.craft.inventory;

import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.core.ModTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;

/** Shared client/server rule for the Stardew inventory trash can. */
public final class InventoryTrashPolicy {
    private InventoryTrashPolicy() {
    }

    public static boolean canTrash(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if ("stardewcraft.type.quest".equals(StardewItemDataApi.getTypeKey(stack))) {
            return false;
        }
        if (stack.is(ModTags.Items.PREVENT_LOSS_ON_DEATH)) {
            return false;
        }
        // SDV protects ordinary farming tools and scythes. Fishing rods and weapons
        // remain trashable, matching Item.canBeTrashed in the original game.
        return !stack.is(ItemTags.AXES)
                && !stack.is(ModTags.Items.PICKAXES)
                && !stack.is(ModTags.Items.HOES)
                && !stack.is(ModTags.Items.WATERING_CANS)
                && !stack.is(ModTags.Items.SCYTHES);
    }
}
