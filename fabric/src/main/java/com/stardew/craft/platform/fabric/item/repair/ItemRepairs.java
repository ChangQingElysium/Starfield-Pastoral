package com.stardew.craft.platform.fabric.item.repair;

import com.stardew.craft.platform.fabric.extension.ItemDurabilityProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Neo's canRepair flag is distinct from stack damageability and repair ingredients. */
public final class ItemRepairs {
    private ItemRepairs() {}
    public static Item.Properties setNoRepair(Item.Properties properties) {
        if (!(properties instanceof RepairableProperties state)) throw new IllegalStateException("Item.Properties repair mixin is not installed");
        state.stardewcraft$disableRepair();
        return properties;
    }
    public static boolean isRepairable(ItemStack stack) {
        return isRepairable(stack.getItem(), stack);
    }
    static boolean isRepairable(Item item, ItemStack stack) {
        if (!(item instanceof RepairableItem state)) throw new IllegalStateException("Item repair mixin is not installed");
        if (!state.stardewcraft$canRepair()) return false;
        // IItemExtension.isDamageable checks MAX_DAMAGE, including when an UNBREAKABLE component is present.
        return item instanceof ItemDurabilityProvider provider ? provider.isDamageable(stack) : stack.has(DataComponents.MAX_DAMAGE);
    }
}
