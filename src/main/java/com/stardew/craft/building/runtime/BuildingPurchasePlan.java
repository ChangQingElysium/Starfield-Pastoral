package com.stardew.craft.building.runtime;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Simulate material removal and delivery together, before spending any money. */
public record BuildingPurchasePlan(Map<Integer, ItemStack> slots) {
    public record Material(Item item, int count) {}

    public static boolean hasMaterials(Inventory inventory, List<Material> materials) {
        Map<Item, Integer> needed = new LinkedHashMap<>();
        materials.forEach(material -> needed.merge(material.item(), material.count(), Integer::sum));
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            needed.computeIfPresent(stack.getItem(), (item, count) -> count - stack.getCount());
        }
        return needed.values().stream().allMatch(count -> count <= 0);
    }

    public static BuildingPurchasePlan prepare(Inventory inventory, ItemStack delivery, List<Material> materials) {
        Map<Integer, ItemStack> after = new LinkedHashMap<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) after.put(slot, inventory.getItem(slot).copy());
        for (Material material : materials) {
            int remaining = material.count();
            for (var stack : after.values()) {
                if (!stack.is(material.item())) continue;
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take); remaining -= take;
                if (remaining == 0) break;
            }
            if (remaining != 0) return null;
        }
        // Fill existing stacks before empty main-inventory slots; never deliver into armor/offhand.
        int remaining=delivery.getCount();
        for(int pass=0;pass<2;pass++)for(int slot=0;slot<inventory.items.size();slot++) {
            var current=after.get(slot);
            if(pass==0 && (current.isEmpty() || !ItemStack.isSameItemSameTags(current,delivery)))continue;
            if(pass==1 && !current.isEmpty())continue;
            int take=Math.min(remaining,delivery.getMaxStackSize()-current.getCount());
            if(take<=0)continue;
            if(current.isEmpty())after.put(slot,delivery.copyWithCount(take));else current.grow(take);
            remaining-=take;
            if(remaining==0)return new BuildingPurchasePlan(after);
        }
        return null;
    }

    public void apply(Inventory inventory) {
        slots.forEach((slot, stack) -> inventory.setItem(slot, stack.copy()));
        inventory.setChanged();
    }
}
